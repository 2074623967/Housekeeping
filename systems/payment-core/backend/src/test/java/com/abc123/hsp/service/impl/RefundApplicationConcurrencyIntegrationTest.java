package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundStatusQueryRequestDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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
 * 同一支付单并发退款申请的完整服务事务集成测试。
 */
public class RefundApplicationConcurrencyIntegrationTest {

    private static final String PAYMENT_ORDER_ID = "PAY-REFUND-CONCURRENT";

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:refund_application_concurrency;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RefundMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RefundMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/RefundMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndPayment();
    }

    @Test
    void shouldRejectSecondRefundAfterWaitingForFirstApplicationCommit() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            RefundListItemDTO first = createService(firstSession.getMapper(RefundMapper.class))
                    .apply(refundRequest("60.00", "REFUND-FIRST"));

            Future<RefundListItemDTO> second = executor.submit(
                    () -> applyInSecondTransaction(secondStarted, "REFUND-SECOND"));
            secondStarted.await(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));

            firstSession.commit();
            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));

            assertEquals("累计退款金额不能超过原支付金额", exception.getCause().getMessage());
            assertEquals("REVIEWING", first.getStatus());
            assertEquals(1, countRefundRows());
            assertEquals(0, sumRefundAmount().compareTo(new BigDecimal("60.00")));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotLetCorruptedActiveRefundOffsetNewApplicationCapacity() throws Exception {
        insertFailedRefund("REF-LEGACY-NEGATIVE", "-80.00");
        updatePaymentFact("UPDATE t_refund_order SET status = 'PROCESSING' "
                + "WHERE refund_order_id = 'REF-LEGACY-NEGATIVE'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1L, mapper.countInvalidActiveRefundAmounts(PAYMENT_ORDER_ID));
            assertEquals(0, mapper.sumActiveRefundAmount(PAYMENT_ORDER_ID)
                    .compareTo(new BigDecimal("-80.00")));
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> createService(mapper).apply(refundRequest("60.00", "REFUND-AFTER-NEGATIVE")));
            assertEquals("active refund amounts are inconsistent", exception.getMessage());
            session.rollback();
        }
        assertEquals(1, countRefundRows());
    }

    @Test
    void shouldNotReserveCanonicalPaymentCapacityForAliasedRefund() throws Exception {
        insertFailedRefund("REF-CANONICAL-RESERVED", "15.00");
        updatePaymentFact("UPDATE t_refund_order SET status = 'PROCESSING' "
                + "WHERE refund_order_id = 'REF-CANONICAL-RESERVED'");
        insertFailedRefund("REF-ALIAS-RESERVED", "80.00");
        updatePaymentFact("UPDATE t_refund_order SET status = 'PROCESSING', "
                + "payment_order_id = 'pay-refund-concurrent' "
                + "WHERE refund_order_id = 'REF-ALIAS-RESERVED'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(0, mapper.sumActiveRefundAmount(PAYMENT_ORDER_ID)
                    .compareTo(new BigDecimal("15.00")));
            assertEquals("REVIEWING", createService(mapper)
                    .apply(refundRequest("20.00", "REFUND-AFTER-ALIAS-RESERVED")).getStatus());
            session.commit();
        }
        assertEquals(3, countRefundRows());
    }

    @Test
    void shouldNotRejectCanonicalApplicationForAliasedInvalidRefundAmount() throws Exception {
        insertFailedRefund("REF-ALIAS-INVALID", "-80.00");
        updatePaymentFact("UPDATE t_refund_order SET status = 'PROCESSING', "
                + "payment_order_id = 'pay-refund-concurrent' "
                + "WHERE refund_order_id = 'REF-ALIAS-INVALID'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(0L, mapper.countInvalidActiveRefundAmounts(PAYMENT_ORDER_ID));
            assertEquals("REVIEWING", createService(mapper)
                    .apply(refundRequest("20.00", "REFUND-AFTER-ALIAS-INVALID")).getStatus());
            session.commit();
        }
        assertEquals(2, countRefundRows());
    }

    @Test
    void shouldRejectRefundApplicationWhenPaymentSuccessAttemptIsIncomplete() throws Exception {
        updatePaymentFact("UPDATE t_payment_attempt SET completed_at = NULL");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> createService(session.getMapper(RefundMapper.class))
                            .apply(refundRequest("20.00", "REFUND-INCOMPLETE-SUCCESS")));
            assertEquals("payment success facts are inconsistent", exception.getMessage());
        }
        assertEquals(0, countRefundRows());
    }

    @Test
    void shouldRejectRefundApplicationWhenLatestSuccessfulAttemptBelongsToAnotherChannel() throws Exception {
        updatePaymentFact("UPDATE t_payment_attempt SET channel_code = 'other_channel'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> createService(session.getMapper(RefundMapper.class))
                            .apply(refundRequest("20.00", "REFUND-WRONG-CHANNEL")));
            assertEquals("payment success facts are inconsistent", exception.getMessage());
        }
        assertEquals(0, countRefundRows());

        updatePaymentFact("UPDATE t_payment_attempt SET channel_code = 'wx_h5'");
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals("REVIEWING", createService(session.getMapper(RefundMapper.class))
                    .apply(refundRequest("20.00", "REFUND-CORRECT-CHANNEL")).getStatus());
            session.commit();
        }
        assertEquals(1, countRefundRows());
    }

    @Test
    void shouldRejectRefundApplicationWhenLatestSuccessAttemptUsesAnotherPaymentMethod() throws Exception {
        updatePaymentFact("UPDATE t_payment_attempt SET payment_method = '支付宝'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> createService(session.getMapper(RefundMapper.class))
                            .apply(refundRequest("20.00", "REFUND-WRONG-PAYMENT-METHOD")));
            assertEquals("payment success facts are inconsistent", exception.getMessage());
        }
        assertEquals(0, countRefundRows());

        updatePaymentFact("UPDATE t_payment_attempt SET payment_method = '微信'");
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals("REVIEWING", createService(session.getMapper(RefundMapper.class))
                    .apply(refundRequest("20.00", "REFUND-CORRECT-PAYMENT-METHOD")).getStatus());
            session.commit();
        }
        assertEquals(1, countRefundRows());
    }

    @Test
    void shouldRejectRefundApplicationWhenSuccessTimePrecedesAttemptByMillisecond() throws Exception {
        updatePaymentFact("UPDATE t_payment_order SET success_at = '2026-10-03 10:00:01.123'");
        updatePaymentFact("UPDATE t_payment_attempt SET completed_at = '2026-10-03 10:00:01.124'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> createService(session.getMapper(RefundMapper.class))
                            .apply(refundRequest("20.00", "REFUND-REVERSED-SUCCESS")));
            assertEquals("payment success facts are inconsistent", exception.getMessage());
        }
        assertEquals(0, countRefundRows());
    }

    @Test
    void shouldRollBackApprovalWhenPaymentFactsBecomeInvalidAfterApplication() throws Exception {
        String refundOrderId;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            refundOrderId = createService(session.getMapper(RefundMapper.class))
                    .apply(refundRequest("20.00", "REFUND-APPROVAL-FACTS")).getRefundOrderId();
            session.commit();
        }
        updatePaymentFact("UPDATE t_payment_attempt SET completed_at = NULL");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundActionRequestDTO request = new RefundActionRequestDTO();
            request.setRefundOrderId(refundOrderId);
            request.setOperator("refund-approver");
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> createService(session.getMapper(RefundMapper.class)).approve(request));
            assertEquals("payment success facts are inconsistent", exception.getMessage());
            session.rollback();
        }
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT status, (SELECT COUNT(*) FROM t_refund_operation_log "
                             + "WHERE refund_order_id = '" + refundOrderId + "') "
                             + "FROM t_refund_order WHERE refund_order_id = '" + refundOrderId + "'")) {
            assertTrue(resultSet.next());
            assertEquals("REVIEWING", resultSet.getString(1));
            assertEquals(1, resultSet.getInt(2));
        }
    }

    @Test
    void shouldCreateSingleAttemptAndOutboxForValidApproval() throws Exception {
        String refundOrderId;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            refundOrderId = createService(session.getMapper(RefundMapper.class))
                    .apply(refundRequest("20.00", "REFUND-APPROVAL-VALID")).getRefundOrderId();
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundActionRequestDTO request = new RefundActionRequestDTO();
            request.setRefundOrderId(refundOrderId);
            request.setOperator("refund-approver");
            assertEquals("PROCESSING", createService(session.getMapper(RefundMapper.class))
                    .approve(request).getStatus());
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT refund_order.status, attempt.refund_attempt_no, "
                             + "event.event_type, event.publish_status, "
                             + "(SELECT COUNT(*) FROM t_refund_operation_log log "
                             + "WHERE log.refund_order_id = refund_order.refund_order_id) "
                             + "FROM t_refund_order refund_order "
                             + "JOIN t_refund_attempt attempt ON attempt.refund_order_id = refund_order.refund_order_id "
                             + "JOIN t_payment_event event ON event.biz_no = attempt.refund_attempt_no "
                             + "WHERE refund_order.refund_order_id = '" + refundOrderId + "'")) {
            assertTrue(resultSet.next());
            assertEquals("PROCESSING", resultSet.getString(1));
            assertTrue(resultSet.getString(2).startsWith("RFA"));
            assertEquals("REFUND_SUBMIT", resultSet.getString(3));
            assertEquals("PENDING", resultSet.getString(4));
            assertEquals(2, resultSet.getInt(5));
            assertTrue(!resultSet.next());
        }
    }

    @Test
    void shouldCreateNewAttemptAndOutboxForValidTaskRetry() throws Exception {
        insertFailedRefund("REF-TASK-RETRY", "20.00");
        makeFailedRefundRetryable("REF-TASK-RETRY");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            createService(session.getMapper(RefundMapper.class))
                    .retryForTask("REF-TASK-RETRY", "refund-retry-scheduler");
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT refund_order.status, COUNT(DISTINCT attempt.refund_attempt_no), "
                             + "COUNT(DISTINCT event.event_no) FROM t_refund_order refund_order "
                             + "JOIN t_refund_attempt attempt ON attempt.refund_order_id = refund_order.refund_order_id "
                             + "LEFT JOIN t_payment_event event ON event.biz_no = attempt.refund_attempt_no "
                             + "WHERE refund_order.refund_order_id = 'REF-TASK-RETRY' "
                             + "GROUP BY refund_order.status")) {
            assertTrue(resultSet.next());
            assertEquals("PROCESSING", resultSet.getString(1));
            assertEquals(2, resultSet.getInt(2));
            assertEquals(1, resultSet.getInt(3));
        }
    }

    @Test
    void shouldCreateNewAttemptAndOutboxForValidManualRetry() throws Exception {
        insertFailedRefund("REF-MANUAL-RETRY", "20.00");
        makeFailedRefundRetryable("REF-MANUAL-RETRY");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundActionRequestDTO request = new RefundActionRequestDTO();
            request.setRefundOrderId("REF-MANUAL-RETRY");
            request.setOperator("refund-operator");
            assertEquals("PROCESSING", createService(session.getMapper(RefundMapper.class))
                    .retry(request).getStatus());
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(DISTINCT attempt.refund_attempt_no), "
                             + "COUNT(DISTINCT event.event_no) FROM t_refund_attempt attempt "
                             + "LEFT JOIN t_payment_event event ON event.biz_no = attempt.refund_attempt_no "
                             + "WHERE attempt.refund_order_id = 'REF-MANUAL-RETRY'")) {
            assertTrue(resultSet.next());
            assertEquals(2, resultSet.getInt(1));
            assertEquals(1, resultSet.getInt(2));
        }
    }

    @Test
    void shouldNotBlockManualRetryForAliasedPaymentRefund() throws Exception {
        insertFailedRefund("REF-CANONICAL-RETRY", "20.00");
        makeFailedRefundRetryable("REF-CANONICAL-RETRY");
        insertFailedRefund("REF-ALIAS-RETRY-CAPACITY", "90.00");
        updatePaymentFact("UPDATE t_refund_order SET status = 'PROCESSING', "
                + "payment_order_id = 'pay-refund-concurrent' "
                + "WHERE refund_order_id = 'REF-ALIAS-RETRY-CAPACITY'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundActionRequestDTO request = new RefundActionRequestDTO();
            request.setRefundOrderId("REF-CANONICAL-RETRY");
            request.setOperator("refund-operator");
            assertEquals("PROCESSING", createService(session.getMapper(RefundMapper.class))
                    .retry(request).getStatus());
            session.commit();
        }
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM t_refund_attempt "
                     + "WHERE refund_order_id = 'REF-CANONICAL-RETRY'")) {
            assertTrue(resultSet.next());
            assertEquals(2, resultSet.getInt(1));
        }
    }

    @Test
    void shouldRejectManualAndTaskRetryWhenPaymentFactsBecomeInvalid() throws Exception {
        insertFailedRefund("REF-INVALID-RETRY", "20.00");
        makeFailedRefundRetryable("REF-INVALID-RETRY");
        updatePaymentFact("UPDATE t_payment_attempt SET completed_at = NULL");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundActionRequestDTO request = new RefundActionRequestDTO();
            request.setRefundOrderId("REF-INVALID-RETRY");
            request.setOperator("refund-operator");
            RefundServiceImpl service = createService(session.getMapper(RefundMapper.class));
            assertEquals("payment success facts are inconsistent", assertThrows(
                    IllegalStateException.class, () -> service.retry(request)).getMessage());
            assertEquals("payment success facts are inconsistent", assertThrows(
                    IllegalStateException.class, () -> service.retryForTask(
                            "REF-INVALID-RETRY", "refund-retry-scheduler")).getMessage());
            session.rollback();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT refund_order.status, "
                             + "(SELECT COUNT(*) FROM t_refund_attempt attempt "
                             + "WHERE attempt.refund_order_id = refund_order.refund_order_id), "
                             + "(SELECT COUNT(*) FROM t_payment_event event "
                             + "WHERE event.payment_order_id = refund_order.payment_order_id) "
                             + "FROM t_refund_order refund_order "
                             + "WHERE refund_order.refund_order_id = 'REF-INVALID-RETRY'")) {
            assertTrue(resultSet.next());
            assertEquals("FAIL", resultSet.getString(1));
            assertEquals(1, resultSet.getInt(2));
            assertEquals(0, resultSet.getInt(3));
        }
    }

    @Test
    void shouldRejectTaskRetryWhenLatestAttemptChannelReferenceChanges() throws Exception {
        insertFailedRefund("REF-DRIFTED-CHANNEL", "20.00");
        makeFailedRefundRetryable("REF-DRIFTED-CHANNEL");
        updatePaymentFact("UPDATE t_refund_attempt SET channel_refund_no = 'CH-OTHER' "
                + "WHERE refund_order_id = 'REF-DRIFTED-CHANNEL'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals("refund terminal facts are inconsistent", assertThrows(
                    IllegalArgumentException.class, () -> createService(session.getMapper(RefundMapper.class))
                            .retryForTask("REF-DRIFTED-CHANNEL", "refund-retry-scheduler")).getMessage());
            session.rollback();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT status, (SELECT COUNT(*) FROM t_payment_event "
                             + "WHERE payment_order_id = '" + PAYMENT_ORDER_ID + "') "
                             + "FROM t_refund_order WHERE refund_order_id = 'REF-DRIFTED-CHANNEL'")) {
            assertTrue(resultSet.next());
            assertEquals("FAIL", resultSet.getString(1));
            assertEquals(0, resultSet.getInt(2));
        }
    }

    @Test
    void shouldRejectTaskRetryWhenLatestAttemptStatusTypeChanges() throws Exception {
        insertFailedRefund("REF-DRIFTED-STYLE", "20.00");
        makeFailedRefundRetryable("REF-DRIFTED-STYLE");
        updatePaymentFact("UPDATE t_refund_attempt SET attempt_status_type = 'warn' "
                + "WHERE refund_order_id = 'REF-DRIFTED-STYLE'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals("refund terminal facts are inconsistent", assertThrows(
                    IllegalArgumentException.class, () -> createService(session.getMapper(RefundMapper.class))
                            .retryForTask("REF-DRIFTED-STYLE", "refund-retry-scheduler")).getMessage());
            session.rollback();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT status, (SELECT COUNT(*) FROM t_payment_event "
                             + "WHERE payment_order_id = '" + PAYMENT_ORDER_ID + "') "
                             + "FROM t_refund_order WHERE refund_order_id = 'REF-DRIFTED-STYLE'")) {
            assertTrue(resultSet.next());
            assertEquals("FAIL", resultSet.getString(1));
            assertEquals(0, resultSet.getInt(2));
        }
    }

    @Test
    void shouldRejectTerminalReadWhenAttemptFactsDriftAfterSuccess() throws Exception {
        String refundOrderId;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundServiceImpl service = createService(session.getMapper(RefundMapper.class));
            refundOrderId = service.apply(refundRequest("20.00", "REFUND-TERMINAL-CHANNEL")).getRefundOrderId();
            RefundActionRequestDTO approveRequest = new RefundActionRequestDTO();
            approveRequest.setRefundOrderId(refundOrderId);
            approveRequest.setOperator("refund-approver");
            service.approve(approveRequest);
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT refund_attempt_no FROM t_refund_attempt WHERE refund_order_id = '"
                             + refundOrderId + "'")) {
            assertTrue(resultSet.next());
            String refundAttemptNo = resultSet.getString(1);
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1, mapper.bindRefundAttemptAccepted(refundAttemptNo, "CH-TERMINAL-VALID"));
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    refundOrderId, refundAttemptNo, "wx_h5", "CH-TERMINAL-VALID"));
            assertEquals(1, mapper.updateRefundStatus(
                    refundOrderId, "PROCESSING", "SUCCESS", "success", true));
            assertEquals(1, mapper.finalizeRefundAttempt(refundAttemptNo, "SUCCESS", "success"));
            session.commit();
        }

        RefundStatusQueryRequestDTO query = new RefundStatusQueryRequestDTO();
        query.setRefundOrderId(refundOrderId);
        query.setOperator("refund-reviewer");
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals("SUCCESS", createService(session.getMapper(RefundMapper.class))
                    .query(query).getStatus());
        }
        updatePaymentFact("UPDATE t_refund_order SET terminal_at = DATEADD('MILLISECOND', 2, success_at) "
                + "WHERE refund_order_id = '" + refundOrderId + "'");
        updatePaymentFact("UPDATE t_refund_attempt SET completed_at = DATEADD('MILLISECOND', 1, "
                + "(SELECT success_at FROM t_refund_order WHERE refund_order_id = '" + refundOrderId + "')) "
                + "WHERE refund_order_id = '" + refundOrderId + "'");
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals("refund terminal facts are inconsistent", assertThrows(
                    IllegalStateException.class, () -> createService(session.getMapper(RefundMapper.class))
                            .query(query)).getMessage());
            session.rollback();
        }
        updatePaymentFact("UPDATE t_refund_attempt SET completed_at = "
                + "(SELECT success_at FROM t_refund_order WHERE refund_order_id = '" + refundOrderId + "') "
                + "WHERE refund_order_id = '" + refundOrderId + "'");
        updatePaymentFact("UPDATE t_refund_order SET terminal_at = success_at "
                + "WHERE refund_order_id = '" + refundOrderId + "'");
        updatePaymentFact("UPDATE t_refund_attempt SET attempt_status_type = 'warn' "
                + "WHERE refund_order_id = '" + refundOrderId + "'");
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals("refund terminal facts are inconsistent", assertThrows(
                    IllegalStateException.class, () -> createService(session.getMapper(RefundMapper.class))
                            .query(query)).getMessage());
            session.rollback();
        }
        updatePaymentFact("UPDATE t_refund_attempt SET attempt_status_type = 'success' "
                + "WHERE refund_order_id = '" + refundOrderId + "'");
        updatePaymentFact("UPDATE t_refund_order SET channel_refund_no = 'CH-TERMINAL-OTHER' "
                + "WHERE refund_order_id = '" + refundOrderId + "'");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> createService(session.getMapper(RefundMapper.class)).query(query));
            assertEquals("refund terminal facts are inconsistent", exception.getMessage());
            session.rollback();
        }
    }

    private void updatePaymentFact(String sql) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate(sql);
            session.commit();
        }
    }

    @Test
    void shouldReturnSingleRefundForConcurrentSameIdempotencyKey() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            RefundListItemDTO first = createService(firstSession.getMapper(RefundMapper.class))
                    .apply(refundRequest("60.00", "REFUND-SAME-REQUEST"));

            Future<RefundListItemDTO> second = executor.submit(
                    () -> applyInSecondTransaction(secondStarted, "REFUND-SAME-REQUEST"));
            secondStarted.await(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));

            firstSession.commit();
            RefundListItemDTO replay = second.get(5, TimeUnit.SECONDS);

            assertEquals(first.getRefundOrderId(), replay.getRefundOrderId());
            assertEquals(1, countRefundRows());
            assertEquals(0, sumRefundAmount().compareTo(new BigDecimal("60.00")));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectIdempotencyKeyCaseAliasWithoutReplayingExistingRefund() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundServiceImpl service = createService(session.getMapper(RefundMapper.class));
            RefundListItemDTO first = service.apply(refundRequest("20.00", "REFUND-CASE-KEY"));
            session.commit();

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.apply(refundRequest("20.00", "refund-case-key")));
            assertEquals(ErrorCode.REFUND_APPLICATION_IDEMPOTENCY_CONFLICT, exception.getCode());
            assertEquals("REVIEWING", first.getStatus());
            assertEquals(1, countRefundRows());
        }
    }

    @Test
    void shouldIgnoreAliasedApplyLogWhenReplayingExactIdempotencyKey() throws Exception {
        RefundListItemDTO first;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            first = createService(session.getMapper(RefundMapper.class))
                    .apply(refundRequest("20.00", "REFUND-EXACT-LOG"));
            session.commit();
        }
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_refund_operation_log WHERE refund_order_id = '"
                    + first.getRefundOrderId() + "'");
            statement.executeUpdate("INSERT INTO t_refund_operation_log "
                    + "(log_no, refund_order_id, action_code, action_name, from_status, to_status, "
                    + "operator_name, operation_remark, created_at) VALUES "
                    + "('LOG-ALIAS', '" + first.getRefundOrderId().toLowerCase(Locale.ROOT)
                    + "', 'APPLY', '申请', 'INIT', 'REVIEWING', 'different-operator', '', NOW()), "
                    + "('LOG-EXACT', '" + first.getRefundOrderId()
                    + "', 'APPLY', '申请', 'INIT', 'REVIEWING', 'refund-concurrency-test', '', NOW())");
            session.commit();
        }
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundListItemDTO replay = createService(session.getMapper(RefundMapper.class))
                    .apply(refundRequest("20.00", "REFUND-EXACT-LOG"));
            assertEquals(first.getRefundOrderId(), replay.getRefundOrderId());
            assertEquals(1, countRefundRows());
        }
    }

    @Test
    void shouldNotReplayRefundWithAliasedApplyAction() throws Exception {
        RefundListItemDTO first;
        try (SqlSession session = sqlSessionFactory.openSession()) {
            first = createService(session.getMapper(RefundMapper.class))
                    .apply(refundRequest("20.00", "REFUND-ACTION-ALIAS"));
            session.commit();
        }
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_operation_log SET action_code = 'apply' "
                    + "WHERE refund_order_id = '" + first.getRefundOrderId() + "'");
            session.commit();
        }
        try (SqlSession session = sqlSessionFactory.openSession()) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> createService(session.getMapper(RefundMapper.class))
                            .apply(refundRequest("20.00", "REFUND-ACTION-ALIAS")));
            assertEquals(ErrorCode.REFUND_APPLICATION_IDEMPOTENCY_CONFLICT, exception.getCode());
            assertEquals(1, countRefundRows());
        }
    }

    @Test
    void shouldRejectFailedRefundRetryAfterConcurrentReplacementCommit() throws Exception {
        insertFailedRefund("REF-FAILED", "60.00");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch retryStarted = new CountDownLatch(1);
        try (SqlSession replacementSession = sqlSessionFactory.openSession()) {
            createService(replacementSession.getMapper(RefundMapper.class))
                    .apply(refundRequest("60.00", "REFUND-REPLACEMENT"));

            Future<RefundListItemDTO> retry = executor.submit(
                    () -> retryInSecondTransaction(retryStarted, "REF-FAILED"));
            retryStarted.await(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> retry.get(200, TimeUnit.MILLISECONDS));

            replacementSession.commit();
            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> retry.get(5, TimeUnit.SECONDS));

            assertEquals("累计退款金额不能超过原支付金额", exception.getCause().getMessage());
            assertEquals(2, countRefundRows());
            assertEquals(0, sumActiveRefundAmount().compareTo(new BigDecimal("60.00")));
        } finally {
            executor.shutdownNow();
        }
    }

    private RefundListItemDTO applyInSecondTransaction(CountDownLatch started, String idempotencyKey)
            throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            started.countDown();
            try {
                RefundListItemDTO result = createService(session.getMapper(RefundMapper.class))
                        .apply(refundRequest("60.00", idempotencyKey));
                session.commit();
                return result;
            } catch (RuntimeException exception) {
                session.rollback();
                throw exception;
            }
        }
    }

    private RefundListItemDTO retryInSecondTransaction(CountDownLatch started, String refundOrderId)
            throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            started.countDown();
            try {
                RefundActionRequestDTO request = new RefundActionRequestDTO();
                request.setRefundOrderId(refundOrderId);
                request.setOperator("refund-concurrency-test");
                RefundListItemDTO result = createService(session.getMapper(RefundMapper.class)).retry(request);
                session.commit();
                return result;
            } catch (RuntimeException exception) {
                session.rollback();
                throw exception;
            }
        }
    }

    private RefundServiceImpl createService(RefundMapper mapper) {
        return new RefundServiceImpl(
                mapper,
                mock(PaymentEventDispatchService.class),
                mock(com.abc123.hsp.service.PaymentCallbackSignatureService.class),
                mock(com.abc123.hsp.service.RefundChannelQueryService.class));
    }

    private RefundApplyRequestDTO refundRequest(String amount, String idempotencyKey) {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId(PAYMENT_ORDER_ID);
        request.setIdempotencyKey(idempotencyKey);
        request.setRefundAmount(new BigDecimal(amount));
        request.setRefundMethod("原路退款");
        request.setRefundReason("并发退款金额保护测试");
        request.setOperator("refund-concurrency-test");
        return request;
    }

    private int countRefundRows() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM t_refund_order")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private BigDecimal sumRefundAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COALESCE(SUM(refund_amount), 0) FROM t_refund_order")) {
            resultSet.next();
            return resultSet.getBigDecimal(1);
        }
    }

    private BigDecimal sumActiveRefundAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COALESCE(SUM(refund_amount), 0) FROM t_refund_order "
                             + "WHERE status IN ('REVIEWING', 'PROCESSING', 'SUCCESS')")) {
            resultSet.next();
            return resultSet.getBigDecimal(1);
        }
    }

    private void insertFailedRefund(String refundOrderId, String amount) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, payment_order_id, idempotency_key, order_no, customer_name, refund_amount, "
                    + "refund_method, refund_reason, status, status_type, applied_at, success_at) VALUES ('"
                    + refundOrderId + "', '" + PAYMENT_ORDER_ID + "', 'LEGACY:" + refundOrderId
                    + "', 'ORD-REFUND-CONCURRENT', "
                    + "'张女士', " + amount + ", '原路退款', '首次退款失败', "
                    + "'FAIL', 'danger', NOW(), NULL)");
            session.commit();
        }
    }

    private void makeFailedRefundRetryable(String refundOrderId) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_order SET applied_at = '2026-10-03 10:00:00', "
                    + "channel_code = 'wx_h5', channel_refund_no = 'CH-" + refundOrderId + "', "
                    + "channel_submitted_at = '2026-10-03 10:00:01', "
                    + "terminal_at = '2026-10-03 10:00:03' WHERE refund_order_id = '" + refundOrderId + "'");
            statement.executeUpdate("INSERT INTO t_refund_attempt (refund_attempt_no, refund_order_id, "
                    + "channel_code, channel_refund_no, attempt_status, attempt_status_type, "
                    + "submitted_at, completed_at, created_at) VALUES ('RFA-" + refundOrderId + "', '"
                    + refundOrderId + "', 'wx_h5', 'CH-" + refundOrderId + "', 'FAIL', 'danger', "
                    + "'2026-10-03 10:00:01', '2026-10-03 10:00:02', '2026-10-03 10:00:00')");
            session.commit();
        }
    }

    private void createTablesAndPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR '"
                    + RefundApplicationConcurrencyIntegrationTest.class.getName()
                    + ".formatDate'");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR '"
                    + RefundApplicationConcurrencyIntegrationTest.class.getName()
                    + ".formatAmount'");
            statement.execute("DROP TABLE IF EXISTS t_refund_operation_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR(64))");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "order_no VARCHAR(64) NOT NULL, customer_name VARCHAR(128) NOT NULL, "
                    + "amount DECIMAL(18,2) NOT NULL, payment_method VARCHAR(32) NOT NULL, "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(128), "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, "
                    + "success_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL, "
                    + "channel_code VARCHAR(64) NOT NULL, payment_method VARCHAR(32) NOT NULL, "
                    + "attempt_status VARCHAR(32) NOT NULL, attempt_status_type VARCHAR(32) NOT NULL, "
                    + "completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "order_no VARCHAR(64), amount DECIMAL(18,2), "
                    + "source_kind VARCHAR(16) DEFAULT 'VIRTUAL', "
                    + "pay_scene VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, order_no VARCHAR(64) NOT NULL, "
                    + "idempotency_key VARCHAR_IGNORECASE(128) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, refund_amount DECIMAL(18,2) NOT NULL, "
                    + "refund_method VARCHAR(32) NOT NULL, refund_reason VARCHAR(255) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, "
                    + "applied_at DATETIME NOT NULL, success_at DATETIME, terminal_at DATETIME, "
                    + "channel_code VARCHAR(64), channel_refund_no VARCHAR(64), "
                    + "channel_submitted_at DATETIME, "
                    + "UNIQUE(payment_order_id, idempotency_key))");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_attempt_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "refund_order_id VARCHAR(64) NOT NULL, channel_code VARCHAR(64) NOT NULL, "
                    + "channel_refund_no VARCHAR(128), attempt_status VARCHAR(32) NOT NULL, "
                    + "attempt_status_type VARCHAR(32) NOT NULL, submitted_at DATETIME, "
                    + "completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "event_type VARCHAR(64) NOT NULL, payment_order_id VARCHAR(64) NOT NULL, "
                    + "biz_no VARCHAR(64) NOT NULL, downstream_system VARCHAR(256) NOT NULL, "
                    + "event_topic VARCHAR(128) NOT NULL, publish_status VARCHAR(32) NOT NULL, "
                    + "publish_status_type VARCHAR(32) NOT NULL, retry_count INT NOT NULL, "
                    + "last_published_at DATETIME, next_retry_at DATETIME, "
                    + "event_payload VARCHAR(2048) NOT NULL, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_refund_operation_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, log_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "refund_order_id VARCHAR_IGNORECASE(64) NOT NULL, refund_attempt_no VARCHAR(64), "
                    + "action_code VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "action_name VARCHAR(128) NOT NULL, from_status VARCHAR(32) NOT NULL, "
                    + "to_status VARCHAR(32) NOT NULL, operator_name VARCHAR(64) NOT NULL, "
                    + "operation_remark VARCHAR(255) NOT NULL, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "channel_transaction_no, status, status_type, success_at, created_at) "
                    + "VALUES ('" + PAYMENT_ORDER_ID + "', 'ORD-REFUND-CONCURRENT', "
                    + "'张女士', 100.00, '微信', 'wx_h5', 'CH-REFUND-CONCURRENT', "
                    + "'SUCCESS', 'success', '2026-10-03 10:00:02', '2026-10-03 10:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, channel_code, payment_method, attempt_status, attempt_status_type, completed_at, created_at) "
                    + "VALUES ('" + PAYMENT_ORDER_ID + "', 'wx_h5', '微信', '成功', 'success', "
                    + "'2026-10-03 10:00:01', '2026-10-03 10:00:00')");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount, "
                    + "pay_scene, created_at) VALUES ('" + PAYMENT_ORDER_ID + "', "
                    + "'ORD-REFUND-CONCURRENT', 100.00, 'HOME_CLEAN', NOW())");
            session.commit();
        }
    }

    public static String formatDate(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static String formatAmount(BigDecimal amount, int decimals) {
        return amount.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }
}
