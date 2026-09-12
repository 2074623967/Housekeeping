package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.sql.Statement;
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
 * 日终支付成功事实一致性的 MySQL 兼容 SQL 集成测试。
 */
class PaymentDayEndPaymentSuccessFactsMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_day_end_success_facts;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentDayEndMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentDayEndMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentDayEndMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldCountOnlySuccessOrdersWhoseLatestAttemptFactsMismatch() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);

            assertEquals(Integer.valueOf(3),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            assertEquals(Integer.valueOf(0),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-12"));
            assertEquals(Integer.valueOf(1),
                    mapper.countMissingPaymentSuccessTimeByDate("2026-09-11"));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, success_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL, "
                    + "attempt_status VARCHAR(64), completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, status, success_at, created_at) VALUES "
                    + "('PAY-NORMAL', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-NO-ATTEMPT', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-STATUS-MISMATCH', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-NO-COMPLETION', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-TIME-ONLY', 'SUCCESS', NULL, '2026-09-11 09:00:00'), "
                    + "('PAY-CLOSED', 'CLOSED', NULL, '2026-09-11 09:00:00'), "
                    + "('PAY-NEXT-DAY', 'SUCCESS', '2026-09-12 10:00:00', '2026-09-12 09:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, attempt_status, completed_at, created_at) VALUES "
                    + "('PAY-NORMAL', '成功', '2026-09-11 10:00:00', '2026-09-11 09:30:00'), "
                    + "('PAY-STATUS-MISMATCH', '成功', '2026-09-11 09:30:00', '2026-09-11 09:20:00'), "
                    + "('PAY-STATUS-MISMATCH', '处理中', NULL, '2026-09-11 09:40:00'), "
                    + "('PAY-NO-COMPLETION', '成功', NULL, '2026-09-11 09:30:00'), "
                    + "('PAY-TIME-ONLY', '成功', '2026-09-11 10:00:00', '2026-09-11 09:30:00'), "
                    + "('PAY-NEXT-DAY', '成功', '2026-09-12 10:00:00', '2026-09-12 09:30:00')");
            session.commit();
        }
    }
}
