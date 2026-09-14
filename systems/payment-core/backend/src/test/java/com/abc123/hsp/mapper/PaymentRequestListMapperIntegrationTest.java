package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentRequestDetailDTO;
import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
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
 * 支付请求列表与导出的 MySQL 兼容 SQL 集成测试。
 */
class PaymentRequestListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_request_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentRequestMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentRequestMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentRequestMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseStableIdTieBreakerForEveryListSort() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRequestMapper mapper = session.getMapper(PaymentRequestMapper.class);
            PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
            query.setRequestNo("ATTEMPT-STABLE");
            query.setPageSize(1);

            assertEquals("ATTEMPT-STABLE-003", mapper.findAll(query).get(0).getRequestNo());
            query.setPageNo(2);
            assertEquals("ATTEMPT-STABLE-002", mapper.findAll(query).get(0).getRequestNo());
            query.setPageNo(3);
            assertEquals("ATTEMPT-STABLE-001", mapper.findAll(query).get(0).getRequestNo());

            query.setSortField("channelCode");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList("ATTEMPT-STABLE-001", "ATTEMPT-STABLE-002", "ATTEMPT-STABLE-003"),
                    requestNos(mapper.findAllForExport(query)));
            query.setSortField("terminal");
            query.setSortOrder("desc");
            assertEquals(Arrays.asList("ATTEMPT-STABLE-003", "ATTEMPT-STABLE-002", "ATTEMPT-STABLE-001"),
                    requestNos(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldAssociateEachRequestWithItsOwnRouteAndResolveDetailReferences() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRequestMapper mapper = session.getMapper(PaymentRequestMapper.class);
            PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
            query.setPaymentOrderId("PAY-SHARED");

            List<PaymentRequestListItemDTO> listed = mapper.findAll(query);
            assertEquals(2, listed.size());
            assertEquals("ATTEMPT-SHARED-002", listed.get(0).getRequestNo());
            assertEquals("route-second", listed.get(0).getRouteResult());
            assertEquals("ATTEMPT-SHARED-001", listed.get(1).getRequestNo());
            assertEquals("route-first", listed.get(1).getRouteResult());

            List<PaymentRequestListItemDTO> exported = mapper.findAllForExport(query);
            assertEquals("route-second", exported.get(0).getRouteResult());
            assertEquals("route-first", exported.get(1).getRouteResult());

            PaymentRequestDetailDTO detail = mapper.findDetail("ATTEMPT-SHARED-001");
            assertEquals("ORD-STANDARD", detail.getOrderNo());
            assertEquals("ORD-STANDARD", detail.getStandardOrderNo());
            assertEquals("BILL-SHARED-001", detail.getBillNo());
            assertEquals("APP-HOME", detail.getSourceAppId());
            assertEquals("ROUTE-SHARED-001", detail.getRouteNo());
            assertEquals("rule-first", detail.getRouteRule());
            assertEquals("route-first", detail.getRouteResult());
            assertEquals(7L, detail.getRouteOpsConfigSnapshotVersion());
            assertEquals(5L, detail.getOpsConfigSnapshotVersion());
            assertEquals("2026-09-12 12:00:03", detail.getCompletedAt());

            PaymentRequestDetailDTO virtual = mapper.findDetail("ATTEMPT-VIRTUAL-001");
            assertEquals("VIRTUAL-ORDER-001", virtual.getOrderNo());
            assertNull(virtual.getStandardOrderNo());
            assertNull(virtual.getRouteNo());
            assertNull(mapper.findDetail("ATTEMPT-MISSING"));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_route_record");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64) NOT NULL, "
                    + "bill_no VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL, "
                    + "order_no VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, attempt_no VARCHAR(64) NOT NULL, "
                    + "prepay_order_no VARCHAR(64) NOT NULL, payment_order_id VARCHAR(64) NOT NULL, "
                    + "channel_code VARCHAR(64) NOT NULL, payment_method VARCHAR(32) NOT NULL, "
                    + "terminal VARCHAR(32) NOT NULL, client_ip VARCHAR(64) NOT NULL, "
                    + "source_app_id VARCHAR(64), "
                    + "idempotency_key VARCHAR(128) NOT NULL, request_payload VARCHAR(2048) NOT NULL, "
                    + "response_payload VARCHAR(2048), attempt_status VARCHAR(32) NOT NULL, "
                    + "attempt_status_type VARCHAR(32) NOT NULL, ops_config_snapshot_version BIGINT, "
                    + "completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_route_record ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL, "
                    + "attempt_no VARCHAR(64), route_no VARCHAR(64), route_rule VARCHAR(128), "
                    + "route_result VARCHAR(128) NOT NULL, ops_config_snapshot_version BIGINT, "
                    + "created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, order_no) VALUES "
                    + "('PAY-001', 'ORD-001'), ('PAY-002', 'ORD-002'), ('PAY-003', 'ORD-003'), "
                    + "('PAY-SHARED', 'ORD-STANDARD'), ('PAY-VIRTUAL', 'VIRTUAL-ORDER-001')");
            statement.executeUpdate("INSERT INTO t_order(order_no) VALUES ('ORD-STANDARD')");
            statement.executeUpdate("INSERT INTO t_prepay_order(prepay_order_no, bill_no) VALUES "
                    + "('PRE-SHARED-001', 'BILL-SHARED-001'), "
                    + "('PRE-SHARED-002', 'BILL-SHARED-002'), "
                    + "('PRE-VIRTUAL-001', 'BILL-VIRTUAL-001')");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, prepay_order_no, payment_order_id, channel_code, payment_method, terminal, "
                    + "client_ip, source_app_id, idempotency_key, request_payload, attempt_status, "
                    + "attempt_status_type, ops_config_snapshot_version, completed_at, created_at) "
                    + "VALUES "
                    + "('ATTEMPT-STABLE-001', 'PRE-001', 'PAY-001', 'wx_h5', '微信支付', 'H5', "
                    + "'127.0.0.1', 'APP-HOME', 'KEY-001', '{}', '处理中', 'warning', NULL, NULL, "
                    + "'2026-09-12 12:00:00'), "
                    + "('ATTEMPT-STABLE-002', 'PRE-002', 'PAY-002', 'wx_h5', '微信支付', 'H5', "
                    + "'127.0.0.2', 'APP-HOME', 'KEY-002', '{}', '处理中', 'warning', NULL, NULL, "
                    + "'2026-09-12 12:00:00'), "
                    + "('ATTEMPT-STABLE-003', 'PRE-003', 'PAY-003', 'wx_h5', '微信支付', 'H5', "
                    + "'127.0.0.3', 'APP-HOME', 'KEY-003', '{}', '处理中', 'warning', NULL, NULL, "
                    + "'2026-09-12 12:00:00'), "
                    + "('ATTEMPT-SHARED-001', 'PRE-SHARED-001', 'PAY-SHARED', 'wx_h5', '微信支付', 'APP', "
                    + "'10.20.30.40', 'APP-HOME', 'KEY-SHARED-001', '{\"customerName\":\"张三\"}', "
                    + "'成功', 'success', 5, '2026-09-12 12:00:03', '2026-09-12 12:00:01'), "
                    + "('ATTEMPT-SHARED-002', 'PRE-SHARED-002', 'PAY-SHARED', 'alipay_h5', '支付宝', 'H5', "
                    + "'10.20.30.41', 'APP-HOME', 'KEY-SHARED-002', '{}', '失败', 'danger', 6, NULL, "
                    + "'2026-09-12 12:00:02'), "
                    + "('ATTEMPT-VIRTUAL-001', 'PRE-VIRTUAL-001', 'PAY-VIRTUAL', 'wx_h5', '微信支付', 'H5', "
                    + "'10.20.30.42', 'APP-HOME', 'KEY-VIRTUAL-001', '{}', '处理中', 'warning', 8, NULL, "
                    + "'2026-09-12 12:00:04')");
            statement.executeUpdate("INSERT INTO t_payment_route_record "
                    + "(payment_order_id, attempt_no, route_no, route_rule, route_result, "
                    + "ops_config_snapshot_version, created_at) VALUES "
                    + "('PAY-SHARED', 'ATTEMPT-SHARED-001', 'ROUTE-SHARED-001', 'rule-first', "
                    + "'route-first', 7, '2026-09-12 12:00:01'), "
                    + "('PAY-SHARED', 'ATTEMPT-SHARED-002', 'ROUTE-SHARED-002', 'rule-second', "
                    + "'route-second', 8, '2026-09-12 12:00:02'), "
                    + "('PAY-VIRTUAL', NULL, 'ROUTE-HISTORICAL', 'legacy', 'historical-route', 9, "
                    + "'2026-09-12 12:00:04')");
            session.commit();
        }
    }

    private List<String> requestNos(List<PaymentRequestListItemDTO> requests) {
        return requests.stream().map(PaymentRequestListItemDTO::getRequestNo).collect(Collectors.toList());
    }
}
