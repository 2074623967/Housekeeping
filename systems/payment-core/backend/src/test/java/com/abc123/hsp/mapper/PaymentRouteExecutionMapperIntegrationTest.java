package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
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
 * 支付路由与支付尝试显式归属的 MySQL 兼容 SQL 集成测试。
 */
class PaymentRouteExecutionMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_route_execution;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentRouteExecutionMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentRouteExecutionMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentRouteExecutionMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldBindEachRouteToItsExplicitAttemptEvenWithinSameSecond() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRouteExecutionMapper mapper = session.getMapper(PaymentRouteExecutionMapper.class);
            PaymentRouteExecutionQueryDTO apiQuery = new PaymentRouteExecutionQueryDTO();
            apiQuery.setTerminal("API");

            List<PaymentRouteExecutionListItemDTO> apiRoutes = mapper.findAll(apiQuery);
            assertEquals(1, apiRoutes.size());
            assertEquals("ROUTE-API", apiRoutes.get(0).getRouteNo());
            assertEquals("支付宝", apiRoutes.get(0).getPaymentMethod());
            assertEquals("API", apiRoutes.get(0).getTerminal());
            assertEquals(1L, mapper.count(apiQuery));

            PaymentRouteExecutionQueryDTO miniProgramQuery = new PaymentRouteExecutionQueryDTO();
            miniProgramQuery.setTerminal("MINI_PROGRAM");
            assertEquals("ROUTE-MINI", mapper.findAllForExport(miniProgramQuery).get(0).getRouteNo());
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName()
                    + ".format\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_route_record");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order (payment_order_id VARCHAR(64) PRIMARY KEY, "
                    + "order_no VARCHAR(64), customer_name VARCHAR(128), amount DECIMAL(18, 2))");
            statement.execute("CREATE TABLE t_prepay_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "prepay_order_no VARCHAR(64), payment_order_id VARCHAR(64), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "attempt_no VARCHAR(64), payment_order_id VARCHAR(64), payment_method VARCHAR(32), "
                    + "terminal VARCHAR(32), client_ip VARCHAR(64), idempotency_key VARCHAR(128), "
                    + "request_payload VARCHAR(2048), response_payload VARCHAR(2048), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_route_record (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "route_no VARCHAR(64), attempt_no VARCHAR(64), payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), route_rule VARCHAR(128), route_result VARCHAR(128), "
                    + "ops_config_snapshot_version BIGINT, created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order VALUES "
                    + "('PAY-SAME-SECOND', 'ORD-SAME-SECOND', 'API客户', 88.00)");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, created_at) VALUES "
                    + "('PRE-SAME-SECOND', 'PAY-SAME-SECOND', '2026-09-14 10:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, payment_method, terminal, client_ip, idempotency_key, "
                    + "request_payload, created_at) VALUES "
                    + "('ATT-API', 'PAY-SAME-SECOND', '支付宝', 'API', '127.0.0.1', 'KEY-API', '{}', "
                    + "'2026-09-14 10:00:01'), "
                    + "('ATT-MINI', 'PAY-SAME-SECOND', '微信', 'MINI_PROGRAM', '127.0.0.2', 'KEY-MINI', '{}', "
                    + "'2026-09-14 10:00:01')");
            statement.executeUpdate("INSERT INTO t_payment_route_record "
                    + "(route_no, attempt_no, payment_order_id, channel_code, route_rule, route_result, created_at) VALUES "
                    + "('ROUTE-API', 'ATT-API', 'PAY-SAME-SECOND', 'alipay_h5', 'terminal=API', '支付宝H5', "
                    + "'2026-09-14 10:00:01'), "
                    + "('ROUTE-MINI', 'ATT-MINI', 'PAY-SAME-SECOND', 'wx_h5', 'terminal=MINI_PROGRAM', '微信H5', "
                    + "'2026-09-14 10:00:01')");
            session.commit();
        }
    }
}
