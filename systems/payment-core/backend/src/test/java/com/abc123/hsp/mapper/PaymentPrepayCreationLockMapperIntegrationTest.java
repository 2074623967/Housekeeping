package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
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
 * 预付单创建订单级数据库协调锁集成测试。
 */
class PaymentPrepayCreationLockMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_prepay_creation_lock;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_prepay_creation_lock");
            statement.execute("CREATE TABLE t_payment_prepay_creation_lock ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL, "
                    + "created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, "
                    + "UNIQUE(order_no))");
            session.commit();
        }
    }

    @Test
    void shouldSerializeConcurrentPrepayCreationForSameOrder() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            PaymentMapper firstMapper = firstSession.getMapper(PaymentMapper.class);
            assertEquals(1, firstMapper.initPrepayCreationLock("ORD-CONCURRENT-001"));
            assertEquals("ORD-CONCURRENT-001", firstMapper.lockPrepayCreation("ORD-CONCURRENT-001"));

            Future<String> second = executor.submit(() -> lockInSecondTransaction(secondStarted));
            secondStarted.await(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));

            firstSession.commit();
            assertEquals("ORD-CONCURRENT-001", second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private String lockInSecondTransaction(CountDownLatch started) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            started.countDown();
            mapper.initPrepayCreationLock("ORD-CONCURRENT-001");
            String orderNo = mapper.lockPrepayCreation("ORD-CONCURRENT-001");
            session.commit();
            return orderNo;
        }
    }
}
