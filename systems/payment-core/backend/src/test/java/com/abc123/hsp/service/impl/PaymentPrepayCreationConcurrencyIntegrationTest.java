package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 同订单并发创建预付单的完整服务与 Mapper 集成测试。
 */
public class PaymentPrepayCreationConcurrencyIntegrationTest {

    private static final String ORDER_NO = "ORD-PREPAY-CONCURRENT-001";

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_prepay_service_concurrency;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndOrder();
    }

    @Test
    void shouldCreateOnlyOnePaymentFactSetForConcurrentSameOrderRequests() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            PaymentMapper firstMapper = firstSession.getMapper(PaymentMapper.class);
            firstMapper.initPrepayCreationLock(ORDER_NO);
            assertEquals(ORDER_NO, firstMapper.lockPrepayCreation(ORDER_NO));

            Future<PrepayOrderDTO> second = executor.submit(
                    () -> createPrepayInSecondTransaction(secondStarted));
            secondStarted.await(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));

            PrepayOrderDTO firstResult = createService(firstMapper).prepay(prepayRequest());
            firstSession.commit();
            PrepayOrderDTO secondResult = second.get(5, TimeUnit.SECONDS);

            assertEquals(firstResult.getPrepayOrderNo(), secondResult.getPrepayOrderNo());
            assertEquals(firstResult.getPaymentOrderId(), secondResult.getPaymentOrderId());
            assertEquals(1, countRows("t_payment_prepay_creation_lock"));
            assertEquals(1, countRows("t_bill"));
            assertEquals(1, countRows("t_payment_order"));
            assertEquals(1, countRows("t_prepay_order"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotReuseActivePrepayFromCaseVariantOrderNumber() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PrepayOrderDTO original = createService(mapper).prepay(prepayRequest());
            session.commit();

            String caseVariant = ORDER_NO.toLowerCase(Locale.ROOT);
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order SET order_no='" + caseVariant
                        + "' WHERE payment_order_id='" + original.getPaymentOrderId() + "'");
                statement.executeUpdate("UPDATE t_prepay_order SET order_no='" + caseVariant
                        + "' WHERE prepay_order_no='" + original.getPrepayOrderNo() + "'");
                statement.executeUpdate("UPDATE t_bill SET order_no='" + caseVariant
                        + "' WHERE bill_no='" + original.getBillNo() + "'");
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO current = createService(mapper).prepay(prepayRequest());
            assertNotEquals(original.getPrepayOrderNo(), current.getPrepayOrderNo());
            assertNotEquals(original.getBillNo(), current.getBillNo());
            assertEquals(ORDER_NO, current.getOrderNo());
            session.commit();
            session.clearCache();
            PrepayOrderDTO upper = mapper.findLatestActivePrepayByOrderNo(ORDER_NO);
            PrepayOrderDTO lower = mapper.findLatestActivePrepayByOrderNo(caseVariant);
            assertEquals(current.getPrepayOrderNo(), upper.getPrepayOrderNo());
            assertEquals(original.getPrepayOrderNo(), lower.getPrepayOrderNo());
            assertEquals(Integer.valueOf(1), upper.getActivePrepayCandidateCount());
            assertEquals(Integer.valueOf(1), lower.getActivePrepayCandidateCount());
        }
        assertEquals(2, countRows("t_bill"));
        assertEquals(2, countRows("t_payment_order"));
        assertEquals(2, countRows("t_prepay_order"));
    }

    @Test
    void shouldRejectCaseVariantPrepayIdentifierAtUserEntries() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PrepayOrderDTO prepay = createService(session.getMapper(PaymentMapper.class))
                    .prepay(prepayRequest());
            session.commit();

            String caseVariant = prepay.getPrepayOrderNo().toLowerCase(Locale.ROOT);
            BusinessException rejected = assertThrows(BusinessException.class,
                    () -> createService(session.getMapper(PaymentMapper.class))
                            .cashier(caseVariant));
            assertEquals(ErrorCode.PREPAY_ORDER_NOT_FOUND, rejected.getCode());

            PaymentSubmitRequestDTO submitRequest = new PaymentSubmitRequestDTO();
            submitRequest.setPrepayOrderNo(caseVariant);
            BusinessException submitRejected = assertThrows(BusinessException.class,
                    () -> createService(session.getMapper(PaymentMapper.class)).submit(submitRequest));
            assertEquals(ErrorCode.PREPAY_ORDER_NOT_FOUND, submitRejected.getCode());
        }
    }

    @Test
    void shouldRejectInvalidOrderAmountBeforeWritingPaymentFacts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_order SET paid_amount = -1.00 WHERE order_no = '"
                        + ORDER_NO + "'");
            }
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> createService(session.getMapper(PaymentMapper.class)).prepay(prepayRequest()));
            assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT, exception.getCode());
        }

        assertEquals(0, countRows("t_bill"));
        assertEquals(0, countRows("t_payment_order"));
        assertEquals(0, countRows("t_prepay_order"));
    }

    @Test
    void shouldRejectExistingStandardPrepayAfterSourceOrderDisappears() throws Exception {
        PrepayOrderDTO original;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            original = createService(session.getMapper(PaymentMapper.class)).prepay(prepayRequest());
            assertEquals("STANDARD_ORDER", original.getSourceKind());
            session.commit();
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("DELETE FROM t_order WHERE order_no = '" + ORDER_NO + "'");
            }
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PrepayRequestDTO request = prepayRequest();
            request.setCustomerName("张女士");
            request.setAmount(new BigDecimal("168.00"));
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> createService(session.getMapper(PaymentMapper.class)).prepay(request));
            assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        }
        try (SqlSession session = sqlSessionFactory.openSession()) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> prepareSubmitInSecondTransaction(session.getMapper(PaymentMapper.class), original));
            assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        }
        try (SqlSession session = sqlSessionFactory.openSession()) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'UNKNOWN' "
                        + "WHERE prepay_order_no = '" + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();
            PrepayOrderDTO unclassified = session.getMapper(PaymentMapper.class)
                    .findPrepay(original.getPrepayOrderNo());
            assertEquals("UNKNOWN", unclassified.getSourceKind());
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> prepareSubmitInSecondTransaction(session.getMapper(PaymentMapper.class), unclassified));
            assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        }

        assertEquals(1, countRows("t_bill"));
        assertEquals(1, countRows("t_payment_order"));
        assertEquals(1, countRows("t_prepay_order"));
        assertEquals(0, countRows("t_payment_attempt"));
    }

    @Test
    void shouldKeepNewVirtualPrepayWhenNoStandardOrderExists() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("DELETE FROM t_order WHERE order_no = '" + ORDER_NO + "'");
            }
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PrepayRequestDTO request = prepayRequest();
            request.setCustomerName("张女士");
            request.setAmount(new BigDecimal("168.00"));
            PrepayOrderDTO created = createService(session.getMapper(PaymentMapper.class)).prepay(request);
            assertEquals("VIRTUAL", created.getSourceKind());
            session.commit();
        }

        assertEquals(1, countRows("t_bill"));
        assertEquals(1, countRows("t_payment_order"));
        assertEquals(1, countRows("t_prepay_order"));
    }

    @Test
    void shouldRejectVirtualPrepayWhenStandardOrderAppearsLater() throws Exception {
        PrepayOrderDTO created;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("DELETE FROM t_order WHERE order_no = '" + ORDER_NO + "'");
            }
            session.commit();
            PrepayRequestDTO request = prepayRequest();
            request.setCustomerName("张女士");
            request.setAmount(new BigDecimal("168.00"));
            created = createService(session.getMapper(PaymentMapper.class)).prepay(request);
            session.commit();
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("INSERT INTO t_order(order_no, customer_name, order_amount, paid_amount) "
                        + "VALUES ('" + ORDER_NO + "', '张女士', 168.00, 0.00)");
            }
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PrepayRequestDTO request = prepayRequest();
            request.setCustomerName("张女士");
            request.setAmount(new BigDecimal("168.00"));
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> createService(session.getMapper(PaymentMapper.class)).prepay(request));
            assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        }
        try (SqlSession session = sqlSessionFactory.openSession()) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> prepareSubmitInSecondTransaction(session.getMapper(PaymentMapper.class), created));
            assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        }
        assertEquals(0, countRows("t_payment_attempt"));
    }

    @Test
    void shouldReuseActivePaymentWhenPrepayDisplaySnapshotIsStale() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PrepayOrderDTO original = createService(mapper).prepay(prepayRequest());
            session.commit();

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '支付成功', "
                        + "cashier_status_type = 'success' WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO pending = createService(mapper).prepay(prepayRequest());
            assertEquals(original.getPrepayOrderNo(), pending.getPrepayOrderNo());
            assertEquals(original.getPaymentOrderId(), pending.getPaymentOrderId());
            assertEquals("PREPAY_CREATED", pending.getPaymentStatus());
            assertEquals("待支付", pending.getCashierStatus());
            assertEquals("warn", pending.getCashierStatusType());

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order SET status = 'WAIT_CALLBACK' "
                        + "WHERE payment_order_id = '" + original.getPaymentOrderId() + "'");
                recordAcceptedChannelAttempt(statement, original.getPaymentOrderId());
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO waiting = createService(mapper).prepay(prepayRequest());
            assertEquals(original.getPrepayOrderNo(), waiting.getPrepayOrderNo());
            assertEquals("WAIT_CALLBACK", waiting.getPaymentStatus());
            assertEquals("待回调", waiting.getCashierStatus());
            assertEquals("warn", waiting.getCashierStatusType());

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '结果待确认', "
                        + "cashier_status_type = 'danger' WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO uncertain = createService(mapper).prepay(prepayRequest());
            assertEquals(original.getPrepayOrderNo(), uncertain.getPrepayOrderNo());
            assertEquals("WAIT_CALLBACK", uncertain.getPaymentStatus());
            assertEquals("结果待确认", uncertain.getCashierStatus());
            assertEquals("danger", uncertain.getCashierStatusType());
            session.commit();
        }
        assertEquals(1, countRows("t_bill"));
        assertEquals(1, countRows("t_payment_order"));
        assertEquals(1, countRows("t_prepay_order"));
    }

    @Test
    void shouldRejectWaitingPrepayWithoutExactChannelAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PrepayOrderDTO original = createService(mapper).prepay(prepayRequest());
            session.commit();

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order SET status = 'WAIT_CALLBACK', "
                        + "channel_code = 'wx_h5' WHERE payment_order_id = '"
                        + original.getPaymentOrderId() + "'");
            }
            session.commit();
            session.clearCache();
            BusinessException missingAttempt = assertThrows(BusinessException.class,
                    () -> createService(mapper).prepay(prepayRequest()));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, missingAttempt.getCode());
            BusinessException missingCashierAttempt = assertThrows(BusinessException.class,
                    () -> createService(mapper).cashier(original.getPrepayOrderNo()));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, missingCashierAttempt.getCode());
            BusinessException missingDetailAttempt = assertThrows(BusinessException.class,
                    () -> createService(mapper).detail(original.getPaymentOrderId()));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, missingDetailAttempt.getCode());

            try (Statement statement = session.getConnection().createStatement()) {
                recordAcceptedChannelAttempt(statement, original.getPaymentOrderId());
                statement.executeUpdate("UPDATE t_payment_attempt SET channel_code = 'WX_H5' "
                        + "WHERE payment_order_id = '" + original.getPaymentOrderId() + "'");
            }
            session.commit();
            session.clearCache();
            BusinessException wrongChannel = assertThrows(BusinessException.class,
                    () -> createService(mapper).prepay(prepayRequest()));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, wrongChannel.getCode());
            BusinessException wrongCashierChannel = assertThrows(BusinessException.class,
                    () -> createService(mapper).cashier(original.getPrepayOrderNo()));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, wrongCashierChannel.getCode());
            BusinessException wrongDetailChannel = assertThrows(BusinessException.class,
                    () -> createService(mapper).detail(original.getPaymentOrderId()));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, wrongDetailChannel.getCode());

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_attempt SET channel_code = 'wx_h5' "
                        + "WHERE payment_order_id = '" + original.getPaymentOrderId() + "'");
            }
            session.commit();
            session.clearCache();
            assertEquals(original.getPrepayOrderNo(), createService(mapper).prepay(prepayRequest()).getPrepayOrderNo());
            assertEquals("WAIT_CALLBACK", createService(mapper).cashier(original.getPrepayOrderNo()).getPaymentStatus());
        }
        assertEquals(1, countRows("t_payment_order"));
        assertEquals(1, countRows("t_prepay_order"));
    }

    @Test
    void shouldReuseRiskReviewPaymentDespiteStaleDisplayAndExpiry() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PrepayOrderDTO original = createService(mapper).prepay(prepayRequest());
            session.commit();

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order SET status = 'RISK_REVIEW', "
                        + "status_type = 'warn' WHERE payment_order_id = '"
                        + original.getPaymentOrderId() + "'");
                statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '支付成功', "
                        + "cashier_status_type = 'success' WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO reviewed = createService(mapper).prepay(prepayRequest());
            assertEquals(original.getPrepayOrderNo(), reviewed.getPrepayOrderNo());
            assertEquals(original.getPaymentOrderId(), reviewed.getPaymentOrderId());
            assertEquals("RISK_REVIEW", reviewed.getPaymentStatus());
            assertEquals("待风控复核", reviewed.getCashierStatus());
            assertEquals("warn", reviewed.getCashierStatusType());

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_prepay_order SET "
                        + "created_at = TIMESTAMPADD(HOUR, -2, NOW()), "
                        + "expires_at = TIMESTAMPADD(HOUR, -1, NOW()) WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO expiredReview = createService(mapper).prepay(prepayRequest());
            assertEquals(original.getPrepayOrderNo(), expiredReview.getPrepayOrderNo());
            assertEquals("RISK_REVIEW", expiredReview.getPaymentStatus());
            assertEquals("待风控复核", expiredReview.getCashierStatus());
            session.commit();
        }
        assertEquals(1, countRows("t_bill"));
        assertEquals(1, countRows("t_payment_order"));
        assertEquals(1, countRows("t_prepay_order"));
    }

    @Test
    void shouldReuseUnexpiredPayingPaymentInsteadOfCreatingAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PrepayOrderDTO original = createService(mapper).prepay(prepayRequest());
            session.commit();

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order SET status = 'PAYING', "
                        + "status_type = 'info' WHERE payment_order_id = '"
                        + original.getPaymentOrderId() + "'");
                statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '支付成功', "
                        + "cashier_status_type = 'success' WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO paying = createService(mapper).prepay(prepayRequest());
            assertEquals(original.getPrepayOrderNo(), paying.getPrepayOrderNo());
            assertEquals(original.getPaymentOrderId(), paying.getPaymentOrderId());
            assertEquals("PAYING", paying.getPaymentStatus());
            assertEquals("支付中", paying.getCashierStatus());
            assertEquals("info", paying.getCashierStatusType());
            session.commit();
        }
        assertEquals(1, countRows("t_bill"));
        assertEquals(1, countRows("t_payment_order"));
        assertEquals(1, countRows("t_prepay_order"));
    }

    @Test
    void shouldReuseSubmittedPaymentAfterPrepayExpiryInsteadOfCreatingAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PrepayOrderDTO original = createService(mapper).prepay(prepayRequest());
            session.commit();

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order SET status = 'WAIT_CALLBACK', "
                        + "status_type = 'danger' WHERE payment_order_id = '"
                        + original.getPaymentOrderId() + "'");
                recordAcceptedChannelAttempt(statement, original.getPaymentOrderId());
                statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '结果待确认', "
                        + "cashier_status_type = 'danger', "
                        + "created_at = TIMESTAMPADD(HOUR, -2, NOW()), "
                        + "expires_at = TIMESTAMPADD(HOUR, -1, NOW()) WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO pending = createService(mapper).prepay(prepayRequest());
            assertEquals(original.getPrepayOrderNo(), pending.getPrepayOrderNo());
            assertEquals(original.getPaymentOrderId(), pending.getPaymentOrderId());
            assertEquals("WAIT_CALLBACK", pending.getPaymentStatus());
            assertEquals("结果待确认", pending.getCashierStatus());
            assertEquals("danger", pending.getCashierStatusType());
            session.commit();
        }
        assertEquals(1, countRows("t_bill"));
        assertEquals(1, countRows("t_payment_order"));
        assertEquals(1, countRows("t_prepay_order"));
    }

    @Test
    void shouldCreateNewPrepayAfterUnsubmittedPreviousPrepayExpires() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PrepayOrderDTO original = createService(mapper).prepay(prepayRequest());
            session.commit();

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_prepay_order SET "
                        + "created_at = TIMESTAMPADD(HOUR, -2, NOW()), "
                        + "expires_at = TIMESTAMPADD(HOUR, -1, NOW()) WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();

            PrepayOrderDTO replacement = createService(mapper).prepay(prepayRequest());
            org.junit.jupiter.api.Assertions.assertNotEquals(
                    original.getPrepayOrderNo(), replacement.getPrepayOrderNo());
            org.junit.jupiter.api.Assertions.assertNotEquals(
                    original.getPaymentOrderId(), replacement.getPaymentOrderId());
            assertEquals("PREPAY_CREATED", replacement.getPaymentStatus());
            assertEquals("待支付", replacement.getCashierStatus());
            session.commit();
        }
        assertEquals(1, countRows("t_bill"));
        assertEquals(2, countRows("t_payment_order"));
        assertEquals(2, countRows("t_prepay_order"));
    }

    @Test
    void shouldRejectAmbiguousActivePaymentsForSameOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PrepayOrderDTO original = createService(mapper).prepay(prepayRequest());
            session.commit();

            assertEquals(1, mapper.insertPaymentOrder(
                    "PAY-DUPLICATE-ACTIVE", ORDER_NO, "张女士", new BigDecimal("168.00"), null));
            assertEquals(1, mapper.insertPrepayOrder(
                    "PRE-DUPLICATE-ACTIVE", original.getBillNo(), ORDER_NO, "张女士",
                    new BigDecimal("168.00"), "HOME_CLEAN", "家政服务收银台", "PAY-DUPLICATE-ACTIVE",
                    "STANDARD_ORDER"));
            session.commit();
            session.clearCache();

            assertThrows(IllegalStateException.class,
                    () -> createService(mapper).prepay(prepayRequest()));

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order SET status = 'WAIT_CALLBACK', "
                        + "status_type = 'warn' WHERE payment_order_id = '"
                        + original.getPaymentOrderId() + "'");
                recordAcceptedChannelAttempt(statement, original.getPaymentOrderId());
                statement.executeUpdate("UPDATE t_prepay_order SET "
                        + "created_at = TIMESTAMPADD(HOUR, -2, NOW()), "
                        + "expires_at = TIMESTAMPADD(HOUR, -1, NOW()) WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            session.commit();
            session.clearCache();
            assertThrows(IllegalStateException.class,
                    () -> createService(mapper).prepay(prepayRequest()));

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_prepay_order SET "
                        + "created_at = TIMESTAMPADD(HOUR, -2, NOW()), "
                        + "expires_at = TIMESTAMPADD(HOUR, -1, NOW()) "
                        + "WHERE prepay_order_no = 'PRE-DUPLICATE-ACTIVE'");
            }
            session.commit();
            session.clearCache();
            PrepayOrderDTO recovered = createService(mapper).prepay(prepayRequest());
            assertEquals(original.getPrepayOrderNo(), recovered.getPrepayOrderNo());
            assertEquals("WAIT_CALLBACK", recovered.getPaymentStatus());
            session.commit();
        }
        assertEquals(1, countRows("t_bill"));
        assertEquals(2, countRows("t_payment_order"));
        assertEquals(2, countRows("t_prepay_order"));
    }

    @Test
    void shouldRejectConflictingRequestAfterWaitingForSameOrderTransaction() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            PaymentMapper firstMapper = firstSession.getMapper(PaymentMapper.class);
            firstMapper.initPrepayCreationLock(ORDER_NO);
            assertEquals(ORDER_NO, firstMapper.lockPrepayCreation(ORDER_NO));

            PrepayRequestDTO conflictingRequest = prepayRequest();
            conflictingRequest.setPayScene("HOME_REPAIR");
            Future<PrepayOrderDTO> second = executor.submit(
                    () -> createPrepayInSecondTransaction(secondStarted, conflictingRequest));
            secondStarted.await(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));

            createService(firstMapper).prepay(prepayRequest());
            firstSession.commit();
            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));

            com.abc123.hsp.common.BusinessException conflict =
                    (com.abc123.hsp.common.BusinessException) exception.getCause();
            assertEquals(com.abc123.hsp.common.ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                    conflict.getCode());
            assertEquals(1, countRows("t_payment_prepay_creation_lock"));
            assertEquals(1, countRows("t_bill"));
            assertEquals(1, countRows("t_payment_order"));
            assertEquals(1, countRows("t_prepay_order"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectSubmitAfterConcurrentActivePrepayCommitsBeforeWritingAttempt() throws Exception {
        PrepayOrderDTO original;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            original = createService(session.getMapper(PaymentMapper.class)).prepay(prepayRequest());
            session.commit();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch submitStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            PaymentMapper firstMapper = firstSession.getMapper(PaymentMapper.class);
            firstMapper.initPrepayCreationLock(ORDER_NO);
            assertEquals(ORDER_NO, firstMapper.lockPrepayCreation(ORDER_NO));

            Future<PrepayOrderDTO> submit = executor.submit(() -> {
                try (SqlSession submitSession = sqlSessionFactory.openSession()) {
                    submitStarted.countDown();
                    return prepareSubmitInSecondTransaction(
                            submitSession.getMapper(PaymentMapper.class), original);
                }
            });
            assertEquals(true, submitStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> submit.get(200, TimeUnit.MILLISECONDS));

            insertSecondActivePrepay(firstSession, original);
            assertThrows(TimeoutException.class, () -> submit.get(200, TimeUnit.MILLISECONDS));
            firstSession.commit();

            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> submit.get(5, TimeUnit.SECONDS));
            assertEquals(IllegalStateException.class, exception.getCause().getClass());
            assertEquals("active prepay orders for the same order are ambiguous",
                    exception.getCause().getMessage());
            assertEquals(2, countRows("t_payment_order"));
            assertEquals(2, countRows("t_prepay_order"));
            assertEquals(0, countRows("t_payment_attempt"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectRiskDecisionAfterConcurrentActivePrepayCommitsBeforeWritingFacts()
            throws Exception {
        PrepayOrderDTO original;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            original = createService(session.getMapper(PaymentMapper.class)).prepay(prepayRequest());
            session.commit();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch riskStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            PaymentMapper firstMapper = firstSession.getMapper(PaymentMapper.class);
            firstMapper.initPrepayCreationLock(ORDER_NO);
            assertEquals(ORDER_NO, firstMapper.lockPrepayCreation(ORDER_NO));

            Future<PrepayOrderDTO> risk = executor.submit(() -> {
                try (SqlSession riskSession = sqlSessionFactory.openSession()) {
                    riskStarted.countDown();
                    return handleRiskDecisionInSecondTransaction(
                            riskSession.getMapper(PaymentMapper.class), original);
                }
            });
            assertEquals(true, riskStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> risk.get(200, TimeUnit.MILLISECONDS));
            insertSecondActivePrepay(firstSession, original);
            assertThrows(TimeoutException.class, () -> risk.get(200, TimeUnit.MILLISECONDS));
            firstSession.commit();

            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> risk.get(5, TimeUnit.SECONDS));
            assertEquals(IllegalStateException.class, exception.getCause().getClass());
            assertEquals("active prepay orders for the same order are ambiguous",
                    exception.getCause().getMessage());
            try (SqlSession session = sqlSessionFactory.openSession();
                 Statement statement = session.getConnection().createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT status, payment_method FROM t_payment_order WHERE payment_order_id = '"
                                 + original.getPaymentOrderId() + "'")) {
                result.next();
                assertEquals("PREPAY_CREATED", result.getString(1));
                assertEquals("待选渠道", result.getString(2));
            }
            assertEquals(2, countRows("t_payment_order"));
            assertEquals(2, countRows("t_prepay_order"));
            assertEquals(0, countRows("t_payment_attempt"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldPersistRiskReviewWithoutCreatingChannelAttempt() throws Exception {
        assertRiskDecisionPersisted("REVIEW", "RISK_REVIEW", "待风控复核", "warn", "PAYMENT_RISK_REVIEW");
    }

    @Test
    void shouldPersistRiskInterceptWithoutCreatingChannelAttempt() throws Exception {
        assertRiskDecisionPersisted("INTERCEPT", "RISK_BLOCKED", "风控拦截", "danger", "PAYMENT_RISK_BLOCKED");
    }

    @Test
    void shouldPersistRiskRejectWithoutCreatingChannelAttempt() throws Exception {
        assertRiskDecisionPersisted("REJECT", "RISK_REJECTED", "风控拒绝", "danger", "PAYMENT_RISK_REJECTED");
    }

    private void assertRiskDecisionPersisted(String decision, String paymentStatus,
            String cashierStatus, String statusType, String eventType) throws Exception {
        PrepayOrderDTO original;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            original = createService(session.getMapper(PaymentMapper.class)).prepay(prepayRequest());
            session.commit();
        }
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PrepayOrderDTO result = handleRiskDecisionInSecondTransaction(
                    session.getMapper(PaymentMapper.class), original, decision);
            assertEquals(original.getPrepayOrderNo(), result.getPrepayOrderNo());
            assertEquals(paymentStatus, result.getPaymentStatus());
            assertEquals(cashierStatus, result.getCashierStatus());
            assertEquals(statusType, result.getCashierStatusType());
            session.commit();
        }
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet facts = statement.executeQuery(
                     "SELECT p.payment_method, p.channel_code, p.channel_transaction_no, "
                             + "p.status, p.status_type, pr.cashier_status, pr.cashier_status_type, "
                             + "e.event_no, e.event_type, e.event_payload, e.publish_status, "
                             + "l.source_type, l.process_stage, l.message "
                             + "FROM t_payment_order p "
                             + "JOIN t_prepay_order pr ON pr.payment_order_id = p.payment_order_id "
                             + "JOIN t_payment_event e ON e.payment_order_id = p.payment_order_id "
                             + "JOIN t_payment_process_log l ON l.source_no = e.event_no "
                             + "WHERE p.payment_order_id = '" + original.getPaymentOrderId() + "'")) {
            assertEquals(true, facts.next());
            assertEquals("微信支付", facts.getString("payment_method"));
            assertEquals("MOCK_CHANNEL", facts.getString("channel_code"));
            assertEquals(null, facts.getString("channel_transaction_no"));
            assertEquals(paymentStatus, facts.getString("status"));
            assertEquals(statusType, facts.getString("status_type"));
            assertEquals(cashierStatus, facts.getString("cashier_status"));
            assertEquals(statusType, facts.getString("cashier_status_type"));
            assertEquals(eventType, facts.getString("event_type"));
            assertEquals(true, facts.getString("event_payload").contains("\"decision\":\"" + decision + "\""));
            assertEquals("PENDING", facts.getString("publish_status"));
            assertEquals("PAYMENT_EVENT", facts.getString("source_type"));
            assertEquals("业务事件", facts.getString("process_stage"));
            assertEquals("事件类型=" + eventType, facts.getString("message"));
            assertEquals(false, facts.next());
        }
        assertEquals(0, countRows("t_payment_attempt"));
        assertEquals(1, countRows("t_payment_event"));
        assertEquals(1, countRows("t_payment_process_log"));
    }

    @Test
    void shouldRejectSubmitAfterPrepayExpiresWhileWaitingForOrderLock() throws Exception {
        PrepayOrderDTO original;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            original = createService(session.getMapper(PaymentMapper.class)).prepay(prepayRequest());
            session.commit();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch submitStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            PaymentMapper firstMapper = firstSession.getMapper(PaymentMapper.class);
            firstMapper.initPrepayCreationLock(ORDER_NO);
            assertEquals(ORDER_NO, firstMapper.lockPrepayCreation(ORDER_NO));

            Future<PrepayOrderDTO> submit = executor.submit(() -> {
                try (SqlSession submitSession = sqlSessionFactory.openSession()) {
                    submitStarted.countDown();
                    return prepareSubmitInSecondTransaction(
                            submitSession.getMapper(PaymentMapper.class), original);
                }
            });
            assertEquals(true, submitStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> submit.get(200, TimeUnit.MILLISECONDS));
            try (Statement statement = firstSession.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_prepay_order SET expires_at = "
                        + "TIMESTAMPADD(MINUTE, -1, NOW()) WHERE prepay_order_no = '"
                        + original.getPrepayOrderNo() + "'");
            }
            assertThrows(TimeoutException.class, () -> submit.get(200, TimeUnit.MILLISECONDS));
            firstSession.commit();

            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> submit.get(5, TimeUnit.SECONDS));
            assertEquals(BusinessException.class, exception.getCause().getClass());
            assertEquals(ErrorCode.PAYMENT_PREPAY_EXPIRED,
                    ((BusinessException) exception.getCause()).getCode());
            assertEquals(1, countRows("t_payment_order"));
            assertEquals(1, countRows("t_prepay_order"));
            assertEquals(0, countRows("t_payment_attempt"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectSubmitWhenOrderOutstandingChangesWhileWaitingForLock() throws Exception {
        PrepayOrderDTO original;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            original = createService(session.getMapper(PaymentMapper.class)).prepay(prepayRequest());
            session.commit();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch submitStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            PaymentMapper firstMapper = firstSession.getMapper(PaymentMapper.class);
            firstMapper.initPrepayCreationLock(ORDER_NO);
            assertEquals(ORDER_NO, firstMapper.lockPrepayCreation(ORDER_NO));

            Future<PrepayOrderDTO> submit = executor.submit(() -> {
                try (SqlSession submitSession = sqlSessionFactory.openSession()) {
                    submitStarted.countDown();
                    return prepareSubmitInSecondTransaction(
                            submitSession.getMapper(PaymentMapper.class), original);
                }
            });
            assertEquals(true, submitStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> submit.get(200, TimeUnit.MILLISECONDS));
            try (Statement statement = firstSession.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_order SET paid_amount = 50.00 WHERE order_no = '"
                        + ORDER_NO + "'");
            }
            assertThrows(TimeoutException.class, () -> submit.get(200, TimeUnit.MILLISECONDS));
            firstSession.commit();

            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> submit.get(5, TimeUnit.SECONDS));
            assertEquals(BusinessException.class, exception.getCause().getClass());
            assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                    ((BusinessException) exception.getCause()).getCode());
            assertEquals(1, countRows("t_payment_order"));
            assertEquals(1, countRows("t_prepay_order"));
            assertEquals(0, countRows("t_payment_attempt"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldReadSettledAmountAfterWaitingForPaymentSuccessLock() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch prepayStarted = new CountDownLatch(1);
        try (SqlSession settlementSession = sqlSessionFactory.openSession()) {
            PaymentMapper settlementMapper = settlementSession.getMapper(PaymentMapper.class);
            settlementMapper.initPrepayCreationLock(ORDER_NO);
            assertEquals(ORDER_NO, settlementMapper.lockPrepayCreation(ORDER_NO));

            Future<PrepayOrderDTO> prepay = executor.submit(
                    () -> createPrepayInSecondTransaction(prepayStarted));
            prepayStarted.await(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> prepay.get(200, TimeUnit.MILLISECONDS));

            try (Statement statement = settlementSession.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_order SET paid_amount = 168.00 WHERE order_no = '"
                        + ORDER_NO + "'");
            }
            settlementSession.commit();

            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> prepay.get(5, TimeUnit.SECONDS));
            com.abc123.hsp.common.BusinessException settled =
                    (com.abc123.hsp.common.BusinessException) exception.getCause();
            assertEquals(com.abc123.hsp.common.ErrorCode.PAYMENT_ORDER_ALREADY_SETTLED,
                    settled.getCode());
            assertEquals(0, countRows("t_payment_order"));
            assertEquals(0, countRows("t_prepay_order"));
        } finally {
            executor.shutdownNow();
        }
    }

    private PrepayOrderDTO createPrepayInSecondTransaction(CountDownLatch started) throws Exception {
        return createPrepayInSecondTransaction(started, prepayRequest());
    }

    private PrepayOrderDTO createPrepayInSecondTransaction(
            CountDownLatch started, PrepayRequestDTO request) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            started.countDown();
            PrepayOrderDTO result = createService(session.getMapper(PaymentMapper.class))
                    .prepay(request);
            session.commit();
            return result;
        }
    }

    private PrepayOrderDTO prepareSubmitInSecondTransaction(
            PaymentMapper mapper, PrepayOrderDTO prepay) throws Exception {
        Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "prepareSubmit", PaymentSubmitRequestDTO.class, PrepayOrderDTO.class,
                String.class, String.class, String.class, String.class, String.class,
                PaymentRouteDecisionDTO.class, String.class, String.class, String.class,
                String.class, Long.class);
        method.setAccessible(true);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());
        request.setPaymentMethod("微信支付");
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("MOCK_CHANNEL");
        try {
            return (PrepayOrderDTO) method.invoke(
                    createService(mapper), request, prepay, prepay.getPaymentOrderId(),
                    "default-app", "H5", "127.0.0.1", "IDEMP-CONCURRENT-SECOND",
                    routeDecision, ORDER_NO, "RTR-CONCURRENT-SECOND", "ATT-CONCURRENT-SECOND",
                    "{}", null);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException) {
                throw (RuntimeException) exception.getCause();
            }
            throw exception;
        }
    }

    private PrepayOrderDTO handleRiskDecisionInSecondTransaction(
            PaymentMapper mapper, PrepayOrderDTO prepay) throws Exception {
        return handleRiskDecisionInSecondTransaction(mapper, prepay, "REVIEW");
    }

    private PrepayOrderDTO handleRiskDecisionInSecondTransaction(
            PaymentMapper mapper, PrepayOrderDTO prepay, String decision) throws Exception {
        Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "handleRiskDecision", PaymentSubmitRequestDTO.class, PrepayOrderDTO.class,
                PaymentDetailDTO.class, String.class, PaymentRiskDecisionResultDTO.class);
        method.setAccessible(true);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPaymentMethod("微信支付");
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId(prepay.getPaymentOrderId());
        detail.setOrderNo(ORDER_NO);
        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision(decision);
        try {
            return (PrepayOrderDTO) method.invoke(
                    createService(mapper), request, prepay, detail, "MOCK_CHANNEL", riskDecision);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException) {
                throw (RuntimeException) exception.getCause();
            }
            throw exception;
        }
    }

    private void insertSecondActivePrepay(SqlSession session, PrepayOrderDTO original) throws Exception {
        try (Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, order_no, "
                    + "customer_name, amount, payment_method, status, status_type, created_at) "
                    + "VALUES ('PAY-CONCURRENT-SECOND', '" + ORDER_NO + "', '张女士', 168.00, "
                    + "'待选渠道', 'PREPAY_CREATED', 'info', NOW())");
            statement.executeUpdate("INSERT INTO t_prepay_order(prepay_order_no, bill_no, "
                    + "order_no, customer_name, amount, pay_scene, cashier_title, "
                    + "cashier_status, cashier_status_type, payment_order_id, source_kind, created_at, expires_at) "
                    + "VALUES ('PRE-CONCURRENT-SECOND', '" + original.getBillNo() + "', '"
                    + ORDER_NO + "', '张女士', 168.00, 'HOME_CLEAN', '家政服务', "
                    + "'待支付', 'info', 'PAY-CONCURRENT-SECOND', 'STANDARD_ORDER', NOW(), "
                    + "TIMESTAMPADD(MINUTE, 30, NOW()))");
        }
    }

    private PaymentServiceImpl createService(PaymentMapper mapper) {
        return new PaymentServiceImpl(
                mapper,
                mock(PaymentCallbackSignatureService.class),
                mock(PaymentChannelRoutingService.class),
                mock(PaymentChannelQueryService.class),
                mock(PaymentChannelSubmitService.class));
    }

    private PrepayRequestDTO prepayRequest() {
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo(ORDER_NO);
        request.setPayScene("HOME_CLEAN");
        return request;
    }

    private int countRows(String tableName) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void createTablesAndOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR '"
                    + PaymentPrepayCreationConcurrencyIntegrationTest.class.getName()
                    + ".formatAmount'");
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR '"
                    + PaymentPrepayCreationConcurrencyIntegrationTest.class.getName()
                    + ".formatDate'");
            statement.execute("DROP TABLE IF EXISTS t_payment_prepay_creation_lock");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_payment_process_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_bill");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "customer_name VARCHAR(128) NOT NULL, order_amount DECIMAL(18,2) NOT NULL, "
                    + "paid_amount DECIMAL(18,2) NOT NULL)");
            statement.execute("CREATE TABLE t_payment_prepay_creation_lock ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_bill ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "order_no VARCHAR_IGNORECASE(64) NOT NULL, customer_name VARCHAR(128) NOT NULL, "
                    + "bill_amount DECIMAL(18,2) NOT NULL, paid_amount DECIMAL(18,2) NOT NULL, "
                    + "bill_status VARCHAR(32) NOT NULL, bill_status_type VARCHAR(32) NOT NULL, "
                    + "due_at DATETIME NOT NULL, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "order_no VARCHAR(64) NOT NULL, customer_name VARCHAR(128) NOT NULL, "
                    + "amount DECIMAL(18,2) NOT NULL, payment_method VARCHAR(32) NOT NULL, "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(128), "
                    + "last_channel_return_code VARCHAR(64), last_standardized_code VARCHAR(64), "
                    + "last_standardized_message VARCHAR(255), last_handling_suggestion VARCHAR(255), "
                    + "last_retryable BOOLEAN, last_manual_intervention_required BOOLEAN, "
                    + "last_mapping_version VARCHAR(64), last_mapping_matched BOOLEAN, "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, "
                    + "success_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, idempotency_key VARCHAR(128) NOT NULL, "
                    + "payment_order_id VARCHAR(64), channel_code VARCHAR(64), "
                    + "terminal VARCHAR(32), client_ip VARCHAR(64), ops_config_snapshot_version BIGINT, "
                    + "request_payload VARCHAR(2048), response_payload VARCHAR(2048), "
                    + "attempt_status VARCHAR(32), attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "event_type VARCHAR(64) NOT NULL, payment_order_id VARCHAR(64) NOT NULL, "
                    + "biz_no VARCHAR(64) NOT NULL, downstream_system VARCHAR(256) NOT NULL, "
                    + "event_topic VARCHAR(128) NOT NULL, publish_status VARCHAR(32) NOT NULL, "
                    + "publish_status_type VARCHAR(32) NOT NULL, retry_count INT NOT NULL, "
                    + "last_published_at DATETIME, next_retry_at DATETIME, "
                    + "event_payload VARCHAR(2048) NOT NULL, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_process_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, log_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "payment_order_id VARCHAR(64) NOT NULL, order_no VARCHAR(64), "
                    + "request_no VARCHAR(64), source_type VARCHAR(32) NOT NULL, "
                    + "source_no VARCHAR(64) NOT NULL, process_stage VARCHAR(32) NOT NULL, "
                    + "log_level VARCHAR(16) NOT NULL, log_level_type VARCHAR(16) NOT NULL, "
                    + "message VARCHAR(2048) NOT NULL, source VARCHAR(64) NOT NULL, "
                    + "created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "bill_no VARCHAR(64) NOT NULL, order_no VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, amount DECIMAL(18,2) NOT NULL, "
                    + "pay_scene VARCHAR(32) NOT NULL, cashier_title VARCHAR(128) NOT NULL, "
                    + "cashier_status VARCHAR(32) NOT NULL, cashier_status_type VARCHAR(32) NOT NULL, "
                    + "payment_order_id VARCHAR(64), source_kind VARCHAR(16) NOT NULL, "
                    + "created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_order(order_no, customer_name, order_amount, paid_amount) "
                    + "VALUES ('" + ORDER_NO + "', '张女士', 168.00, 0.00)");
            session.commit();
        }
    }

    private void recordAcceptedChannelAttempt(Statement statement, String paymentOrderId) throws Exception {
        statement.executeUpdate("UPDATE t_payment_order SET channel_code = 'wx_h5' "
                + "WHERE payment_order_id = '" + paymentOrderId + "'");
        statement.executeUpdate("INSERT INTO t_payment_attempt "
                + "(idempotency_key, payment_order_id, channel_code, attempt_status, attempt_status_type, created_at) "
                + "VALUES ('KEY-" + paymentOrderId + "', '" + paymentOrderId
                + "', 'wx_h5', '待回调', 'warn', NOW())");
    }

    public static String formatAmount(BigDecimal amount, int scale) {
        return String.format(Locale.ROOT, "%,." + scale + "f", amount);
    }

    public static String formatDate(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(timestamp);
    }
}
