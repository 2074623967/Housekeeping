package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.math.BigDecimal;
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
 * 日终退款成功与完整性事实的 MySQL 兼容 SQL 集成测试。
 */
class PaymentDayEndRefundFactsMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_day_end_refund_facts;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldAssignSuccessfulRefundsBySuccessDateAndMissingFactsByApplicationDate() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);

            assertEquals(Integer.valueOf(2), mapper.countSuccessRefundsByDate("2026-09-12"));
            assertEquals(new BigDecimal("30.00"), mapper.sumSuccessRefundAmountByDate("2026-09-12"));
            assertEquals(Integer.valueOf(0), mapper.countSuccessRefundsByDate("2026-09-11"));
            assertEquals(Integer.valueOf(1), mapper.countMissingRefundSuccessTimeByDate("2026-09-11"));
            assertEquals(Integer.valueOf(0), mapper.countMissingRefundSuccessTimeByDate("2026-09-12"));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_amount DECIMAL(18, 2) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, applied_at DATETIME NOT NULL, success_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_refund_order(refund_amount, status, applied_at, success_at) VALUES "
                    + "(10.00, 'SUCCESS', '2026-09-11 10:00:00', '2026-09-12 09:00:00'), "
                    + "(20.00, 'SUCCESS', '2026-09-12 10:00:00', '2026-09-12 11:00:00'), "
                    + "(30.00, 'SUCCESS', '2026-09-11 12:00:00', NULL), "
                    + "(40.00, 'PROCESSING', '2026-09-11 13:00:00', NULL)");
            session.commit();
        }
    }
}
