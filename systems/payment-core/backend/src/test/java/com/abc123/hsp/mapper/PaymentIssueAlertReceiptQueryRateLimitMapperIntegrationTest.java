package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
 * 告警供应商回执查询共享限流 Mapper 集成测试。
 */
class PaymentIssueAlertReceiptQueryRateLimitMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:issue_alert_receipt_query_rate_limit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(PaymentIssueAlertReceiptQueryRateLimitMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream(
                "mapper/PaymentIssueAlertReceiptQueryRateLimitMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentIssueAlertReceiptQueryRateLimitMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_alert_receipt_query_rate_bucket");
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_alert_receipt_query_rate_limit_log");
            statement.execute("CREATE TABLE t_payment_issue_alert_receipt_query_rate_bucket ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR(64) UNIQUE, "
                    + "window_started_at DATETIME, used_count INT, updated_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_issue_alert_receipt_query_rate_limit_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, rejection_no VARCHAR(64) UNIQUE, "
                    + "channel_code VARCHAR(64), alert_no VARCHAR(64), "
                    + "window_seconds INT, max_queries INT, rejected_at DATETIME)");
            session.commit();
        }
    }

    @Test
    void shouldAtomicallyLimitAndResetChannelWindow() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentIssueAlertReceiptQueryRateLimitMapper mapper = session.getMapper(
                    PaymentIssueAlertReceiptQueryRateLimitMapper.class);
            assertEquals(1, mapper.initBucket("IM"));
            assertEquals(1, mapper.acquire("IM", 60, 2));
            assertEquals(1, mapper.acquire("IM", 60, 2));
            assertEquals(0, mapper.acquire("IM", 60, 2));
            assertEquals(1, mapper.insertRejection(
                    "IARQRL-001", "IM", "PIA-IM-LIMITED", 60, 2));

            expireWindow(session.getConnection(), "IM");

            assertEquals(1, mapper.acquire("IM", 60, 2));
            assertEquals(1, findUsedCount(session.getConnection(), "IM"));
            assertEquals(1, findRejectionCount(session.getConnection(), "PIA-IM-LIMITED"));
            session.commit();
        }
    }

    private void expireWindow(Connection connection, String channelCode) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE t_payment_issue_alert_receipt_query_rate_bucket SET window_started_at = ? "
                        + "WHERE channel_code = ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusSeconds(61)));
            statement.setString(2, channelCode);
            statement.executeUpdate();
        }
    }

    private int findUsedCount(Connection connection, String channelCode) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT used_count FROM t_payment_issue_alert_receipt_query_rate_bucket WHERE channel_code = ?")) {
            statement.setString(1, channelCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int findRejectionCount(Connection connection, String alertNo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(1) FROM t_payment_issue_alert_receipt_query_rate_limit_log WHERE alert_no = ?")) {
            statement.setString(1, alertNo);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
