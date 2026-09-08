package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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
 * 支付任务中心 Mapper 的 MySQL 兼容 SQL 集成测试。
 */
class PaymentTaskCenterMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_task_center;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment(
                "test",
                new JdbcTransactionFactory(),
                dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentTaskCenterMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentTaskCenterMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentTaskCenterMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndFixtures();
    }

    @Test
    void shouldApplyExponentialBackoffForAutomaticQueriesAndBypassItForManualQueries() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            List<String> automaticCandidates = mapper.findDuePendingPaymentOrderIdsForQuery(
                    30, 60, 2, 900, 20, 100);
            List<String> channelLimitedAutomaticCandidates = mapper.findDuePendingPaymentOrderIdsForQuery(
                    30, 60, 2, 900, 1, 100);
            List<String> manualCandidates = mapper.findPendingPaymentOrderIdsForManualQuery(30, 20, 100);
            List<String> channelLimitedManualCandidates = mapper.findPendingPaymentOrderIdsForManualQuery(
                    30, 2, 100);

            assertEquals(Arrays.asList("PAY-NO-HISTORY", "PAY-BACKOFF-DUE", "PAY-MAX-DUE"),
                    automaticCandidates);
            assertEquals(Arrays.asList("PAY-NO-HISTORY", "PAY-MAX-DUE"),
                    channelLimitedAutomaticCandidates);
            assertEquals(Arrays.asList(
                    "PAY-NO-HISTORY",
                    "PAY-BASE-WAIT",
                    "PAY-BACKOFF-WAIT",
                    "PAY-BACKOFF-DUE",
                    "PAY-MAX-WAIT",
                    "PAY-MAX-DUE"), manualCandidates);
            assertEquals(Arrays.asList(
                    "PAY-NO-HISTORY",
                    "PAY-BASE-WAIT",
                    "PAY-MAX-WAIT",
                    "PAY-MAX-DUE"), channelLimitedManualCandidates);
            assertEquals(6, mapper.countPendingPaymentQueryBacklog(30));
            assertEquals(3, mapper.countDuePendingPaymentQueries(30, 60, 2, 900));
        }
    }

    private void createTablesAndFixtures() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            Connection connection = session.getConnection();
            statement.execute("DROP TABLE IF EXISTS t_payment_notify_log");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "payment_order_id VARCHAR(64), expires_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "notify_status VARCHAR(32), created_at DATETIME)");

            LocalDateTime now = LocalDateTime.now();
            insertPayment(connection, "PAY-NO-HISTORY", "wx_h5", now.minusMinutes(10));
            insertPayment(connection, "PAY-BASE-WAIT", "wx_h5", now.minusMinutes(9));
            insertPayment(connection, "PAY-BACKOFF-WAIT", "wx_h5", now.minusMinutes(8));
            insertPayment(connection, "PAY-BACKOFF-DUE", "wx_h5", now.minusMinutes(7));
            insertPayment(connection, "PAY-MAX-WAIT", "alipay_h5", now.minusMinutes(6));
            insertPayment(connection, "PAY-MAX-DUE", "alipay_h5", now.minusMinutes(5));

            insertQueryHistory(connection, "PAY-BASE-WAIT", now, 30);
            insertQueryHistory(connection, "PAY-BACKOFF-WAIT", now, 300, 90);
            insertQueryHistory(connection, "PAY-BACKOFF-DUE", now, 300, 130);
            insertQueryHistory(connection, "PAY-MAX-WAIT", now, 2100, 1800, 1500, 1200, 800);
            insertQueryHistory(connection, "PAY-MAX-DUE", now, 2100, 1800, 1500, 1200, 920);
            statement.executeUpdate("UPDATE t_payment_notify_log SET notify_status = '查单失败' "
                    + "WHERE payment_order_id = 'PAY-BACKOFF-WAIT' AND id = ("
                    + "SELECT MAX(id) FROM t_payment_notify_log WHERE payment_order_id = 'PAY-BACKOFF-WAIT')");
            connection.commit();
        }
    }

    private void insertPayment(
            Connection connection,
            String paymentOrderId,
            String channelCode,
            LocalDateTime createdAt)
            throws Exception {
        try (PreparedStatement paymentStatement = connection.prepareStatement(
                "INSERT INTO t_payment_order(payment_order_id, channel_code, status, created_at) "
                        + "VALUES (?, ?, ?, ?)");
             PreparedStatement prepayStatement = connection.prepareStatement(
                     "INSERT INTO t_prepay_order(payment_order_id, expires_at) VALUES (?, ?)")) {
            paymentStatement.setString(1, paymentOrderId);
            paymentStatement.setString(2, channelCode);
            paymentStatement.setString(3, "WAIT_CALLBACK");
            paymentStatement.setTimestamp(4, Timestamp.valueOf(createdAt));
            paymentStatement.executeUpdate();

            prepayStatement.setString(1, paymentOrderId);
            prepayStatement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
            prepayStatement.executeUpdate();
        }
    }

    private void insertQueryHistory(
            Connection connection,
            String paymentOrderId,
            LocalDateTime now,
            int... secondsAgo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_payment_notify_log(payment_order_id, notify_status, created_at) VALUES (?, ?, ?)")) {
            for (int seconds : secondsAgo) {
                statement.setString(1, paymentOrderId);
                statement.setString(2, "已查单");
                statement.setTimestamp(3, Timestamp.valueOf(now.minusSeconds(seconds)));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
