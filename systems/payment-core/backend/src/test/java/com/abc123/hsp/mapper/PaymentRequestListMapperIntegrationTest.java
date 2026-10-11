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
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentRequestMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentRequestMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/PaymentMapper.xml",
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
            assertEquals("HOME_REPAIR", listed.get(0).getPayScene());
            assertEquals("route-second", listed.get(0).getRouteResult());
            assertEquals("ATTEMPT-SHARED-001", listed.get(1).getRequestNo());
            assertEquals("HOME_CLEAN", listed.get(1).getPayScene());
            assertEquals("route-first", listed.get(1).getRouteResult());

            List<PaymentRequestListItemDTO> exported = mapper.findAllForExport(query);
            assertEquals("route-second", exported.get(0).getRouteResult());
            assertEquals("route-first", exported.get(1).getRouteResult());

            PaymentRequestDetailDTO detail = mapper.findDetail("ATTEMPT-SHARED-001");
            assertEquals("ORD-STANDARD", detail.getOrderNo());
            assertEquals("ORD-STANDARD", detail.getStandardOrderNo());
            assertEquals("BILL-SHARED-001", detail.getBillNo());
            assertEquals("HOME_CLEAN", detail.getPayScene());
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

    @Test
    void shouldNotReadRequestDetailThroughNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertNull(session.getMapper(PaymentRequestMapper.class).findDetail("attempt-shared-001"));
        }
    }

    @Test
    void shouldNotLinkVirtualRequestToCollidingStandardOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRequestMapper mapper = session.getMapper(PaymentRequestMapper.class);
            statement.executeUpdate("UPDATE t_payment_order SET order_no = 'ORD-STANDARD' "
                    + "WHERE payment_order_id = 'PAY-VIRTUAL'");
            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-STANDARD', "
                    + "source_kind = 'VIRTUAL' WHERE payment_order_id = 'PAY-VIRTUAL'");
            assertEquals("ORD-STANDARD", mapper.findDetail("ATTEMPT-VIRTUAL-001").getOrderNo());
            assertNull(mapper.findDetail("ATTEMPT-VIRTUAL-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'standard_order' "
                    + "WHERE payment_order_id = 'PAY-VIRTUAL'");
            session.clearCache();
            assertNull(mapper.findDetail("ATTEMPT-VIRTUAL-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'STANDARD_ORDER' "
                    + "WHERE payment_order_id = 'PAY-VIRTUAL'");
            session.clearCache();
            assertEquals("ORD-STANDARD", mapper.findDetail("ATTEMPT-VIRTUAL-001").getStandardOrderNo());
        }
    }

    @Test
    void shouldNotJoinRouteThroughAttemptNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET attempt_no = 'attempt-shared-001' "
                    + "WHERE route_no = 'ROUTE-SHARED-001'");
            assertNoForeignRoute(session.getMapper(PaymentRequestMapper.class));
        }
    }

    @Test
    void shouldNotJoinRouteFromAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET payment_order_id = 'PAY-VIRTUAL' "
                    + "WHERE route_no = 'ROUTE-SHARED-001'");
            assertNoForeignRoute(session.getMapper(PaymentRequestMapper.class));
        }
    }

    @Test
    void shouldNotJoinRouteThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET payment_order_id = 'pay-shared' "
                    + "WHERE route_no = 'ROUTE-SHARED-001'");
            assertNoForeignRoute(session.getMapper(PaymentRequestMapper.class));
        }
    }

    @Test
    void shouldNotJoinPrepayFromAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'PAY-VIRTUAL' "
                    + "WHERE prepay_order_no = 'PRE-SHARED-001'");
            assertNoForeignPrepay(session.getMapper(PaymentRequestMapper.class));
        }
    }

    @Test
    void shouldNotJoinPrepayThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET prepay_order_no = 'pre-shared-001' "
                    + "WHERE attempt_no = 'ATTEMPT-SHARED-001'");
            assertNoForeignPrepay(session.getMapper(PaymentRequestMapper.class));
        }
    }

    @Test
    void shouldNotJoinPrepayThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'pay-shared' "
                    + "WHERE prepay_order_no = 'PRE-SHARED-001'");
            assertNoForeignPrepay(session.getMapper(PaymentRequestMapper.class));
        }
    }

    private void assertNoForeignRoute(PaymentRequestMapper mapper) {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestNo("ATTEMPT-SHARED-001");
        assertNull(mapper.findAll(query).get(0).getRouteResult());
        assertNull(mapper.findAllForExport(query).get(0).getRouteResult());
        assertNull(mapper.findDetail("ATTEMPT-SHARED-001").getRouteNo());
    }

    private void assertNoForeignPrepay(PaymentRequestMapper mapper) {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestNo("ATTEMPT-SHARED-001");
        assertNull(mapper.findAll(query).get(0).getPayScene());
        assertNull(mapper.findAllForExport(query).get(0).getPayScene());
        PaymentRequestDetailDTO detail = mapper.findDetail("ATTEMPT-SHARED-001");
        assertNull(detail.getPayScene());
        assertNull(detail.getBillNo());
    }

    @Test
    void shouldNotJoinPaymentOrderThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET payment_order_id = 'pay-shared' "
                    + "WHERE payment_order_id = 'PAY-SHARED'");
            PaymentRequestMapper mapper = session.getMapper(PaymentRequestMapper.class);
            PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
            query.setRequestNo("ATTEMPT-SHARED-001");
            assertNull(mapper.findDetail("ATTEMPT-SHARED-001").getOrderNo());
            assertNull(mapper.findDetail("ATTEMPT-SHARED-001").getStandardOrderNo());
            assertNull(mapper.findAll(query).get(0).getOrderNo());
            assertNull(mapper.findAllForExport(query).get(0).getOrderNo());
            query.setOrderNo("ORD-STANDARD");
            assertEquals(0L, mapper.count(query));
            assertEquals(0L, mapper.findOverviewSummary(query).getTotalRequestCount());
        }
    }

    @Test
    void shouldCountManualConfirmationAsProcessingAndIncludeItInProcessingFilter() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRequestMapper mapper = session.getMapper(PaymentRequestMapper.class);
            PaymentRequestQueryDTO overviewQuery = new PaymentRequestQueryDTO();
            assertEquals(7L, mapper.findOverviewSummary(overviewQuery).getTotalRequestCount());
            assertEquals(5L, mapper.findOverviewSummary(overviewQuery).getProcessingRequestCount());

            PaymentRequestQueryDTO processingQuery = new PaymentRequestQueryDTO();
            processingQuery.setRequestStatus("处理中");
            assertEquals(5L, mapper.count(processingQuery));
            assertEquals(1L, mapper.findAllForExport(processingQuery).stream()
                    .filter(item -> "待人工确认".equals(item.getRequestStatus()))
                    .count());
        }
    }

    @Test
    void shouldProjectOwningPaymentSuccessFactIssueAcrossRequestsAndDetail() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRequestMapper mapper = session.getMapper(PaymentRequestMapper.class);
            PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
            query.setPaymentOrderId("PAY-MANUAL");
            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findDetail("ATTEMPT-MANUAL-001").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_order SET status = 'SUCCESS', status_type = 'success', "
                    + "channel_code = 'wx_h5', payment_method = '微信支付', channel_transaction_no = 'CH-001', "
                    + "created_at = '2026-09-12 11:59:59.000', success_at = '2026-09-12 12:00:03.123' "
                    + "WHERE payment_order_id = 'PAY-001'");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '成功', "
                    + "attempt_status_type = 'success', completed_at = '2026-09-12 12:00:03.122' "
                    + "WHERE attempt_no = 'ATTEMPT-STABLE-001'");
            statement.executeUpdate("INSERT INTO t_prepay_order("
                    + "prepay_order_no, bill_no, pay_scene, payment_order_id, order_no, amount, source_kind) "
                    + "VALUES ('PRE-001', 'BILL-001', 'HOME_CLEAN', 'PAY-001', 'ORD-001', 1.00, 'VIRTUAL')");
            session.commit();
            query.setPaymentOrderId("PAY-001");
            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findDetail("ATTEMPT-STABLE-001").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='virtual' "
                    + "WHERE payment_order_id='PAY-001'");
            session.commit();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findDetail("ATTEMPT-STABLE-001")
                    .getSuccessFactIssue());
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind=CONCAT('VIRTUAL', CHAR(0)) "
                    + "WHERE payment_order_id='PAY-001'");
            session.commit();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='VIRTUAL' "
                    + "WHERE payment_order_id='PAY-001'");
            session.commit();
            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = '2026-09-12 12:00:03.124' "
                    + "WHERE attempt_no = 'ATTEMPT-STABLE-001'");
            session.commit();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findDetail("ATTEMPT-STABLE-001")
                    .getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-001'");
            session.commit();
            assertEquals("SUCCESS_TIME_MISSING", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_TIME_MISSING", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_TIME_MISSING", mapper.findDetail("ATTEMPT-STABLE-001")
                    .getSuccessFactIssue());
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
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "bill_no VARCHAR(64) NOT NULL, pay_scene VARCHAR(32) NOT NULL, "
                    + "payment_order_id VARCHAR_IGNORECASE(64), order_no VARCHAR(64), amount DECIMAL(18,2), "
                    + "source_kind VARCHAR_IGNORECASE(16) DEFAULT 'STANDARD_ORDER')");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "order_no VARCHAR(64) NOT NULL, amount DECIMAL(18,2) DEFAULT 1.00, "
                    + "channel_code VARCHAR(64), payment_method VARCHAR(32), "
                    + "channel_transaction_no VARCHAR(64), status VARCHAR(32), status_type VARCHAR(32), "
                    + "created_at DATETIME(3), success_at DATETIME(3))");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, attempt_no VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "prepay_order_no VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "channel_code VARCHAR(64) NOT NULL, payment_method VARCHAR(32) NOT NULL, "
                    + "terminal VARCHAR(32) NOT NULL, client_ip VARCHAR(64) NOT NULL, "
                    + "source_app_id VARCHAR(64), "
                    + "idempotency_key VARCHAR(128) NOT NULL, request_payload VARCHAR(2048) NOT NULL, "
                    + "response_payload VARCHAR(2048), attempt_status VARCHAR(32) NOT NULL, "
                    + "attempt_status_type VARCHAR(32) NOT NULL, ops_config_snapshot_version BIGINT, "
                    + "completed_at DATETIME(3), created_at DATETIME(3) NOT NULL)");
            statement.execute("CREATE TABLE t_payment_route_record ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "attempt_no VARCHAR_IGNORECASE(64), route_no VARCHAR(64), route_rule VARCHAR(128), "
                    + "route_result VARCHAR(128) NOT NULL, ops_config_snapshot_version BIGINT, "
                    + "created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, order_no) VALUES "
                    + "('PAY-001', 'ORD-001'), ('PAY-002', 'ORD-002'), ('PAY-003', 'ORD-003'), "
                    + "('PAY-SHARED', 'ORD-STANDARD'), ('PAY-VIRTUAL', 'VIRTUAL-ORDER-001')");
            statement.executeUpdate("INSERT INTO t_order(order_no) VALUES ('ORD-STANDARD')");
            statement.executeUpdate("INSERT INTO t_prepay_order(prepay_order_no, bill_no, pay_scene) VALUES "
                    + "('PRE-SHARED-001', 'BILL-SHARED-001', 'HOME_CLEAN'), "
                    + "('PRE-SHARED-002', 'BILL-SHARED-002', 'HOME_REPAIR'), "
                    + "('PRE-VIRTUAL-001', 'BILL-VIRTUAL-001', 'HOME_CLEAN')");
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'PAY-SHARED' "
                    + "WHERE prepay_order_no LIKE 'PRE-SHARED-%'");
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'PAY-VIRTUAL' "
                    + "WHERE prepay_order_no = 'PRE-VIRTUAL-001'");
            statement.executeUpdate("UPDATE t_prepay_order SET order_no = "
                    + "(SELECT order_no FROM t_payment_order WHERE payment_order_id = t_prepay_order.payment_order_id), "
                    + "amount = (SELECT amount FROM t_payment_order "
                    + "WHERE payment_order_id = t_prepay_order.payment_order_id)");
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
                    + "'2026-09-12 12:00:04'), "
                    + "('ATTEMPT-MANUAL-001', 'PRE-MANUAL-001', 'PAY-MANUAL', 'offline_bank', '银行转账', 'H5', "
                    + "'10.20.30.43', 'APP-HOME', 'KEY-MANUAL-001', '{}', '待人工确认', 'warn', NULL, NULL, "
                    + "'2026-09-12 12:00:05')");
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
