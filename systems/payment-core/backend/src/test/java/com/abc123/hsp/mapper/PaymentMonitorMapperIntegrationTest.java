package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentChannelMetricDTO;
import java.io.InputStream;
import java.sql.Statement;
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
 * 支付监控聚合查询的 MySQL 兼容 SQL 集成测试。
 */
class PaymentMonitorMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_monitor;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentMonitorMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMonitorMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMonitorMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseStableGroupKeysWhenChannelMetricsHaveEqualCounts() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            List<PaymentChannelMetricDTO> metrics = session.getMapper(PaymentMonitorMapper.class)
                    .findChannelMetrics();

            assertEquals(3, metrics.size());
            assertEquals("channel-a", metrics.get(0).getChannelCode());
            assertEquals("method-a", metrics.get(0).getPaymentMethod());
            assertEquals("channel-a", metrics.get(1).getChannelCode());
            assertEquals("method-b", metrics.get(1).getPaymentMethod());
            assertEquals("channel-b", metrics.get(2).getChannelCode());
            assertEquals("method-a", metrics.get(2).getPaymentMethod());
            metrics.forEach(metric -> {
                assertEquals(Integer.valueOf(2), metric.getTotalCount());
                assertEquals(Integer.valueOf(1), metric.getSuccessCount());
            });
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName()
                    + ".format\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR(64), "
                    + "payment_method VARCHAR(32), status VARCHAR(32), amount DECIMAL(18, 2))");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "channel_code, payment_method, status, amount) VALUES "
                    + "('channel-b', 'method-a', 'SUCCESS', 10.00), "
                    + "('channel-b', 'method-a', 'FAIL', 10.00), "
                    + "('channel-a', 'method-b', 'SUCCESS', 10.00), "
                    + "('channel-a', 'method-b', 'FAIL', 10.00), "
                    + "('channel-a', 'method-a', 'SUCCESS', 10.00), "
                    + "('channel-a', 'method-a', 'FAIL', 10.00)");
            session.commit();
        }
    }
}
