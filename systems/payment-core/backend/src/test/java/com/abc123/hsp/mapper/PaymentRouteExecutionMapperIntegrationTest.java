package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentRouteExecutionDetailDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionOverviewDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
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
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentRouteExecutionMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentRouteExecutionMapper.xml",
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

    @Test
    void shouldFindRouteDetailByRouteNoWithoutGuessingMissingAttempt() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRouteExecutionMapper mapper = session.getMapper(PaymentRouteExecutionMapper.class);

            PaymentRouteExecutionDetailDTO detail = mapper.findDetail("ROUTE-API");
            PaymentRouteExecutionDetailDTO legacy = mapper.findDetail("ROUTE-LEGACY");

            assertEquals("ROUTE-API", detail.getRouteNo());
            assertEquals("ATT-API", detail.getRequestNo());
            assertEquals("PAY-SAME-SECOND", detail.getPaymentOrderId());
            assertEquals("ORD-SAME-SECOND", detail.getOrderNo());
            assertEquals("PRE-SAME-SECOND", detail.getPrepayOrderNo());
            assertEquals("支付宝", detail.getPaymentMethod());
            assertEquals("API", detail.getTerminal());
            assertEquals("alipay_h5", detail.getChannelCode());
            assertEquals("terminal=API", detail.getRouteRule());
            assertEquals("支付宝H5", detail.getRouteResult());
            assertEquals("2026-09-14 10:00:01", detail.getCreatedAt());

            assertEquals("ROUTE-LEGACY", legacy.getRouteNo());
            assertNull(legacy.getRequestNo());
            assertNull(legacy.getPaymentMethod());
            assertNull(legacy.getTerminal());
            assertNull(mapper.findDetail("ROUTE-MISSING"));
        }
    }

    @Test
    void shouldNotReadRouteDetailThroughNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertNull(session.getMapper(PaymentRouteExecutionMapper.class).findDetail("route-api"));
        }
    }

    @Test
    void shouldNotJoinRouteToAttemptThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET attempt_no = 'att-api' "
                    + "WHERE route_no = 'ROUTE-API'");
            assertNoApiAttempt(session.getMapper(PaymentRouteExecutionMapper.class));
        }
    }

    @Test
    void shouldNotJoinRouteToAttemptFromAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET payment_order_id = 'PAY-OTHER' "
                    + "WHERE route_no = 'ROUTE-API'");
            assertNoApiAttempt(session.getMapper(PaymentRouteExecutionMapper.class));
        }
    }

    @Test
    void shouldNotJoinRouteToAttemptThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record SET payment_order_id = 'pay-same-second' "
                    + "WHERE route_no = 'ROUTE-API'");
            assertNoApiAttempt(session.getMapper(PaymentRouteExecutionMapper.class));
        }
    }

    @Test
    void shouldNotJoinPrepayFromAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'PAY-OTHER' "
                    + "WHERE prepay_order_no = 'PRE-SAME-SECOND'");
            assertNoApiPrepay(session.getMapper(PaymentRouteExecutionMapper.class));
        }
    }

    @Test
    void shouldNotJoinPrepayThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET prepay_order_no = 'pre-same-second' "
                    + "WHERE attempt_no = 'ATT-API'");
            assertNull(session.getMapper(PaymentRouteExecutionMapper.class)
                    .findDetail("ROUTE-API").getPrepayOrderNo());
        }
    }

    @Test
    void shouldNotJoinPrepayThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'pay-same-second' "
                    + "WHERE prepay_order_no = 'PRE-SAME-SECOND'");
            assertNoApiPrepay(session.getMapper(PaymentRouteExecutionMapper.class));
        }
    }

    @Test
    void shouldNotJoinPaymentOrderThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            assertEquals(1, statement.executeUpdate("UPDATE t_payment_order SET payment_order_id = 'pay-same-second' "
                    + "WHERE payment_order_id = 'PAY-SAME-SECOND'"));
            try (ResultSet resultSet = statement.executeQuery("SELECT payment_order_id FROM t_payment_order "
                    + "WHERE payment_order_id = 'PAY-SAME-SECOND'")) {
                resultSet.next();
                assertEquals("pay-same-second", resultSet.getString(1));
            }
            PaymentRouteExecutionMapper mapper = session.getMapper(PaymentRouteExecutionMapper.class);
            assertNull(mapper.findDetail("ROUTE-API").getOrderNo());
            assertNull(findRoute(mapper.findAll(new PaymentRouteExecutionQueryDTO()), "ROUTE-API").getOrderNo());
            assertNull(findRoute(mapper.findAllForExport(new PaymentRouteExecutionQueryDTO()),
                    "ROUTE-API").getOrderNo());
            PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
            query.setOrderNo("ORD-SAME-SECOND");
            assertEquals(0L, mapper.count(query));
            assertEquals(0L, mapper.findOverview(query).getTotalRouteCount());
        }
    }

    @Test
    void shouldNotGuessLegacyAttemptThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_route_record "
                    + "SET payment_order_id = 'pay-same-second', created_at = '2026-09-14 10:00:02' "
                    + "WHERE route_no = 'ROUTE-LEGACY'");
            PaymentRouteExecutionMapper mapper = session.getMapper(PaymentRouteExecutionMapper.class);
            PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
            query.setRouteRule("legacy");
            assertNull(mapper.findAll(query).get(0).getPaymentMethod());
            assertNull(mapper.findAllForExport(query).get(0).getPrepayOrderNo());
        }
    }

    @Test
    void shouldOnlyWriteOpsSnapshotToExactRouteNumber() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.updateRouteOpsConfigSnapshotVersion("route-api", 7L));
            assertEquals(0, mapper.updateRouteOpsConfigSnapshotVersion("ROUTE-API ", 7L));
            try (ResultSet resultSet = statement.executeQuery("SELECT ops_config_snapshot_version "
                    + "FROM t_payment_route_record WHERE route_no = 'ROUTE-API'")) {
                resultSet.next();
                assertNull(resultSet.getObject(1));
            }
            assertEquals(1, mapper.updateRouteOpsConfigSnapshotVersion("ROUTE-API", 7L));
            try (ResultSet resultSet = statement.executeQuery("SELECT ops_config_snapshot_version "
                    + "FROM t_payment_route_record WHERE route_no = 'ROUTE-API'")) {
                resultSet.next();
                assertEquals(7L, resultSet.getLong(1));
            }
        }
    }

    private void assertNoApiAttempt(PaymentRouteExecutionMapper mapper) {
        PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
        assertNull(findRoute(mapper.findAll(query), "ROUTE-API").getPaymentMethod());
        assertNull(findRoute(mapper.findAllForExport(query), "ROUTE-API").getTerminal());
        assertNull(mapper.findDetail("ROUTE-API").getPaymentMethod());
        query.setTerminal("API");
        assertEquals(0L, mapper.count(query));
    }

    private void assertNoApiPrepay(PaymentRouteExecutionMapper mapper) {
        PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
        assertNull(findRoute(mapper.findAll(query), "ROUTE-API").getPrepayOrderNo());
        assertNull(findRoute(mapper.findAllForExport(query), "ROUTE-API").getPrepayOrderNo());
        assertNull(mapper.findDetail("ROUTE-API").getPrepayOrderNo());
    }

    private PaymentRouteExecutionListItemDTO findRoute(List<PaymentRouteExecutionListItemDTO> routes,
                                                        String routeNo) {
        return routes.stream().filter(item -> routeNo.equals(item.getRouteNo()))
                .findFirst().orElseThrow(AssertionError::new);
    }

    @Test
    void shouldNotOffsetDailyAcceptedChannelAmountWithHistoricalNegativeSuccess() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order ("
                    + "payment_order_id, amount, channel_code, status, created_at) VALUES "
                    + "('PAY-LIMIT-SUCCESS', 80.00, 'wx_h5', 'SUCCESS', NOW()), "
                    + "('PAY-LIMIT-NEGATIVE', -20.00, 'wx_h5', 'SUCCESS', NOW()), "
                    + "('PAY-LIMIT-PENDING', 5.00, 'wx_h5', 'WAIT_CALLBACK', NOW()), "
                    + "('PAY-LIMIT-PENDING-NEGATIVE', -10.00, 'wx_h5', 'WAIT_CALLBACK', NOW()), "
                    + "('PAY-LIMIT-CLOSED', 100.00, 'wx_h5', 'CLOSED', NOW()), "
                    + "('PAY-LIMIT-OTHER', 200.00, 'alipay_h5', 'SUCCESS', NOW())");

            BigDecimal acceptedAmount = session.getMapper(PaymentMapper.class)
                    .sumTodayAcceptedAmountByChannel("wx_h5");
            assertEquals(0, acceptedAmount.compareTo(new BigDecimal("85.00")));
        }
    }

    @Test
    void shouldProjectOwningPaymentSuccessFactIssueAcrossRoutesAndDetail() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRouteExecutionMapper mapper = session.getMapper(PaymentRouteExecutionMapper.class);
            statement.executeUpdate("INSERT INTO t_payment_route_record "
                    + "(route_no, payment_order_id, channel_code, route_rule, route_result, created_at) "
                    + "VALUES ('ROUTE-ORPHAN', 'PAY-ORPHAN', 'wx_h5', 'legacy', '孤立记录', "
                    + "'2026-09-14 10:00:03')");
            session.commit();
            PaymentRouteExecutionQueryDTO orphanQuery = new PaymentRouteExecutionQueryDTO();
            orphanQuery.setPaymentOrderId("PAY-ORPHAN");
            assertNull(mapper.findAll(orphanQuery).get(0).getSuccessFactIssue());
            assertNull(mapper.findDetail("ROUTE-ORPHAN").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_order SET status = 'SUCCESS', status_type = 'success', "
                    + "channel_code = 'wx_h5', payment_method = '微信', channel_transaction_no = 'CH-ROUTE', "
                    + "created_at = '2026-09-14 09:59:58.000', success_at = '2026-09-14 10:00:02.123' "
                    + "WHERE payment_order_id = 'PAY-SAME-SECOND'");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '成功', "
                    + "attempt_status_type = 'success', channel_code = 'wx_h5', "
                    + "completed_at = '2026-09-14 10:00:02.122' "
                    + "WHERE attempt_no = 'ATT-MINI'");
            session.commit();
            PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
            query.setPaymentOrderId("PAY-SAME-SECOND");
            assertEquals(3L, mapper.count(query));
            assertRouteIssues(mapper, query, null);

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = '2026-09-14 10:00:02.124' "
                    + "WHERE attempt_no = 'ATT-MINI'");
            session.commit();
            assertRouteIssues(mapper, query, "SUCCESS_FACT_MISMATCH");

            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-SAME-SECOND'");
            session.commit();
            assertRouteIssues(mapper, query, "SUCCESS_TIME_MISSING");
        }
    }

    private void assertRouteIssues(PaymentRouteExecutionMapper mapper, PaymentRouteExecutionQueryDTO query,
                                   String expectedIssue) {
        for (PaymentRouteExecutionListItemDTO item : mapper.findAll(query)) {
            assertEquals(expectedIssue, item.getSuccessFactIssue());
            assertEquals(expectedIssue, mapper.findDetail(item.getRouteNo()).getSuccessFactIssue());
        }
        for (PaymentRouteExecutionListItemDTO item : mapper.findAllForExport(query)) {
            assertEquals(expectedIssue, item.getSuccessFactIssue());
        }
    }

    @Test
    void shouldReturnZeroOverviewCountsWhenRouteTableIsEmpty() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_payment_route_record");
            PaymentRouteExecutionMapper mapper = session.getMapper(PaymentRouteExecutionMapper.class);

            PaymentRouteExecutionOverviewDTO overview = mapper.findOverview(new PaymentRouteExecutionQueryDTO());

            assertEquals(0L, overview.getTotalRouteCount());
            assertEquals(0L, overview.getSuccessRouteCount());
            assertEquals(0L, overview.getWarnRouteCount());
            assertEquals(0, overview.getDistinctChannelCount());
            assertEquals(0, overview.getOfflineRouteCount());
            assertEquals(0, overview.getWechatRouteCount());
            assertEquals(0, overview.getAlipayRouteCount());
            assertNull(overview.getLatestRouteAt());
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
            statement.execute("CREATE TABLE t_payment_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR(64), customer_name VARCHAR(128), amount DECIMAL(18, 2), "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(64), "
                    + "payment_method VARCHAR(32), "
                    + "status VARCHAR(32), status_type VARCHAR(32), "
                    + "created_at DATETIME(3), success_at DATETIME(3))");
            statement.execute("CREATE TABLE t_prepay_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "prepay_order_no VARCHAR_IGNORECASE(64), payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR(64), amount DECIMAL(18,2), created_at DATETIME, "
                    + "source_kind VARCHAR(16) DEFAULT 'VIRTUAL')");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR(64))");
            statement.execute("CREATE TABLE t_payment_attempt (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "attempt_no VARCHAR_IGNORECASE(64), prepay_order_no VARCHAR_IGNORECASE(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), "
                    + "channel_code VARCHAR(64), payment_method VARCHAR(32), terminal VARCHAR(32), "
                    + "client_ip VARCHAR(64), idempotency_key VARCHAR(128), "
                    + "request_payload VARCHAR(2048), response_payload VARCHAR(2048), "
                    + "attempt_status VARCHAR(32), attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME(3), created_at DATETIME(3))");
            statement.execute("CREATE TABLE t_payment_route_record (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "route_no VARCHAR_IGNORECASE(64), attempt_no VARCHAR_IGNORECASE(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), "
                    + "channel_code VARCHAR(64), route_rule VARCHAR(128), route_result VARCHAR(128), "
                    + "ops_config_snapshot_version BIGINT, created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, amount) VALUES "
                    + "('PAY-SAME-SECOND', 'ORD-SAME-SECOND', 'API客户', 88.00)");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, created_at) VALUES "
                    + "('PRE-SAME-SECOND', 'PAY-SAME-SECOND', '2026-09-14 10:00:00')");
            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-SAME-SECOND', amount = 88.00 "
                    + "WHERE payment_order_id = 'PAY-SAME-SECOND'");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, prepay_order_no, payment_order_id, payment_method, terminal, client_ip, idempotency_key, "
                    + "request_payload, created_at) VALUES "
                    + "('ATT-API', 'PRE-SAME-SECOND', 'PAY-SAME-SECOND', '支付宝', 'API', '127.0.0.1', 'KEY-API', '{}', "
                    + "'2026-09-14 10:00:01'), "
                    + "('ATT-MINI', 'PRE-SAME-SECOND', 'PAY-SAME-SECOND', '微信', 'MINI_PROGRAM', '127.0.0.2', 'KEY-MINI', '{}', "
                    + "'2026-09-14 10:00:01')");
            statement.executeUpdate("INSERT INTO t_payment_route_record "
                    + "(route_no, attempt_no, payment_order_id, channel_code, route_rule, route_result, created_at) VALUES "
                    + "('ROUTE-API', 'ATT-API', 'PAY-SAME-SECOND', 'alipay_h5', 'terminal=API', '支付宝H5', "
                    + "'2026-09-14 10:00:01'), "
                    + "('ROUTE-MINI', 'ATT-MINI', 'PAY-SAME-SECOND', 'wx_h5', 'terminal=MINI_PROGRAM', '微信H5', "
                    + "'2026-09-14 10:00:01'), "
                    + "('ROUTE-LEGACY', NULL, 'PAY-SAME-SECOND', 'offline_bank', 'legacy', '历史空尝试号', "
                    + "'2026-09-14 09:59:59')");
            session.commit();
        }
    }
}
