package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.hsp.dto.PaymentCallbackSecurityProfileDTO;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 支付回调渠道安全配置与 nonce 防重 SQL 集成测试。 */
class PaymentCallbackSecurityMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_callback_security;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentCallbackSecurityMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream(
                "mapper/PaymentCallbackSecurityMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentCallbackSecurityMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldLoadChannelProfileAndEnforceNonceLifecycle() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentCallbackSecurityMapper mapper = session.getMapper(PaymentCallbackSecurityMapper.class);
            PaymentCallbackSecurityProfileDTO profile =
                    mapper.findCallbackSecurityProfileByChannelCode("WX_H5");

            assertNotNull(profile);
            assertEquals("wx_h5", profile.getChannelCode());
            assertEquals("HMAC-SHA256", profile.getCallbackSignAlgorithm());
            assertEquals("channel-secret", profile.getCallbackSecret());
            assertEquals(Integer.valueOf(300), profile.getNotifySignWindowSec());
            mapper.insertCallbackNonce("WX_H5", "nonce-001", "PAY-001", 601L);
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            NonceRow nonce = findNonce(session.getConnection(), "nonce-001");
            assertEquals("wx_h5", nonce.channelCode);
            assertEquals("PAY-001", nonce.paymentOrderId);
            assertEquals(601L, Duration.between(
                    nonce.createdAt.toLocalDateTime(),
                    nonce.expiresAt.toLocalDateTime()).getSeconds());

            PaymentCallbackSecurityMapper mapper = session.getMapper(PaymentCallbackSecurityMapper.class);
            assertThrows(PersistenceException.class,
                    () -> mapper.insertCallbackNonce("wx_h5", "nonce-001", "PAY-OTHER", 601L));
            session.rollback();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            expireNonce(session.getConnection(), "nonce-001");
            PaymentCallbackSecurityMapper mapper = session.getMapper(PaymentCallbackSecurityMapper.class);
            mapper.insertCallbackNonce("wx_h5", "nonce-active", "PAY-ACTIVE", 601L);
            mapper.insertCallbackNonce("alipay_h5", "nonce-expired-other", "PAY-OTHER", 601L);
            mapper.insertCallbackNonce("offline_bank", "nonce-expired-third", "PAY-THIRD", 601L);
            expireNonce(session.getConnection(), "nonce-expired-other");
            expireNonce(session.getConnection(), "nonce-expired-third");
            assertEquals(1, mapper.deleteExpiredNonce("WX_H5", "nonce-001"));
            assertEquals(0, countNonce(session.getConnection(), "nonce-001"));
            mapper.insertCallbackNonce("wx_h5", "nonce-001", "PAY-REUSED", 601L);
            assertEquals(1, countNonce(session.getConnection(), "nonce-001"));
            assertEquals(1, countNonce(session.getConnection(), "nonce-expired-other"));
            assertEquals(1, mapper.deleteExpiredNonceBatch(1));
            assertEquals(1, countExpiredNonce(session.getConnection()));
            assertEquals(1, mapper.deleteExpiredNonceBatch(1));
            assertEquals(0, countExpiredNonce(session.getConnection()));
            assertEquals(1, countNonce(session.getConnection(), "nonce-active"));
            session.commit();
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_callback_nonce");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_config");
            statement.execute("CREATE TABLE t_payment_channel_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "callback_sign_algorithm VARCHAR(32) NOT NULL, callback_secret VARCHAR(256), "
                    + "callback_public_key CLOB, callback_notify_url VARCHAR(256), "
                    + "notify_sign_window_sec INT NOT NULL)");
            statement.execute("CREATE TABLE t_payment_callback_nonce ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR(64) NOT NULL, "
                    + "nonce VARCHAR(128) NOT NULL, payment_order_id VARCHAR(64) NOT NULL, "
                    + "expires_at DATETIME NOT NULL, created_at DATETIME NOT NULL, "
                    + "UNIQUE(channel_code, nonce))");
            statement.execute("INSERT INTO t_payment_channel_config ("
                    + "channel_code, callback_sign_algorithm, callback_secret, callback_public_key, "
                    + "callback_notify_url, notify_sign_window_sec) VALUES ("
                    + "'wx_h5', 'HMAC-SHA256', 'channel-secret', NULL, "
                    + "'https://example.test/notify', 300)");
            session.commit();
        }
    }

    private NonceRow findNonce(Connection connection, String nonce) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT channel_code, payment_order_id, created_at, expires_at "
                        + "FROM t_payment_callback_nonce WHERE nonce = ?")) {
            statement.setString(1, nonce);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new NonceRow(
                        resultSet.getString("channel_code"),
                        resultSet.getString("payment_order_id"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("expires_at"));
            }
        }
    }

    private void expireNonce(Connection connection, String nonce) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE t_payment_callback_nonce SET expires_at = ? WHERE nonce = ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)));
            statement.setString(2, nonce);
            statement.executeUpdate();
        }
    }

    private int countNonce(Connection connection, String nonce) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(1) FROM t_payment_callback_nonce WHERE nonce = ?")) {
            statement.setString(1, nonce);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int countExpiredNonce(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(1) FROM t_payment_callback_nonce WHERE expires_at <= NOW()")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static final class NonceRow {
        private final String channelCode;
        private final String paymentOrderId;
        private final Timestamp createdAt;
        private final Timestamp expiresAt;

        private NonceRow(String channelCode, String paymentOrderId, Timestamp createdAt, Timestamp expiresAt) {
            this.channelCode = channelCode;
            this.paymentOrderId = paymentOrderId;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }
}
