package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import java.io.InputStream;
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
                    + "order_no VARCHAR(64) NOT NULL, customer_name VARCHAR(128) NOT NULL, "
                    + "bill_amount DECIMAL(18,2) NOT NULL, paid_amount DECIMAL(18,2) NOT NULL, "
                    + "bill_status VARCHAR(32) NOT NULL, bill_status_type VARCHAR(32) NOT NULL, "
                    + "due_at DATETIME NOT NULL, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "order_no VARCHAR(64) NOT NULL, customer_name VARCHAR(128) NOT NULL, "
                    + "amount DECIMAL(18,2) NOT NULL, payment_method VARCHAR(32) NOT NULL, "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(128), "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, "
                    + "created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "bill_no VARCHAR(64) NOT NULL, order_no VARCHAR(64) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, amount DECIMAL(18,2) NOT NULL, "
                    + "pay_scene VARCHAR(32) NOT NULL, cashier_title VARCHAR(128) NOT NULL, "
                    + "cashier_status VARCHAR(32) NOT NULL, cashier_status_type VARCHAR(32) NOT NULL, "
                    + "payment_order_id VARCHAR(64), created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_order(order_no, customer_name, order_amount, paid_amount) "
                    + "VALUES ('" + ORDER_NO + "', '张女士', 168.00, 0.00)");
            session.commit();
        }
    }

    public static String formatAmount(BigDecimal amount, int scale) {
        return String.format(Locale.ROOT, "%,." + scale + "f", amount);
    }

    public static String formatDate(Timestamp timestamp, String pattern) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(timestamp);
    }
}
