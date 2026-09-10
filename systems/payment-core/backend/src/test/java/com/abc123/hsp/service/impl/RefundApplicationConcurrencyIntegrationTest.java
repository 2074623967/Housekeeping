package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
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
                    .apply(refundRequest("60.00"));

            Future<RefundListItemDTO> second = executor.submit(
                    () -> applyInSecondTransaction(secondStarted));
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
    void shouldRejectFailedRefundRetryAfterConcurrentReplacementCommit() throws Exception {
        insertFailedRefund("REF-FAILED", "60.00");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch retryStarted = new CountDownLatch(1);
        try (SqlSession replacementSession = sqlSessionFactory.openSession()) {
            createService(replacementSession.getMapper(RefundMapper.class)).apply(refundRequest("60.00"));

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

    private RefundListItemDTO applyInSecondTransaction(CountDownLatch started) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            started.countDown();
            try {
                RefundListItemDTO result = createService(session.getMapper(RefundMapper.class))
                        .apply(refundRequest("60.00"));
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
                mock(com.abc123.hsp.service.PaymentCallbackSignatureService.class));
    }

    private RefundApplyRequestDTO refundRequest(String amount) {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId(PAYMENT_ORDER_ID);
        request.setRefundAmount(new BigDecimal(amount));
        request.setRefundMethod("原路退款");
        request.setRefundReason("并发退款金额保护测试");
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
                    + "refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, refund_reason, status, status_type, applied_at, success_at) VALUES ('"
                    + refundOrderId + "', '" + PAYMENT_ORDER_ID + "', 'ORD-REFUND-CONCURRENT', "
                    + "'张女士', " + amount + ", '原路退款', '首次退款失败', "
                    + "'FAIL', 'danger', NOW(), NULL)");
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
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "order_no VARCHAR(64) NOT NULL, customer_name VARCHAR(128) NOT NULL, "
                    + "amount DECIMAL(18,2) NOT NULL, payment_method VARCHAR(32) NOT NULL, "
                    + "channel_code VARCHAR(64), status VARCHAR(32) NOT NULL)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "payment_order_id VARCHAR(64) NOT NULL, order_no VARCHAR(64) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, refund_amount DECIMAL(18,2) NOT NULL, "
                    + "refund_method VARCHAR(32) NOT NULL, refund_reason VARCHAR(255) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, "
                    + "applied_at DATETIME NOT NULL, success_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_operation_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, log_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "refund_order_id VARCHAR(64) NOT NULL, action_code VARCHAR(64) NOT NULL, "
                    + "action_name VARCHAR(128) NOT NULL, from_status VARCHAR(32) NOT NULL, "
                    + "to_status VARCHAR(32) NOT NULL, operator_name VARCHAR(64) NOT NULL, "
                    + "operation_remark VARCHAR(255) NOT NULL, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, status) "
                    + "VALUES ('" + PAYMENT_ORDER_ID + "', 'ORD-REFUND-CONCURRENT', "
                    + "'张女士', 100.00, '微信', 'wx_h5', 'SUCCESS')");
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
