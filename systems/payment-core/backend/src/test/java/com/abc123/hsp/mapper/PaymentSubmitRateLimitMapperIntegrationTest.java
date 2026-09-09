package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * 支付提交共享限流 Mapper 集成测试。
 */
class PaymentSubmitRateLimitMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_submit_rate_limit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(PaymentSubmitRateLimitMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream(
                "mapper/PaymentSubmitRateLimitMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentSubmitRateLimitMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_submit_rate_limit_bucket");
            statement.execute("CREATE TABLE t_payment_submit_rate_limit_bucket ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, scope_type VARCHAR(32), scope_key CHAR(64), "
                    + "window_started_at DATETIME, used_count INT, updated_at DATETIME, "
                    + "UNIQUE(scope_type, scope_key))");
            session.commit();
        }
    }

    @Test
    void shouldAtomicallyLimitScopesAndResetExpiredWindow() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentSubmitRateLimitMapper mapper = session.getMapper(PaymentSubmitRateLimitMapper.class);
            String interfaceKey = repeat("a", 64);
            String methodKey = repeat("b", 64);

            assertEquals(1, mapper.initBucket("INTERFACE", interfaceKey));
            assertEquals(1, mapper.acquire("INTERFACE", interfaceKey, 60, 2));
            assertEquals(1, mapper.acquire("INTERFACE", interfaceKey, 60, 2));
            assertEquals(0, mapper.acquire("INTERFACE", interfaceKey, 60, 2));
            assertEquals(1, mapper.initBucket("SOURCE_METHOD", methodKey));
            assertEquals(1, mapper.acquire("SOURCE_METHOD", methodKey, 60, 1));
            assertEquals(0, mapper.acquire("SOURCE_METHOD", methodKey, 60, 1));

            expireWindow(session.getConnection(), "INTERFACE", interfaceKey);
            assertEquals(1, mapper.acquire("INTERFACE", interfaceKey, 60, 2));
            assertEquals(1, findUsedCount(session.getConnection(), "INTERFACE", interfaceKey));
            session.commit();
        }
    }

    @Test
    void shouldAllowOnlyOneConcurrentAcquisitionAtLimitOne() throws Exception {
        String scopeKey = repeat("c", 64);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentSubmitRateLimitMapper mapper = session.getMapper(PaymentSubmitRateLimitMapper.class);
            assertEquals(1, mapper.initBucket("INTERFACE", scopeKey));
            session.commit();
        }
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> acquireConcurrently(scopeKey, ready, start));
            Future<Integer> second = executor.submit(() -> acquireConcurrently(scopeKey, ready, start));
            ready.await();
            start.countDown();

            assertEquals(1, first.get().intValue() + second.get().intValue());
        } finally {
            executor.shutdownNow();
        }
    }

    private int acquireConcurrently(String scopeKey,
                                    CountDownLatch ready,
                                    CountDownLatch start) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentSubmitRateLimitMapper mapper = session.getMapper(PaymentSubmitRateLimitMapper.class);
            ready.countDown();
            start.await();
            int result = mapper.acquire("INTERFACE", scopeKey, 60, 1);
            session.commit();
            return result;
        }
    }

    private void expireWindow(Connection connection, String scopeType, String scopeKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE t_payment_submit_rate_limit_bucket SET window_started_at = ? "
                        + "WHERE scope_type = ? AND scope_key = ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusSeconds(61)));
            statement.setString(2, scopeType);
            statement.setString(3, scopeKey);
            statement.executeUpdate();
        }
    }

    private int findUsedCount(Connection connection, String scopeType, String scopeKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT used_count FROM t_payment_submit_rate_limit_bucket "
                        + "WHERE scope_type = ? AND scope_key = ?")) {
            statement.setString(1, scopeType);
            statement.setString(2, scopeKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int index = 0; index < times; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
