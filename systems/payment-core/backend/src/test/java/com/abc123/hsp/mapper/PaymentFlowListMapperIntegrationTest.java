package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import java.io.InputStream;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
 * 支付流水列表与导出的 MySQL 兼容 SQL 集成测试。
 */
class PaymentFlowListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_flow_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentFlowMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentFlowMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentFlowMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseSourceRankAndIdAsStableTieBreakerForEveryListSort() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);

            PaymentFlowQueryDTO crossSourceQuery = new PaymentFlowQueryDTO();
            crossSourceQuery.setKeyword("CROSS-STABLE");
            assertEquals(Arrays.asList(
                            "FLOW-CROSS-EVENT",
                            "FLOW-CROSS-ROUTE",
                            "FLOW-CROSS-NOTIFY",
                            "FLOW-CROSS-ATTEMPT"),
                    flowNos(mapper.findAllForExport(crossSourceQuery)));

            PaymentFlowQueryDTO attemptQuery = new PaymentFlowQueryDTO();
            attemptQuery.setPaymentOrderId("PAY-ATTEMPT-STABLE");
            attemptQuery.setPageSize(1);
            assertEquals("FLOW-ATTEMPT-STABLE-003", mapper.findAll(attemptQuery).get(0).getFlowNo());
            attemptQuery.setPageNo(2);
            assertEquals("FLOW-ATTEMPT-STABLE-002", mapper.findAll(attemptQuery).get(0).getFlowNo());
            attemptQuery.setPageNo(3);
            assertEquals("FLOW-ATTEMPT-STABLE-001", mapper.findAll(attemptQuery).get(0).getFlowNo());
            assertEquals(3L, mapper.count(attemptQuery));

            attemptQuery.setSortField("flowType");
            attemptQuery.setSortOrder("asc");
            assertEquals(Arrays.asList(
                            "FLOW-ATTEMPT-STABLE-001",
                            "FLOW-ATTEMPT-STABLE-002",
                            "FLOW-ATTEMPT-STABLE-003"),
                    flowNos(mapper.findAllForExport(attemptQuery)));

            PaymentFlowQueryDTO eventQuery = new PaymentFlowQueryDTO();
            eventQuery.setPaymentOrderId("PAY-EVENT-STABLE");
            eventQuery.setSortField("retryCount");
            eventQuery.setSortOrder("desc");
            assertEquals(Arrays.asList(
                            "FLOW-EVENT-STABLE-003",
                            "FLOW-EVENT-STABLE-002",
                            "FLOW-EVENT-STABLE-001"),
                    flowNos(mapper.findAllForExport(eventQuery)));
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
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "payment_order_id VARCHAR(64) PRIMARY KEY, order_no VARCHAR(64), channel_code VARCHAR(64))");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64), "
                    + "payment_order_id VARCHAR(64), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, attempt_no VARCHAR(64), payment_order_id VARCHAR(64), "
                    + "prepay_order_no VARCHAR(64), channel_code VARCHAR(64), terminal VARCHAR(32), "
                    + "client_ip VARCHAR(64), idempotency_key VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), request_payload VARCHAR(2048), response_payload VARCHAR(2048), "
                    + "payment_method VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, notify_no VARCHAR(64), payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), notify_status VARCHAR(32), notify_status_type VARCHAR(32), "
                    + "notify_type VARCHAR(32), notify_payload VARCHAR(2048), notify_result VARCHAR(2048), "
                    + "created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_route_record ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, route_no VARCHAR(64), payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), route_result VARCHAR(128), route_rule VARCHAR(128), "
                    + "created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64), payment_order_id VARCHAR(64), "
                    + "biz_no VARCHAR(64), event_type VARCHAR(64), downstream_system VARCHAR(256), "
                    + "event_topic VARCHAR(128), publish_status VARCHAR(32), retry_count INT, "
                    + "event_payload VARCHAR(2048), created_at DATETIME)");

            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, prepay_order_no, channel_code, terminal, client_ip, "
                    + "idempotency_key, attempt_status, attempt_status_type, request_payload, payment_method, created_at) "
                    + "VALUES ('FLOW-CROSS-ATTEMPT', 'PAY-CROSS-ATTEMPT', 'PRE-CROSS', 'wx_h5', 'H5', "
                    + "'127.0.0.1', 'KEY-CROSS', '处理中', 'warning', '{}', 'CROSS-STABLE', "
                    + "'2026-09-12 12:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_notify_log "
                    + "(notify_no, payment_order_id, channel_code, notify_status, notify_status_type, "
                    + "notify_type, notify_payload, created_at) VALUES "
                    + "('FLOW-CROSS-NOTIFY', 'PAY-CROSS-NOTIFY', 'wx_h5', '已处理', 'success', "
                    + "'支付回调', 'CROSS-STABLE', '2026-09-12 12:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_route_record "
                    + "(route_no, payment_order_id, channel_code, route_result, route_rule, created_at) VALUES "
                    + "('FLOW-CROSS-ROUTE', 'PAY-CROSS-ROUTE', 'wx_h5', '命中', 'CROSS-STABLE', "
                    + "'2026-09-12 12:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, payment_order_id, biz_no, event_type, downstream_system, event_topic, "
                    + "publish_status, retry_count, event_payload, created_at) VALUES "
                    + "('FLOW-CROSS-EVENT', 'PAY-CROSS-EVENT', 'BIZ-CROSS', 'PAYMENT_SUCCESS', "
                    + "'trade-center', 'payment.events', 'SUCCESS', 1, 'CROSS-STABLE', "
                    + "'2026-09-12 12:00:00')");

            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, prepay_order_no, channel_code, terminal, client_ip, "
                    + "idempotency_key, attempt_status, attempt_status_type, request_payload, payment_method, created_at) "
                    + "VALUES "
                    + "('FLOW-ATTEMPT-STABLE-001', 'PAY-ATTEMPT-STABLE', 'PRE-001', 'wx_h5', 'H5', "
                    + "'127.0.0.1', 'KEY-001', '处理中', 'warning', '{}', '微信', '2026-09-12 13:00:00'), "
                    + "('FLOW-ATTEMPT-STABLE-002', 'PAY-ATTEMPT-STABLE', 'PRE-002', 'wx_h5', 'H5', "
                    + "'127.0.0.2', 'KEY-002', '处理中', 'warning', '{}', '微信', '2026-09-12 13:00:00'), "
                    + "('FLOW-ATTEMPT-STABLE-003', 'PAY-ATTEMPT-STABLE', 'PRE-003', 'wx_h5', 'H5', "
                    + "'127.0.0.3', 'KEY-003', '处理中', 'warning', '{}', '微信', '2026-09-12 13:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, payment_order_id, biz_no, event_type, downstream_system, event_topic, "
                    + "publish_status, retry_count, event_payload, created_at) VALUES "
                    + "('FLOW-EVENT-STABLE-001', 'PAY-EVENT-STABLE', 'BIZ-001', 'PAYMENT_SUCCESS', "
                    + "'trade-center', 'payment.events', 'FAILED', 5, '{}', '2026-09-12 14:00:00'), "
                    + "('FLOW-EVENT-STABLE-002', 'PAY-EVENT-STABLE', 'BIZ-002', 'PAYMENT_SUCCESS', "
                    + "'trade-center', 'payment.events', 'FAILED', 5, '{}', '2026-09-12 14:00:00'), "
                    + "('FLOW-EVENT-STABLE-003', 'PAY-EVENT-STABLE', 'BIZ-003', 'PAYMENT_SUCCESS', "
                    + "'trade-center', 'payment.events', 'FAILED', 5, '{}', '2026-09-12 14:00:00')");
            session.commit();
        }
    }

    private List<String> flowNos(List<PaymentFlowListItemDTO> flows) {
        return flows.stream().map(PaymentFlowListItemDTO::getFlowNo).collect(Collectors.toList());
    }
}
