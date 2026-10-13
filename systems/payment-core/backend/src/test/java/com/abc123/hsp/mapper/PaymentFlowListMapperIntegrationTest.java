package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import java.io.InputStream;
import java.sql.PreparedStatement;
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
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentFlowMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentFlowMapper.xml",
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

    @Test
    void shouldSearchFlowIdentifiersAndPayloadByLiteralSubstring() throws Exception {
        String suffix = "%_!\\\"";
        String paymentOrderId = "PAY-FLOW-" + suffix;
        String orderNo = "ORD-FLOW-" + suffix;
        String channelCode = "channel-" + suffix;
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             PreparedStatement paymentInsert = session.getConnection().prepareStatement(
                     "INSERT INTO t_payment_order (payment_order_id, order_no, channel_code, payment_method) "
                             + "VALUES (?, ?, ?, '本地方式')");
             PreparedStatement attemptInsert = session.getConnection().prepareStatement(
                     "INSERT INTO t_payment_attempt (attempt_no, payment_order_id, channel_code, terminal, "
                             + "attempt_status, request_payload, response_payload, payment_method, created_at) "
                             + "VALUES ('FLOW-LITERAL', ?, ?, 'LITERAL', '处理中', 'plain', 'plain', ?, NOW())");
             PreparedStatement payloadUpdate = session.getConnection().prepareStatement(
                     "UPDATE t_payment_attempt SET payment_method = ?, request_payload = ?, "
                             + "response_payload = ? WHERE attempt_no = 'FLOW-LITERAL'")) {
            paymentInsert.setString(1, paymentOrderId);
            paymentInsert.setString(2, orderNo);
            paymentInsert.setString(3, channelCode);
            paymentInsert.executeUpdate();
            attemptInsert.setString(1, paymentOrderId);
            attemptInsert.setString(2, channelCode);
            attemptInsert.setString(3, "method-" + suffix);
            attemptInsert.executeUpdate();
            statement.executeUpdate("INSERT INTO t_payment_order "
                    + "(payment_order_id, order_no, channel_code, payment_method) "
                    + "VALUES ('PAY-FLOW-PLAIN', 'ORD-FLOW-PLAIN', 'channel-plain', '本地方式')");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, channel_code, terminal, attempt_status, "
                    + "request_payload, response_payload, payment_method, created_at) "
                    + "VALUES ('FLOW-PLAIN', 'PAY-FLOW-PLAIN', 'channel-plain', 'LITERAL', "
                    + "'处理中', 'plain', 'plain', 'plain', NOW())");

            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            for (String fragment : Arrays.asList("%", "_", "!", "\\", suffix)) {
                PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
                query.setTerminal("LITERAL");
                query.setPaymentOrderId(fragment);
                assertLiteralFlowResults(mapper, query);
                query.setPaymentOrderId(null);
                query.setOrderNo(fragment);
                assertLiteralFlowResults(mapper, query);
                query.setOrderNo(null);
                query.setChannelCode(fragment);
                assertLiteralFlowResults(mapper, query);
                query.setChannelCode(null);
                query.setKeyword(fragment);
                assertLiteralFlowResults(mapper, query);
            }

            PaymentFlowQueryDTO keywordQuery = new PaymentFlowQueryDTO();
            keywordQuery.setTerminal("LITERAL");
            keywordQuery.setKeyword(suffix);
            payloadUpdate.setString(1, "plain");
            payloadUpdate.setString(2, "request-" + suffix);
            payloadUpdate.setString(3, "plain");
            payloadUpdate.executeUpdate();
            session.clearCache();
            assertLiteralFlowResults(mapper, keywordQuery);
            payloadUpdate.setString(1, "plain");
            payloadUpdate.setString(2, "plain");
            payloadUpdate.setString(3, "response-" + suffix);
            payloadUpdate.executeUpdate();
            session.clearCache();
            assertLiteralFlowResults(mapper, keywordQuery);
        }
    }

    private void assertLiteralFlowResults(PaymentFlowMapper mapper, PaymentFlowQueryDTO query) {
        assertEquals(1L, mapper.count(query), query.toString());
        assertEquals(Arrays.asList("FLOW-LITERAL"), flowNos(mapper.findAll(query)));
        assertEquals(Arrays.asList("FLOW-LITERAL"), flowNos(mapper.findAllForExport(query)));
    }

    @Test
    void shouldResolveCompositeFlowDetailsAndOnlyExactRouteOwnership() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            assertEquals("PRE-DETAIL",
                    mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getPrepayOrderNo());
            assertEquals("HOME_CLEAN",
                    mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getPayScene());
            assertEquals("HOME_CLEAN", mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getPayScene());
            assertEquals("HOME_CLEAN", mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getPayScene());
            assertEquals("HOME_CLEAN", mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getPayScene());
            PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
            query.setPaymentOrderId("PAY-DETAIL");
            for (PaymentFlowListItemDTO item : mapper.findAllForExport(query)) {
                if (!"FLOW-ROUTE-ORPHAN".equals(item.getFlowNo())) {
                    assertEquals("HOME_CLEAN", item.getPayScene());
                }
            }
            assertEquals("FLOW-SAME",
                    mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getPaymentRequestNo());
            assertNull(mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getBillNo());
            assertNull(mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getPrepayOrderNo());
            assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-ROUTE-ORPHAN").getPaymentRequestNo());
        }
    }

    @Test
    void shouldNotTreatRefundOrVirtualEventFlowAsStandardOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            assertEquals("ORD-DETAIL", mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'REFUND_SUBMIT' "
                    + "WHERE event_no = 'FLOW-EVENT'");
            session.clearCache();
            assertNull(mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'payment_success' "
                    + "WHERE event_no = 'FLOW-EVENT'");
            session.clearCache();
            assertNull(mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'PAYMENT_SUCCESS' "
                    + "WHERE event_no = 'FLOW-EVENT'");
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'VIRTUAL' "
                    + "WHERE payment_order_id = 'PAY-DETAIL'");
            session.clearCache();
            assertNull(mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'STANDARD_ORDER', "
                    + "order_no = 'OTHER-ORDER' WHERE payment_order_id = 'PAY-DETAIL'");
            session.clearCache();
            assertNull(mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-DETAIL' "
                    + "WHERE payment_order_id = 'PAY-DETAIL'");
            session.clearCache();
            assertEquals("ORD-DETAIL", mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getStandardOrderNo());
        }
    }

    @Test
    void shouldNotLinkVirtualPaymentFlowToCollidingStandardOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            assertEquals("ORD-DETAIL", mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getStandardOrderNo());
            assertEquals("ORD-DETAIL", mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getStandardOrderNo());
            assertEquals("ORD-DETAIL", mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'VIRTUAL' "
                    + "WHERE payment_order_id = 'PAY-DETAIL'");
            session.clearCache();
            assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getStandardOrderNo());
            assertNull(mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getStandardOrderNo());
            assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'standard_order' "
                    + "WHERE payment_order_id = 'PAY-DETAIL'");
            session.clearCache();
            assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getStandardOrderNo());
            assertNull(mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getStandardOrderNo());
            assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'STANDARD_ORDER', "
                    + "order_no = 'OTHER-ORDER' WHERE payment_order_id = 'PAY-DETAIL'");
            session.clearCache();
            assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getStandardOrderNo());
            assertNull(mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getStandardOrderNo());
            assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-DETAIL' "
                    + "WHERE payment_order_id = 'PAY-DETAIL'");
            session.clearCache();
            assertEquals("ORD-DETAIL", mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getStandardOrderNo());
            assertEquals("ORD-DETAIL", mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getStandardOrderNo());
            assertEquals("ORD-DETAIL", mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getStandardOrderNo());
        }
    }

    @Test
    void shouldNotReadFlowDetailThroughNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            assertEquals("FLOW-SAME", mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getFlowNo());
            assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "flow-same"));
            assertNull(mapper.findDetail("CHANNEL_NOTIFY", "flow-notify"));
            assertNull(mapper.findDetail("ROUTE_RECORD", "flow-same"));
            assertNull(mapper.findDetail("BUSINESS_EVENT", "flow-event"));
        }
    }

    @Test
    void shouldNotJoinRouteToAttemptThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET attempt_no = 'flow-same' "
                    + "WHERE route_no = 'FLOW-SAME'");
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getRouteNo());
            assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getPrepayOrderNo());
            PaymentFlowListItemDTO route = mapper.findAllForExport(new PaymentFlowQueryDTO()).stream()
                    .filter(item -> "ROUTE_RECORD".equals(item.getFlowSource())
                            && "FLOW-SAME".equals(item.getFlowNo()))
                    .findFirst().orElseThrow(AssertionError::new);
            assertNull(route.getPrepayOrderNo());
        }
    }

    @Test
    void shouldNotJoinRouteToAttemptFromAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET payment_order_id = 'PAY-OTHER' "
                    + "WHERE route_no = 'FLOW-SAME'");
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getRouteNo());
            assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getPrepayOrderNo());
            PaymentFlowListItemDTO route = mapper.findAllForExport(new PaymentFlowQueryDTO()).stream()
                    .filter(item -> "ROUTE_RECORD".equals(item.getFlowSource())
                            && "FLOW-SAME".equals(item.getFlowNo()))
                    .findFirst().orElseThrow(AssertionError::new);
            assertNull(route.getPrepayOrderNo());
        }
    }

    @Test
    void shouldNotJoinRouteToAttemptThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET payment_order_id = 'pay-detail' "
                    + "WHERE route_no = 'FLOW-SAME'");
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getRouteNo());
            assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getPrepayOrderNo());
            PaymentFlowListItemDTO route = mapper.findAllForExport(new PaymentFlowQueryDTO()).stream()
                    .filter(item -> "ROUTE_RECORD".equals(item.getFlowSource())
                            && "FLOW-SAME".equals(item.getFlowNo()))
                    .findFirst().orElseThrow(AssertionError::new);
            assertNull(route.getPrepayOrderNo());
        }
    }

    @Test
    void shouldNotAttributePrepayFromAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'PAY-OTHER' "
                    + "WHERE prepay_order_no = 'PRE-DETAIL'");
            assertNoForeignPrepayAttribution(session.getMapper(PaymentFlowMapper.class), true);
        }
    }

    @Test
    void shouldNotAttributePrepayThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET prepay_order_no = 'pre-detail' "
                    + "WHERE attempt_no = 'FLOW-SAME'");
            assertNoForeignPrepayAttribution(session.getMapper(PaymentFlowMapper.class), false);
        }
    }

    @Test
    void shouldNotAttributePrepayThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'pay-detail' "
                    + "WHERE prepay_order_no = 'PRE-DETAIL'");
            assertNoForeignPrepayAttribution(session.getMapper(PaymentFlowMapper.class), true);
        }
    }

    private void assertNoForeignPrepayAttribution(PaymentFlowMapper mapper, boolean ownerMismatch) {
        PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
        for (PaymentFlowListItemDTO item : mapper.findAllForExport(query)) {
            if ("PAY-DETAIL".equals(item.getPaymentOrderId())
                    && (ownerMismatch || "PAYMENT_ATTEMPT".equals(item.getFlowSource())
                            || "ROUTE_RECORD".equals(item.getFlowSource()))) {
                assertNull(item.getPayScene());
            }
        }
        assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getBillNo());
        assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getBillNo());
        assertNull(mapper.findDetail("PAYMENT_ATTEMPT", "FLOW-SAME").getPayScene());
        assertNull(mapper.findDetail("ROUTE_RECORD", "FLOW-SAME").getPayScene());
        if (ownerMismatch) {
            assertNull(mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getPayScene());
            assertNull(mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getPayScene());
        } else {
            assertEquals("HOME_CLEAN", mapper.findDetail("CHANNEL_NOTIFY", "FLOW-NOTIFY").getPayScene());
            assertEquals("HOME_CLEAN", mapper.findDetail("BUSINESS_EVENT", "FLOW-EVENT").getPayScene());
        }
    }

    @Test
    void shouldProjectOwningPaymentSuccessFactIssueAcrossFlowsAndDetails() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status = 'SUCCESS', status_type = 'success', "
                    + "channel_transaction_no = 'CH-DETAIL', created_at = '2026-09-12 11:00:00.000', "
                    + "success_at = '2026-09-12 11:02:00.123' WHERE payment_order_id = 'PAY-DETAIL'");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '成功', "
                    + "attempt_status_type = 'success', completed_at = '2026-09-12 11:02:00.122' "
                    + "WHERE attempt_no = 'FLOW-SAME'");
            session.commit();
            PaymentFlowMapper mapper = session.getMapper(PaymentFlowMapper.class);
            PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
            query.setPaymentOrderId("PAY-DETAIL");
            assertEquals(5L, mapper.count(query));
            for (PaymentFlowListItemDTO item : mapper.findAllForExport(query)) {
                assertNull(item.getSuccessFactIssue());
                assertNull(mapper.findDetail(item.getFlowSource(), item.getFlowNo()).getSuccessFactIssue());
            }

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = '2026-09-12 11:02:00.124' "
                    + "WHERE attempt_no = 'FLOW-SAME'");
            session.commit();
            for (PaymentFlowListItemDTO item : mapper.findAllForExport(query)) {
                assertEquals("SUCCESS_FACT_MISMATCH", item.getSuccessFactIssue());
                assertEquals(item.getSuccessFactIssue(),
                        mapper.findDetail(item.getFlowSource(), item.getFlowNo()).getSuccessFactIssue());
            }
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-DETAIL'");
            session.commit();
            for (PaymentFlowListItemDTO item : mapper.findAllForExport(query)) {
                assertEquals("SUCCESS_TIME_MISSING", item.getSuccessFactIssue());
                assertEquals(item.getSuccessFactIssue(),
                        mapper.findDetail(item.getFlowSource(), item.getFlowNo()).getSuccessFactIssue());
            }
            PaymentFlowQueryDTO orphanQuery = new PaymentFlowQueryDTO();
            orphanQuery.setPaymentOrderId("PAY-CROSS-ATTEMPT");
            assertNull(mapper.findAll(orphanQuery).get(0).getSuccessFactIssue());
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
            statement.execute("DROP TABLE IF EXISTS t_bill");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "payment_order_id VARCHAR(64) PRIMARY KEY, order_no VARCHAR(64), "
                    + "amount DECIMAL(18,2) DEFAULT 1.00, "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(64), "
                    + "payment_method VARCHAR(32), status VARCHAR(32), status_type VARCHAR(32), "
                    + "created_at DATETIME(3), success_at DATETIME(3))");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64), customer_name VARCHAR(64), "
                    + "service_type VARCHAR(32), worker_name VARCHAR(64), order_amount DECIMAL(18,2), "
                    + "paid_amount DECIMAL(18,2), order_status VARCHAR(32), order_status_type VARCHAR(32), "
                    + "fulfillment_status VARCHAR(32), fulfillment_status_type VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_bill ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_no VARCHAR(64), order_no VARCHAR(64), "
                    + "bill_amount DECIMAL(18,2), paid_amount DECIMAL(18,2), bill_status VARCHAR(32), "
                    + "bill_status_type VARCHAR(32), due_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR_IGNORECASE(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), bill_no VARCHAR(64), order_no VARCHAR(64), "
                    + "amount DECIMAL(18,2), pay_scene VARCHAR(64), created_at DATETIME, "
                    + "source_kind VARCHAR(16) DEFAULT 'STANDARD_ORDER')");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, attempt_no VARCHAR_IGNORECASE(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), "
                    + "prepay_order_no VARCHAR_IGNORECASE(64), channel_code VARCHAR(64), terminal VARCHAR(32), "
                    + "client_ip VARCHAR(64), idempotency_key VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), request_payload VARCHAR(2048), response_payload VARCHAR(2048), "
                    + "payment_method VARCHAR(32), source_app_id VARCHAR(64), "
                    + "ops_config_snapshot_version BIGINT, completed_at DATETIME(3), created_at DATETIME(3))");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, notify_no VARCHAR_IGNORECASE(64), payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), notify_status VARCHAR(32), notify_status_type VARCHAR(32), "
                    + "notify_type VARCHAR(32), notify_payload VARCHAR(2048), notify_result VARCHAR(2048), "
                    + "created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_route_record ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, route_no VARCHAR_IGNORECASE(64), attempt_no VARCHAR(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), "
                    + "channel_code VARCHAR(64), route_result VARCHAR(128), route_rule VARCHAR(128), "
                    + "ops_config_snapshot_version BIGINT, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR_IGNORECASE(64), payment_order_id VARCHAR(64), "
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
            statement.executeUpdate("INSERT INTO t_order "
                    + "(order_no, customer_name, service_type, worker_name, order_amount, paid_amount, "
                    + "order_status, order_status_type, fulfillment_status, fulfillment_status_type, created_at) "
                    + "VALUES ('ORD-DETAIL', '客户', '保洁', '阿姨', 100, 0, '待支付', 'warning', "
                    + "'待履约', 'info', '2026-09-12 11:00:00')");
            statement.executeUpdate("INSERT INTO t_bill "
                    + "(bill_no, order_no, bill_amount, paid_amount, bill_status, bill_status_type, due_at, created_at) "
                    + "VALUES ('BILL-DETAIL', 'ORD-DETAIL', 100, 0, '待支付', 'warning', "
                    + "'2026-09-13 11:00:00', '2026-09-12 11:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_order "
                    + "(payment_order_id, order_no, channel_code, payment_method) "
                    + "VALUES ('PAY-DETAIL', 'ORD-DETAIL', 'wx_h5', '微信')");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, bill_no, order_no, pay_scene, created_at) "
                    + "VALUES ('PRE-DETAIL', 'PAY-DETAIL', 'BILL-DETAIL', 'ORD-DETAIL', 'HOME_CLEAN', "
                    + "'2026-09-12 11:00:00')");
            statement.executeUpdate("UPDATE t_prepay_order SET amount = "
                    + "(SELECT amount FROM t_payment_order WHERE payment_order_id = t_prepay_order.payment_order_id)");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, prepay_order_no, channel_code, terminal, client_ip, "
                    + "idempotency_key, attempt_status, attempt_status_type, request_payload, response_payload, "
                    + "payment_method, source_app_id, created_at) VALUES "
                    + "('FLOW-SAME', 'PAY-DETAIL', 'PRE-DETAIL', 'wx_h5', 'H5', '127.0.0.1', "
                    + "'KEY-DETAIL', '处理中', 'warning', '{}', '{}', '微信', 'h5-app', '2026-09-12 11:01:00')");
            statement.executeUpdate("INSERT INTO t_payment_route_record "
                    + "(route_no, attempt_no, payment_order_id, channel_code, route_result, route_rule, created_at) "
                    + "VALUES ('FLOW-SAME', 'FLOW-SAME', 'PAY-DETAIL', 'wx_h5', '命中', '默认', "
                    + "'2026-09-12 11:01:01'), ('FLOW-ROUTE-ORPHAN', NULL, 'PAY-DETAIL', 'wx_h5', "
                    + "'命中', '存量', '2026-09-12 11:01:02')");
            statement.executeUpdate("INSERT INTO t_payment_notify_log "
                    + "(notify_no, payment_order_id, channel_code, notify_status, notify_status_type, notify_type, "
                    + "notify_payload, notify_result, created_at) VALUES "
                    + "('FLOW-NOTIFY', 'PAY-DETAIL', 'wx_h5', '已处理', 'success', '支付回调', '{}', '{}', "
                    + "'2026-09-12 11:02:00')");
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, payment_order_id, biz_no, event_type, downstream_system, event_topic, "
                    + "publish_status, retry_count, event_payload, created_at) VALUES "
                    + "('FLOW-EVENT', 'PAY-DETAIL', 'ORD-DETAIL', 'PAYMENT_SUCCESS', "
                    + "'trade-center', 'payment.events', 'SUCCESS', 0, '{}', '2026-09-12 11:03:00')");

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
