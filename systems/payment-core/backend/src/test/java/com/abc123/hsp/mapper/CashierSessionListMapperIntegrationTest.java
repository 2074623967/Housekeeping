package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.CashierSessionDetailDTO;
import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.dto.CashierSessionOverviewDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
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
 * 收银台会话列表与导出的 MySQL 兼容 SQL 集成测试。
 */
class CashierSessionListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:cashier_session_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CashierSessionMapper.class);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/CashierSessionMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/CashierSessionMapper.xml",
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
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);
            CashierSessionQueryDTO query = new CashierSessionQueryDTO();
            query.setSessionNo("PRE-STABLE");
            query.setPageSize(1);

            assertEquals("PRE-STABLE-003", mapper.findAll(query).get(0).getSessionNo());
            query.setPageNo(2);
            assertEquals("PRE-STABLE-002", mapper.findAll(query).get(0).getSessionNo());
            query.setPageNo(3);
            assertEquals("PRE-STABLE-001", mapper.findAll(query).get(0).getSessionNo());

            query.setSortField("expiresAt");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList("PRE-STABLE-001", "PRE-STABLE-002", "PRE-STABLE-003"),
                    sessionNos(mapper.findAllForExport(query)));
            query.setSortField("amount");
            query.setSortOrder("desc");
            assertEquals(Arrays.asList("PRE-STABLE-003", "PRE-STABLE-002", "PRE-STABLE-001"),
                    sessionNos(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldTreatCashierSessionSearchFieldsAsLiteralSubstrings() throws Exception {
        String literal = "%_!\\";
        String sessionNo = "PRE-LITERAL-" + literal;
        try (SqlSession session = sqlSessionFactory.openSession();
             PreparedStatement updatePayment = session.getConnection().prepareStatement(
                     "UPDATE t_payment_order SET payment_order_id = ? WHERE payment_order_id = 'PAY-003'");
             PreparedStatement updateSession = session.getConnection().prepareStatement(
                     "UPDATE t_prepay_order SET prepay_order_no = ?, payment_order_id = ?, "
                             + "order_no = ?, customer_name = ? WHERE prepay_order_no = 'PRE-STABLE-003'")) {
            updatePayment.setString(1, "PAY-LITERAL-" + literal);
            updatePayment.executeUpdate();
            updateSession.setString(1, sessionNo);
            updateSession.setString(2, "PAY-LITERAL-" + literal);
            updateSession.setString(3, "ORD-LITERAL-" + literal);
            updateSession.setString(4, "客户%_!\\");
            updateSession.executeUpdate();
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);
            for (String fragment : Arrays.asList("%", "_", "!", "\\", literal)) {
                CashierSessionQueryDTO query = new CashierSessionQueryDTO();
                query.setSessionNo(fragment);
                assertOnlyLiteralSession(mapper, query, sessionNo);
                assertEquals(fragment, query.getSessionNo());

                query = new CashierSessionQueryDTO();
                query.setPaymentOrderId(fragment);
                assertOnlyLiteralSession(mapper, query, sessionNo);

                query = new CashierSessionQueryDTO();
                query.setOrderNo(fragment);
                assertOnlyLiteralSession(mapper, query, sessionNo);

                query = new CashierSessionQueryDTO();
                query.setCustomerName(fragment);
                assertOnlyLiteralSession(mapper, query, sessionNo);
            }
        }
    }

    private void assertOnlyLiteralSession(CashierSessionMapper mapper, CashierSessionQueryDTO query,
                                          String sessionNo) {
        assertEquals(1L, mapper.count(query));
        assertEquals(Arrays.asList(sessionNo), sessionNos(mapper.findAll(query)));
        assertEquals(Arrays.asList(sessionNo), sessionNos(mapper.findAllForExport(query)));
        assertEquals(1L, mapper.findOverview(query).getTotalSessionCount());
    }

    @Test
    void shouldSeparatePaySceneFromLatestAttemptTerminalAndFilterByTerminal() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);
            CashierSessionQueryDTO query = new CashierSessionQueryDTO();
            query.setTerminal("API");

            List<CashierSessionListItemDTO> sessions = mapper.findAll(query);
            assertEquals(1, sessions.size());
            assertEquals("PRE-STABLE-001", sessions.get(0).getSessionNo());
            assertEquals("H5", sessions.get(0).getPayScene());
            assertEquals("API", sessions.get(0).getTerminal());
            assertEquals(1L, mapper.count(query));

            CashierSessionOverviewDTO overview = mapper.findOverview(new CashierSessionQueryDTO());
            assertEquals(3, overview.getDistinctTerminalCount());

            CashierSessionQueryDTO fallbackQuery = new CashierSessionQueryDTO();
            fallbackQuery.setSessionNo("PRE-STABLE-003");
            CashierSessionListItemDTO fallback = mapper.findAllForExport(fallbackQuery).get(0);
            assertEquals("H5", fallback.getPayScene());
            assertEquals("H5", fallback.getTerminal());
        }
    }

    @Test
    void shouldReturnZeroOverviewCountsWhenPrepayTableIsEmpty() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_prepay_order");
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);

            CashierSessionOverviewDTO overview = mapper.findOverview(new CashierSessionQueryDTO());

            assertEquals(0L, overview.getTotalSessionCount());
            assertEquals(0L, overview.getExpiredSessionCount());
            assertEquals(0L, overview.getSuccessSessionCount());
            assertEquals(0L, overview.getPayingSessionCount());
            assertEquals(0L, overview.getPendingSessionCount());
            assertEquals(0L, overview.getOtherSessionCount());
            assertEquals(0, overview.getDistinctTerminalCount());
            assertEquals(0, overview.getExpiringSoonCount());
            assertEquals("¥0.00", overview.getTotalAmount());
        }
    }

    @Test
    void shouldFindExactCashierSessionDetailWithStableAttemptAndStandardOrderIdentity() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);

            CashierSessionDetailDTO standard = mapper.findDetail("PRE-STABLE-001");
            assertEquals("PRE-STABLE-001", standard.getSessionNo());
            assertEquals("ORD-001", standard.getStandardOrderNo());
            assertEquals("H5", standard.getPayScene());
            assertEquals("API", standard.getTerminal());

            CashierSessionDetailDTO virtual = mapper.findDetail("PRE-STABLE-003");
            assertEquals("ORD-003", virtual.getOrderNo());
            assertNull(virtual.getStandardOrderNo());
            assertEquals("H5", virtual.getTerminal());
            assertNull(mapper.findDetail("PRE-MISSING"));
        }
    }

    @Test
    void shouldNotReadCashierSessionThroughCaseVariantIdentifier() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);
            assertNull(mapper.findDetail("pre-stable-001"));
            assertEquals("PRE-STABLE-001", mapper.findDetail("PRE-STABLE-001").getSessionNo());
        }
    }

    @Test
    void shouldNotBorrowPaymentOrAttemptFromCaseVariantAssociation() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id='pay-001' "
                    + "WHERE prepay_order_no='PRE-STABLE-001'");
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);

            CashierSessionDetailDTO detail = mapper.findDetail("PRE-STABLE-001");
            assertEquals("H5", detail.getTerminal());
            assertEquals("已失效", detail.getSessionStatus());
            CashierSessionQueryDTO query = new CashierSessionQueryDTO();
            query.setSessionNo("PRE-STABLE-001");
            assertEquals("已失效", mapper.findAll(query).get(0).getSessionStatus());
            assertEquals("H5", mapper.findAllForExport(query).get(0).getTerminal());
            assertEquals(1L, mapper.count(query));
            assertEquals(1L, mapper.findOverview(query).getExpiredSessionCount());
            assertEquals(0L, mapper.findOverview(query).getPayingSessionCount());

            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id='PAY-001' "
                    + "WHERE prepay_order_no='PRE-STABLE-001'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_order_id='pay-001' "
                    + "WHERE payment_order_id='PAY-001'");
            session.clearCache();
            assertEquals("H5", mapper.findDetail("PRE-STABLE-001").getTerminal());
            assertEquals("支付中", mapper.findDetail("PRE-STABLE-001").getSessionStatus());
            assertEquals("H5", mapper.findAllForExport(query).get(0).getTerminal());
            assertEquals(1L, mapper.findOverview(query).getPayingSessionCount());
        }
    }

    @Test
    void shouldNotLinkVirtualSessionToCollidingStandardOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_order (order_no) VALUES ('ORD-003')");
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);
            assertEquals("ORD-003", mapper.findDetail("PRE-STABLE-003").getOrderNo());
            assertNull(mapper.findDetail("PRE-STABLE-003").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'standard_order' "
                    + "WHERE prepay_order_no = 'PRE-STABLE-003'");
            session.clearCache();
            assertNull(mapper.findDetail("PRE-STABLE-003").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'STANDARD_ORDER' "
                    + "WHERE prepay_order_no = 'PRE-STABLE-003'");
            session.clearCache();
            assertEquals("ORD-003", mapper.findDetail("PRE-STABLE-003").getStandardOrderNo());
        }
    }

    @Test
    void shouldReflectSubmittedPaymentAcrossViewsAndOverview() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '支付中', "
                    + "cashier_status_type = 'info', expires_at = TIMESTAMPADD(MINUTE, -1, NOW()) "
                    + "WHERE prepay_order_no = 'PRE-STABLE-001'");
            statement.executeUpdate("UPDATE t_prepay_order "
                    + "SET expires_at = TIMESTAMPADD(MINUTE, 5, NOW()) "
                    + "WHERE prepay_order_no = 'PRE-STABLE-002'");
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);

            assertEquals("支付中", mapper.findDetail("PRE-STABLE-001").getSessionStatus());
            assertEquals("info", mapper.findDetail("PRE-STABLE-001").getSessionStatusType());
            CashierSessionQueryDTO query = new CashierSessionQueryDTO();
            query.setSessionStatus("支付中");
            assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAll(query)));
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAllForExport(query)));
            CashierSessionOverviewDTO overview = mapper.findOverview(new CashierSessionQueryDTO());
            assertEquals(1L, overview.getExpiredSessionCount());
            assertEquals(1L, overview.getPayingSessionCount());
            assertEquals(1L, overview.getPendingSessionCount());
            assertEquals(1, overview.getExpiringSoonCount());

            statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '待支付', "
                    + "cashier_status_type = 'warning', expires_at = TIMESTAMPADD(MINUTE, 5, NOW()) "
                    + "WHERE prepay_order_no = 'PRE-STABLE-001'");
            session.clearCache();
            assertEquals("支付中", mapper.findDetail("PRE-STABLE-001").getSessionStatus());
            assertEquals("info", mapper.findDetail("PRE-STABLE-001").getSessionStatusType());
            assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAll(query)));
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAllForExport(query)));
            CashierSessionOverviewDTO staleOverview = mapper.findOverview(new CashierSessionQueryDTO());
            assertEquals(1L, staleOverview.getPayingSessionCount());
            assertEquals(1L, staleOverview.getPendingSessionCount());
            assertEquals(1, staleOverview.getExpiringSoonCount());
        }
    }

    @Test
    void shouldNotReportSuccessWhenPaymentIsStillPendingOrUnsubmitted() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '支付成功', "
                    + "cashier_status_type = 'success', expires_at = TIMESTAMPADD(MINUTE, -1, NOW()) "
                    + "WHERE prepay_order_no = 'PRE-STABLE-001'");
            statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '已完成', "
                    + "cashier_status_type = 'success', expires_at = TIMESTAMPADD(MINUTE, 5, NOW()) "
                    + "WHERE prepay_order_no = 'PRE-STABLE-002'");
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);

            assertEquals("支付中", mapper.findDetail("PRE-STABLE-001").getSessionStatus());
            assertEquals("info", mapper.findDetail("PRE-STABLE-001").getSessionStatusType());
            assertEquals("待支付", mapper.findDetail("PRE-STABLE-002").getSessionStatus());
            assertEquals("warn", mapper.findDetail("PRE-STABLE-002").getSessionStatusType());
            CashierSessionQueryDTO paying = new CashierSessionQueryDTO();
            paying.setSessionStatus("支付中");
            assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAll(paying)));
            assertEquals(1L, mapper.count(paying));
            assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAllForExport(paying)));
            CashierSessionQueryDTO success = new CashierSessionQueryDTO();
            success.setSessionStatus("支付成功");
            assertEquals(0L, mapper.count(success));
            CashierSessionOverviewDTO overview = mapper.findOverview(new CashierSessionQueryDTO());
            assertEquals(0L, overview.getSuccessSessionCount());
            assertEquals(1L, overview.getPayingSessionCount());
            assertEquals(1L, overview.getPendingSessionCount());
            assertEquals(1, overview.getExpiringSoonCount());

            statement.executeUpdate("UPDATE t_prepay_order SET expires_at = TIMESTAMPADD(MINUTE, -1, NOW()) "
                    + "WHERE prepay_order_no = 'PRE-STABLE-002'");
            session.clearCache();
            assertEquals("已失效", mapper.findDetail("PRE-STABLE-002").getSessionStatus());
            assertEquals("danger", mapper.findDetail("PRE-STABLE-002").getSessionStatusType());
            CashierSessionQueryDTO expired = new CashierSessionQueryDTO();
            expired.setSessionNo("PRE-STABLE-002");
            expired.setSessionStatus("已失效");
            assertEquals(Arrays.asList("PRE-STABLE-002"), sessionNos(mapper.findAll(expired)));
            assertEquals(1L, mapper.count(expired));
            assertEquals(Arrays.asList("PRE-STABLE-002"), sessionNos(mapper.findAllForExport(expired)));
            assertEquals(0L, mapper.findOverview(new CashierSessionQueryDTO()).getPendingSessionCount());

            statement.executeUpdate("UPDATE t_payment_order SET status = 'SUCCESS' "
                    + "WHERE payment_order_id = 'PAY-002'");
            session.clearCache();
            assertEquals("已完成", mapper.findDetail("PRE-STABLE-002").getSessionStatus());
            assertEquals("success", mapper.findDetail("PRE-STABLE-002").getSessionStatusType());
            assertEquals(1L, mapper.findOverview(new CashierSessionQueryDTO()).getSuccessSessionCount());
        }
    }

    @Test
    void shouldProjectRiskAndTerminalPaymentStatusesAcrossSessionViews() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '支付成功', "
                    + "cashier_status_type = 'success', expires_at = TIMESTAMPADD(MINUTE, -1, NOW()) "
                    + "WHERE prepay_order_no = 'PRE-STABLE-001'");
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);
            String[][] states = {
                    {"RISK_REVIEW", "待风控复核", "warn"},
                    {"RISK_BLOCKED", "风控拦截", "danger"},
                    {"RISK_REJECTED", "风控拒绝", "danger"},
                    {"PAYING", "支付中", "info"},
                    {"FAIL", "结果待确认", "danger"},
                    {"CLOSED", "已关闭", "danger"}
            };
            for (String[] state : states) {
                statement.executeUpdate("UPDATE t_payment_order SET status = '" + state[0]
                        + "' WHERE payment_order_id = 'PAY-001'");
                session.clearCache();
                assertEquals(state[1], mapper.findDetail("PRE-STABLE-001").getSessionStatus());
                assertEquals(state[2], mapper.findDetail("PRE-STABLE-001").getSessionStatusType());
                CashierSessionQueryDTO query = new CashierSessionQueryDTO();
                query.setSessionNo("PRE-STABLE-001");
                query.setSessionStatus(state[1]);
                assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAll(query)));
                assertEquals(1L, mapper.count(query));
                assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAllForExport(query)));
                assertEquals(0L, mapper.findOverview(query).getSuccessSessionCount());
                assertEquals("PAYING".equals(state[0]) ? 0L : 1L,
                        mapper.findOverview(query).getOtherSessionCount());
            }

            statement.executeUpdate("UPDATE t_payment_order SET status = 'PREPAY_CREATED' "
                    + "WHERE payment_order_id = 'PAY-001'");
            statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '支付中', "
                    + "cashier_status_type = 'info', expires_at = TIMESTAMPADD(MINUTE, 5, NOW()) "
                    + "WHERE prepay_order_no = 'PRE-STABLE-001'");
            session.clearCache();
            assertEquals("待支付", mapper.findDetail("PRE-STABLE-001").getSessionStatus());
            assertEquals("warn", mapper.findDetail("PRE-STABLE-001").getSessionStatusType());
        }
    }

    @Test
    void shouldOnlyCountTrustedSuccessfulPaymentsAsSuccessfulSessions() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status = 'SUCCESS', status_type = 'success', "
                    + "order_no = 'ORD-001', amount = 100.00, "
                    + "created_at = '2026-09-12 11:59:00', success_at = '2026-09-12 12:01:00' "
                    + "WHERE payment_order_id = 'PAY-001'");
            statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '支付成功', "
                    + "cashier_status_type = 'success' WHERE prepay_order_no = 'PRE-STABLE-001'");
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);
            CashierSessionQueryDTO anomalous = new CashierSessionQueryDTO();
            anomalous.setSessionNo("PRE-STABLE-001");
            anomalous.setSessionStatus("成功事实异常");
            assertEquals("成功事实异常", mapper.findDetail("PRE-STABLE-001").getSessionStatus());
            assertEquals("danger", mapper.findDetail("PRE-STABLE-001").getSessionStatusType());
            assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAll(anomalous)));
            assertEquals(1L, mapper.count(anomalous));
            assertEquals(Arrays.asList("PRE-STABLE-001"), sessionNos(mapper.findAllForExport(anomalous)));
            assertEquals(0L, mapper.findOverview(anomalous).getSuccessSessionCount());
            assertEquals(1L, mapper.findOverview(anomalous).getOtherSessionCount());

            statement.executeUpdate("UPDATE t_payment_order SET status = 'SUCCESS' "
                    + "WHERE payment_order_id = 'PAY-002'");
            statement.executeUpdate("UPDATE t_prepay_order SET cashier_status = '待支付', "
                    + "cashier_status_type = 'warn' WHERE prepay_order_no = 'PRE-STABLE-002'");
            session.clearCache();
            CashierSessionQueryDTO trusted = new CashierSessionQueryDTO();
            trusted.setSessionNo("PRE-STABLE-002");
            assertEquals("支付成功", mapper.findDetail("PRE-STABLE-002").getSessionStatus());
            assertEquals("success", mapper.findDetail("PRE-STABLE-002").getSessionStatusType());
            assertEquals(1L, mapper.findOverview(trusted).getSuccessSessionCount());
            assertEquals(0L, mapper.findOverview(trusted).getOtherSessionCount());

            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, order_no, bill_no, customer_name, cashier_title, "
                    + "pay_scene, amount, cashier_status, cashier_status_type, created_at, expires_at) VALUES "
                    + "('PRE-DUPLICATE', 'PAY-002', 'ORD-002', 'BILL-002', '客户二', '收银台二', "
                    + "'H5', 100.00, '待支付', 'warn', '2026-09-12 12:00:00', '2026-09-15 12:00:00')");
            session.clearCache();
            assertEquals("成功事实异常", mapper.findDetail("PRE-STABLE-002").getSessionStatus());
            statement.executeUpdate("DELETE FROM t_prepay_order WHERE prepay_order_no = 'PRE-DUPLICATE'");
            session.clearCache();
            assertEquals("支付成功", mapper.findDetail("PRE-STABLE-002").getSessionStatus());

            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-002'");
            session.clearCache();
            assertEquals("成功事实异常", mapper.findDetail("PRE-STABLE-002").getSessionStatus());
            statement.executeUpdate("UPDATE t_payment_order SET success_at = '2026-09-12 12:01:00' "
                    + "WHERE payment_order_id = 'PAY-002'");
            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = '2026-09-12 11:58:00' "
                    + "WHERE attempt_no = 'ATT-002'");
            session.clearCache();
            assertEquals("成功事实异常", mapper.findDetail("PRE-STABLE-002").getSessionStatus());
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
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "payment_order_id VARCHAR_IGNORECASE(64), order_no VARCHAR(64) NOT NULL, bill_no VARCHAR(64) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, cashier_title VARCHAR(128) NOT NULL, "
                    + "pay_scene VARCHAR(32) NOT NULL, amount DECIMAL(18, 2) NOT NULL, "
                    + "source_kind VARCHAR(16) DEFAULT 'STANDARD_ORDER', "
                    + "cashier_status VARCHAR(32) NOT NULL, cashier_status_type VARCHAR(32) NOT NULL, "
                    + "created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, attempt_no VARCHAR(64) NOT NULL, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, terminal VARCHAR(32) NOT NULL, "
                    + "channel_code VARCHAR(64), payment_method VARCHAR(32), "
                    + "attempt_status VARCHAR(32), attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL PRIMARY KEY, status VARCHAR(32) NOT NULL, "
                    + "order_no VARCHAR(64), amount DECIMAL(18,2), status_type VARCHAR(32), "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(64), "
                    + "payment_method VARCHAR(32), "
                    + "created_at DATETIME, success_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, status) VALUES "
                    + "('PAY-001', 'WAIT_CALLBACK'), ('PAY-002', 'PREPAY_CREATED'), "
                    + "('PAY-003', 'PREPAY_CREATED')");
            statement.executeUpdate("UPDATE t_payment_order SET order_no = 'ORD-002', amount = 100.00, "
                    + "status_type = 'success', channel_code = 'wx_h5', channel_transaction_no = 'TXN-002', "
                    + "payment_method = '微信支付', "
                    + "created_at = '2026-09-12 11:59:00', success_at = '2026-09-12 12:01:00' "
                    + "WHERE payment_order_id = 'PAY-002'");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL)");
            statement.executeUpdate("INSERT INTO t_order (order_no) VALUES ('ORD-001')");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, order_no, bill_no, customer_name, cashier_title, "
                    + "pay_scene, amount, cashier_status, cashier_status_type, created_at, expires_at) VALUES "
                    + "('PRE-STABLE-001', 'PAY-001', 'ORD-001', 'BILL-001', '客户一', '收银台一', "
                    + "'H5', 100.00, '待支付', 'warning', '2026-09-12 12:00:00', '2026-09-15 12:00:00'), "
                    + "('PRE-STABLE-002', 'PAY-002', 'ORD-002', 'BILL-002', '客户二', '收银台二', "
                    + "'H5', 100.00, '待支付', 'warning', '2026-09-12 12:00:00', '2026-09-15 12:00:00'), "
                    + "('PRE-STABLE-003', 'PAY-003', 'ORD-003', 'BILL-003', '客户三', '收银台三', "
                    + "'H5', 100.00, '待支付', 'warning', '2026-09-12 12:00:00', '2026-09-15 12:00:00')");
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='VIRTUAL' "
                    + "WHERE order_no <> 'ORD-001'");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, terminal, created_at) VALUES "
                    + "('ATT-001-OLD', 'PAY-001', 'H5', '2026-09-12 12:00:00'), "
                    + "('ATT-001-NEW', 'PAY-001', 'API', '2026-09-12 12:00:00'), "
                    + "('ATT-002', 'PAY-002', 'MINI_PROGRAM', '2026-09-12 12:00:00')");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code = 'wx_h5', "
                    + "payment_method = '微信支付', attempt_status = '成功', attempt_status_type = 'success', "
                    + "completed_at = '2026-09-12 12:01:00' WHERE attempt_no = 'ATT-002'");
            session.commit();
        }
    }

    private List<String> sessionNos(List<CashierSessionListItemDTO> sessions) {
        return sessions.stream().map(CashierSessionListItemDTO::getSessionNo).collect(Collectors.toList());
    }
}
