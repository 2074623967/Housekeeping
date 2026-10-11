package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
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
 * 支付详情路由、通知与事件轨迹排序的 MySQL 兼容 SQL 集成测试。
 */
class PaymentDetailTraceOrderingMapperIntegrationTest {

    private static final String PAYMENT_ORDER_ID = "PAY-TRACE-STABLE";

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_detail_trace_ordering;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseIdAsStableTieBreakerForEveryDetailTrace() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(Arrays.asList(
                            "2026-09-12 17:00:00 | 通知-003 | 已处理",
                            "2026-09-12 17:00:00 | 通知-002 | 已处理",
                            "2026-09-12 17:00:00 | 通知-001 | 已处理"),
                    mapper.findNotifyLogs(PAYMENT_ORDER_ID));
            assertEquals(Arrays.asList(
                            "2026-09-12 17:00:00 | 规则-003 | 命中-003",
                            "2026-09-12 17:00:00 | 规则-002 | 命中-002",
                            "2026-09-12 17:00:00 | 规则-001 | 命中-001"),
                    mapper.findRouteLogs(PAYMENT_ORDER_ID));
            assertEquals(Arrays.asList(
                            "EVENT-003 | payment.trace | SUCCESS | 2026-09-12 17:00:00",
                            "EVENT-002 | payment.trace | SUCCESS | 2026-09-12 17:00:00",
                            "EVENT-001 | payment.trace | SUCCESS | 2026-09-12 17:00:00"),
                    mapper.findEventItems(PAYMENT_ORDER_ID));
        }
    }

    @Test
    void shouldNotMixCaseVariantPaymentTraces() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_notify_log "
                    + "(payment_order_id, notify_type, notify_status, created_at) VALUES "
                    + "('pay-trace-stable', '别名通知', '已处理', '2026-09-12 17:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_route_record "
                    + "(payment_order_id, route_rule, route_result, created_at) VALUES "
                    + "('pay-trace-stable', '别名规则', '误挂', '2026-09-12 17:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(payment_order_id, event_type, event_topic, publish_status, created_at) VALUES "
                    + "('pay-trace-stable', 'ALIAS', 'payment.trace', 'SUCCESS', '2026-09-12 17:00:00')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(3, mapper.findNotifyLogs(PAYMENT_ORDER_ID).size());
            assertEquals(3, mapper.findRouteLogs(PAYMENT_ORDER_ID).size());
            assertEquals(3, mapper.findEventItems(PAYMENT_ORDER_ID).size());
            assertEquals(Collections.emptyList(), mapper.findNotifyLogs("PAY-TRACE-UNKNOWN"));
            assertEquals(Collections.singletonList("2026-09-12 17:00:00 | 别名通知 | 已处理"),
                    mapper.findNotifyLogs("pay-trace-stable"));
            assertEquals(Collections.singletonList("2026-09-12 17:00:00 | 别名规则 | 误挂"),
                    mapper.findRouteLogs("pay-trace-stable"));
            assertEquals(Collections.singletonList("ALIAS | payment.trace | SUCCESS | 2026-09-12 17:00:00"),
                    mapper.findEventItems("pay-trace-stable"));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_payment_route_record");
            statement.execute("DROP TABLE IF EXISTS t_payment_notify_log");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "notify_type VARCHAR(32), notify_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_route_record ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "route_rule VARCHAR(128), route_result VARCHAR(128), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "event_type VARCHAR(64), event_topic VARCHAR(128), publish_status VARCHAR(32), "
                    + "created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_notify_log "
                    + "(payment_order_id, notify_type, notify_status, created_at) VALUES "
                    + "('PAY-TRACE-STABLE', '通知-001', '已处理', '2026-09-12 17:00:00'), "
                    + "('PAY-TRACE-STABLE', '通知-002', '已处理', '2026-09-12 17:00:00'), "
                    + "('PAY-TRACE-STABLE', '通知-003', '已处理', '2026-09-12 17:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_route_record "
                    + "(payment_order_id, route_rule, route_result, created_at) VALUES "
                    + "('PAY-TRACE-STABLE', '规则-001', '命中-001', '2026-09-12 17:00:00'), "
                    + "('PAY-TRACE-STABLE', '规则-002', '命中-002', '2026-09-12 17:00:00'), "
                    + "('PAY-TRACE-STABLE', '规则-003', '命中-003', '2026-09-12 17:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(payment_order_id, event_type, event_topic, publish_status, created_at) VALUES "
                    + "('PAY-TRACE-STABLE', 'EVENT-001', 'payment.trace', 'SUCCESS', '2026-09-12 17:00:00'), "
                    + "('PAY-TRACE-STABLE', 'EVENT-002', 'payment.trace', 'SUCCESS', '2026-09-12 17:00:00'), "
                    + "('PAY-TRACE-STABLE', 'EVENT-003', 'payment.trace', 'SUCCESS', '2026-09-12 17:00:00')");
            session.commit();
        }
    }
}
