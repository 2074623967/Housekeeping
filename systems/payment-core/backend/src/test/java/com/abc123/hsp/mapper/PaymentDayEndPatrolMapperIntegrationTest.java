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
 * 支付日终巡检 Mapper 的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentDayEndPatrolMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_day_end_patrol;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(PaymentDayEndMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RefundMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/RefundMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentDayEndMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentDayEndMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_day_end_batch");
            statement.execute("CREATE TABLE t_payment_day_end_batch ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "biz_date VARCHAR(10) NOT NULL, run_mode VARCHAR(32) NOT NULL)");
            statement.execute("INSERT INTO t_payment_day_end_batch (biz_date, run_mode) VALUES "
                    + "('2026-09-11', 'AUTO'), ('2026-09-11', 'AUTO'), "
                    + "('2026-09-11', 'MANUAL'), ('2026-09-10', 'AUTO')");
            session.commit();
        }
    }

    @Test
    void shouldCountAllAndAutomaticBatchesByBusinessDate() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);

            assertEquals(3, mapper.countBatchesByDate("2026-09-11"));
            assertEquals(2, mapper.countAutoBatchesByDate("2026-09-11"));
            assertEquals(0, mapper.countAutoBatchesByDate("2026-09-09"));
        }
    }
}
