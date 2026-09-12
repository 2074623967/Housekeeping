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
 * 告警供应商派发共享限流 Mapper 集成测试。
 */
class PaymentIssueAlertDeliveryRateLimitMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:issue_alert_delivery_rate_limit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(PaymentIssueAlertDeliveryRateLimitMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream(
                "mapper/PaymentIssueAlertDeliveryRateLimitMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentIssueAlertDeliveryRateLimitMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_alert_delivery_rate_bucket");
            statement.execute("CREATE TABLE t_payment_issue_alert_delivery_rate_bucket ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, provider_code VARCHAR(64), "
                    + "channel_code VARCHAR(64), window_started_at DATETIME, "
                    + "used_count INT, updated_at DATETIME, UNIQUE(provider_code, channel_code))");
            session.commit();
        }
    }

    @Test
    void shouldLimitEachProviderChannelBucketAndResetExpiredWindow() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentIssueAlertDeliveryRateLimitMapper mapper = session.getMapper(
                    PaymentIssueAlertDeliveryRateLimitMapper.class);
            assertEquals(1, mapper.initBucket("PROVIDER_A", "IM"));
            assertEquals(1, mapper.acquire("PROVIDER_A", "IM", 60, 2));
            assertEquals(1, mapper.acquire("PROVIDER_A", "IM", 60, 2));
            assertEquals(0, mapper.acquire("PROVIDER_A", "IM", 60, 2));

            assertEquals(1, mapper.initBucket("PROVIDER_B", "IM"));
            assertEquals(1, mapper.acquire("PROVIDER_B", "IM", 60, 2));
            assertEquals(1, mapper.initBucket("PROVIDER_A", "SMS"));
            assertEquals(1, mapper.acquire("PROVIDER_A", "SMS", 60, 2));

            expireWindow(session.getConnection(), "PROVIDER_A", "IM");

            assertEquals(1, mapper.acquire("PROVIDER_A", "IM", 60, 2));
            assertEquals(1, findUsedCount(session.getConnection(), "PROVIDER_A", "IM"));
            session.commit();
        }
    }

    private void expireWindow(Connection connection, String providerCode, String channelCode) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE t_payment_issue_alert_delivery_rate_bucket SET window_started_at = ? "
                        + "WHERE provider_code = ? AND channel_code = ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusSeconds(61)));
            statement.setString(2, providerCode);
            statement.setString(3, channelCode);
            statement.executeUpdate();
        }
    }

    private int findUsedCount(Connection connection, String providerCode, String channelCode) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT used_count FROM t_payment_issue_alert_delivery_rate_bucket "
                        + "WHERE provider_code = ? AND channel_code = ?")) {
            statement.setString(1, providerCode);
            statement.setString(2, channelCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
