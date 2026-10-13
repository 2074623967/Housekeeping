package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
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
 * 退款列表与导出的 MySQL 兼容 SQL 集成测试。
 */
class RefundListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:refund_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RefundMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RefundMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/RefundMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseStableIdTieBreakerForListAndExport() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setPageSize(1);

            assertEquals("REF-003", mapper.findAll(query).get(0).getRefundOrderId());
            query.setPageNo(2);
            assertEquals("REF-002", mapper.findAll(query).get(0).getRefundOrderId());
            query.setPageNo(3);
            assertEquals("REF-001", mapper.findAll(query).get(0).getRefundOrderId());

            assertEquals(Arrays.asList("REF-003", "REF-002", "REF-001"),
                    refundOrderIds(mapper.findAllForExport(query)));
            assertEquals("HOME_SERVICE", mapper.findAllForExport(query).get(0).getPayScene());
        }
    }

    @Test
    void shouldSearchRefundAndPaymentIdentifiersByLiteralSubstring() throws Exception {
        String suffix = "%_!\\\"";
        String refundOrderId = "REF-LITERAL-" + suffix;
        String paymentOrderId = "PAY-LITERAL-" + suffix;
        try (SqlSession session = sqlSessionFactory.openSession();
             PreparedStatement update = session.getConnection().prepareStatement(
                     "UPDATE t_refund_order SET refund_order_id = ?, payment_order_id = ? "
                             + "WHERE refund_order_id = 'REF-003'")) {
            update.setString(1, refundOrderId);
            update.setString(2, paymentOrderId);
            assertEquals(1, update.executeUpdate());
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            for (String fragment : Arrays.asList("%", "_", "!", "\\", suffix)) {
                RefundQueryDTO query = new RefundQueryDTO();
                query.setRefundOrderId(fragment);
                assertLiteralRefundResults(mapper, query, refundOrderId);
                query.setRefundOrderId(null);
                query.setPaymentOrderId(fragment);
                assertLiteralRefundResults(mapper, query, refundOrderId);
                assertEquals(fragment, query.getPaymentOrderId());
            }
        }
    }

    private void assertLiteralRefundResults(RefundMapper mapper, RefundQueryDTO query,
                                            String refundOrderId) {
        assertEquals(1L, mapper.count(query), query.toString());
        assertEquals(Arrays.asList(refundOrderId), refundOrderIds(mapper.findAll(query)));
        assertEquals(Arrays.asList(refundOrderId), refundOrderIds(mapper.findAllForExport(query)));
    }

    @Test
    void shouldKeepRefundRowsUniqueWhenPrepayAssociationsRepeat() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, pay_scene, created_at) "
                    + "VALUES ('PAY-001', 'HOME_REPAIR', TIMESTAMP '2026-09-10 08:00:00')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundOrderId("REF-001");

            assertEquals(1, mapper.findAll(query).size());
            assertEquals("HOME_SERVICE", mapper.findAll(query).get(0).getPayScene());
            assertEquals(1L, mapper.count(query));
            assertEquals(1, mapper.findAllForExport(query).size());
            assertEquals("HOME_SERVICE", mapper.findByRefundOrderId("REF-001").getPayScene());
        }
    }

    @Test
    void shouldNotBorrowPrepaySceneThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'pay-001' "
                    + "WHERE payment_order_id = 'PAY-001'");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundOrderId("REF-001");
            assertNull(mapper.findAll(query).get(0).getPayScene());
            assertNull(mapper.findAllForExport(query).get(0).getPayScene());
            assertNull(mapper.findByRefundOrderId("REF-001").getPayScene());
        }
    }

    @Test
    void shouldRejectTerminalSuccessBorrowingPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, payment_order_id, order_no, "
                    + "customer_name, refund_amount, refund_method, channel_code, channel_refund_no, "
                    + "channel_submitted_at, status, status_type, applied_at, success_at, terminal_at) VALUES "
                    + "('REF-ALIAS-PARENT', 'PAY-001', 'ORD-001', '客户一', 10.00, '归属诊断', "
                    + "'wx_h5', 'CH-ALIAS-PARENT', '2026-09-11 12:30:00', 'SUCCESS', 'success', "
                    + "'2026-09-11 12:00:00', '2026-09-11 13:00:00', '2026-09-11 13:00:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-ALIAS-PARENT', 'wx_h5', 'CH-ALIAS-PARENT', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:00:00', '2026-09-11 12:30:00')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundMethod("归属诊断");
            query.setRefundStatus("TERMINAL_FACT_MISMATCH");
            assertEquals(0L, mapper.count(query));
            statement.executeUpdate("UPDATE t_refund_order SET payment_order_id = 'pay-001' "
                    + "WHERE refund_order_id = 'REF-ALIAS-PARENT'");
            session.clearCache();
            assertEquals(1L, mapper.count(query));
            assertEquals("REF-ALIAS-PARENT", mapper.findAll(query).get(0).getRefundOrderId());
            assertEquals("REF-ALIAS-PARENT", mapper.findAllForExport(query).get(0).getRefundOrderId());
            query.setRefundStatus("UNVERIFIED_SUCCESS");
            assertEquals(1L, mapper.count(query));
        }
    }

    @Test
    void shouldNotIncludePaymentNumberAliasInCumulativeRefundAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, payment_order_id, order_no, "
                    + "customer_name, refund_amount, refund_method, channel_code, channel_refund_no, "
                    + "channel_submitted_at, status, status_type, applied_at, success_at, terminal_at) VALUES "
                    + "('REF-CUM-OWN', 'PAY-001', 'ORD-001', '客户一', 70.00, '累计归属', "
                    + "'wx_h5', 'CH-CUM-OWN', '2026-09-11 12:30:00', 'SUCCESS', 'success', "
                    + "'2026-09-11 12:00:00', '2026-09-11 13:00:00', '2026-09-11 13:00:00'), "
                    + "('REF-CUM-ALIAS', 'pay-001', 'ORD-001', '客户一', 50.00, '累计归属', "
                    + "'wx_h5', 'CH-CUM-ALIAS', '2026-09-11 12:31:00', 'SUCCESS', 'success', "
                    + "'2026-09-11 12:01:00', '2026-09-11 13:01:00', '2026-09-11 13:01:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-CUM-OWN', 'wx_h5', 'CH-CUM-OWN', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:00:00', '2026-09-11 12:30:00'), "
                    + "('REF-CUM-ALIAS', 'wx_h5', 'CH-CUM-ALIAS', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:01:00', '2026-09-11 12:31:00')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundStatus("TERMINAL_FACT_MISMATCH");
            query.setRefundOrderId("REF-CUM-OWN");
            assertEquals(0L, mapper.count(query));
            query.setRefundOrderId("REF-CUM-ALIAS");
            assertEquals(1L, mapper.count(query));
        }
    }

    @Test
    void shouldNotTrustTerminalAttemptThroughRefundNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, payment_order_id, order_no, "
                    + "customer_name, refund_amount, refund_method, channel_code, channel_refund_no, "
                    + "channel_submitted_at, status, status_type, applied_at, success_at, terminal_at) VALUES "
                    + "('REF-ALIAS-ATTEMPT', 'PAY-001', 'ORD-001', '客户一', 10.00, '尝试归属', "
                    + "'wx_h5', 'CH-ALIAS-ATTEMPT', '2026-09-11 12:30:00', 'SUCCESS', 'success', "
                    + "'2026-09-11 12:00:00', '2026-09-11 13:00:00', '2026-09-11 13:00:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('ref-alias-attempt', 'wx_h5', 'CH-ALIAS-ATTEMPT', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:00:00', '2026-09-11 12:30:00')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundMethod("尝试归属");
            query.setRefundStatus("TERMINAL_FACT_MISMATCH");
            assertEquals(1L, mapper.count(query));
            assertEquals("REF-ALIAS-ATTEMPT", mapper.findAll(query).get(0).getRefundOrderId());
            assertEquals("REF-ALIAS-ATTEMPT", mapper.findAllForExport(query).get(0).getRefundOrderId());
        }
    }

    @Test
    void shouldApplyPendingStatusToListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, status, status_type, applied_at) VALUES "
                    + "('REF-PENDING-REVIEW', 'PAY-PENDING-1', 'ORD-PENDING-1', '客户甲', 1.00, "
                    + "'待收口测试', 'REVIEWING', 'warning', '2026-09-12 12:00:00'), "
                    + "('REF-PENDING-PROCESS', 'PAY-PENDING-2', 'ORD-PENDING-2', '客户乙', 2.00, "
                    + "'待收口测试', 'PROCESSING', 'warning', '2026-09-12 12:00:01'), "
                    + "('REF-PENDING-SUCCESS', 'PAY-PENDING-3', 'ORD-PENDING-3', '客户丙', 3.00, "
                    + "'待收口测试', 'SUCCESS', 'success', '2026-09-12 12:00:02'), "
                    + "('REF-PENDING-FAIL', 'PAY-PENDING-4', 'ORD-PENDING-4', '客户丁', 4.00, "
                    + "'待收口测试', 'FAIL', 'danger', '2026-09-12 12:00:03')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundStatus("PENDING");
            query.setRefundMethod("待收口测试");

            assertEquals(Arrays.asList("REF-PENDING-PROCESS", "REF-PENDING-REVIEW"),
                    refundOrderIds(mapper.findAll(query)));
            assertEquals(2L, mapper.count(query));
            assertEquals(Arrays.asList("REF-PENDING-PROCESS", "REF-PENDING-REVIEW"),
                    refundOrderIds(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldFilterSuccessfulRefundsWithoutSuccessTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, status, status_type, applied_at, success_at) VALUES "
                    + "('REF-SUCCESS-MISSING', 'PAY-MISSING', 'ORD-MISSING', '客户甲', 1.00, "
                    + "'完整性测试', 'SUCCESS', 'success', '2026-09-11 12:00:00', NULL), "
                    + "('REF-SUCCESS-PRESENT', 'PAY-PRESENT', 'ORD-PRESENT', '客户乙', 2.00, "
                    + "'完整性测试', 'SUCCESS', 'success', '2026-09-11 12:00:01', '2026-09-12 12:00:00')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundStatus("SUCCESS_TIME_MISSING");
            query.setRefundMethod("完整性测试");

            assertEquals(Arrays.asList("REF-SUCCESS-MISSING"), refundOrderIds(mapper.findAll(query)));
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("REF-SUCCESS-MISSING"),
                    refundOrderIds(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldKeepIsolatedMissingSuccessTimeOutOfTerminalMismatch() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, payment_order_id, order_no, "
                    + "customer_name, refund_amount, "
                    + "refund_method, status, status_type, applied_at, channel_code, channel_refund_no, "
                    + "channel_submitted_at, terminal_at) VALUES "
                    + "('REF-MISSING-ONLY', 'PAY-001', 'ORD-001', '客户一', 1.00, '独立缺失测试', 'SUCCESS', 'success', "
                    + "'2026-09-11 10:00:00', 'wx_h5', 'CH-MISSING', '2026-09-11 10:01:00', "
                    + "'2026-09-11 11:00:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-MISSING-ONLY', 'wx_h5', 'CH-MISSING', 'SUCCESS', 'success', "
                    + "'2026-09-11 11:00:00', '2026-09-11 10:01:00')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundMethod("独立缺失测试");
            query.setRefundStatus("SUCCESS_TIME_MISSING");
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("REF-MISSING-ONLY"), refundOrderIds(mapper.findAll(query)));
            query.setRefundStatus("UNVERIFIED_SUCCESS");
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("REF-MISSING-ONLY"), refundOrderIds(mapper.findAll(query)));
            assertEquals(Arrays.asList("REF-MISSING-ONLY"), refundOrderIds(mapper.findAllForExport(query)));
            query.setRefundStatus("TERMINAL_FACT_MISMATCH");
            assertEquals(0L, mapper.count(query));
            assertTrue(mapper.findAll(query).isEmpty());
            assertTrue(mapper.findAllForExport(query).isEmpty());

            statement.executeUpdate("UPDATE t_refund_attempt SET channel_refund_no = 'CH-OTHER' "
                    + "WHERE refund_order_id = 'REF-MISSING-ONLY'");
            session.clearCache();
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("REF-MISSING-ONLY"), refundOrderIds(mapper.findAll(query)));
            assertEquals(Arrays.asList("REF-MISSING-ONLY"), refundOrderIds(mapper.findAllForExport(query)));
            query.setRefundStatus("UNVERIFIED_SUCCESS");
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("REF-MISSING-ONLY"), refundOrderIds(mapper.findAll(query)));
        }
    }

    @Test
    void shouldFilterTerminalFactMismatchesForListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, channel_code, channel_refund_no, channel_submitted_at, status, "
                    + "status_type, applied_at, success_at, terminal_at) VALUES "
                    + "('REF-TERMINAL-OK', 'PAY-T1', 'ORD-T1', '客户甲', 1.00, "
                    + "'终态测试', 'wx_h5', 'CH-T1', '2026-09-11 12:30:00', 'SUCCESS', 'success', '2026-09-11 12:00:00', "
                    + "'2026-09-11 13:00:00', '2026-09-11 13:00:00'), "
                    + "('REF-TERMINAL-MISMATCH', 'PAY-T2', 'ORD-T2', '客户乙', 2.00, "
                    + "'终态测试', 'wx_h5', 'CH-T2', '2026-09-11 12:31:00', 'SUCCESS', 'success', '2026-09-11 12:01:00', "
                    + "'2026-09-11 13:01:00', '2026-09-11 13:01:00'), "
                    + "('REF-TERMINAL-NO-ATTEMPT', 'PAY-T3', 'ORD-T3', '客户丙', 3.00, "
                    + "'终态测试', 'wx_h5', 'CH-T3', '2026-09-11 12:32:00', 'FAIL', 'danger', '2026-09-11 12:02:00', NULL, NULL), "
                    + "('REF-TERMINAL-CHANNEL-MISSING', 'PAY-T4', 'ORD-T4', '客户丁', 4.00, "
                    + "'终态测试', NULL, NULL, NULL, 'SUCCESS', 'success', '2026-09-11 12:03:00', "
                    + "'2026-09-11 13:03:00', '2026-09-11 13:03:00'), "
                    + "('REF-TERMINAL-CHANNEL-ORDER', 'PAY-T5', 'ORD-T5', '客户戊', 5.00, "
                    + "'终态测试', 'wx_h5', 'CH-T5', '2026-09-11 13:05:00', 'SUCCESS', 'success', '2026-09-11 12:04:00', "
                    + "'2026-09-11 13:04:00', '2026-09-11 13:04:00'), "
                    + "('REF-TERMINAL-SUCCESS-ORDER', 'PAY-T6', 'ORD-T6', '客户己', 6.00, "
                    + "'终态测试', 'wx_h5', 'CH-T6', '2026-09-11 12:35:00', 'SUCCESS', 'success', '2026-09-11 12:05:00', "
                    + "'2026-09-11 12:04:00', '2026-09-11 13:05:00'), "
                    + "('REF-TERMINAL-CHANNEL-MISMATCH', 'PAY-T7', 'ORD-T7', '客户庚', 7.00, "
                    + "'终态测试', 'wx_h5', 'CH-T7', '2026-09-11 12:36:00', 'SUCCESS', 'success', '2026-09-11 12:06:00', "
                    + "'2026-09-11 13:06:00', '2026-09-11 13:06:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, "
                    + "channel_refund_no, attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-TERMINAL-OK', 'wx_h5', 'CH-T1', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:00:00', '2026-09-11 12:00:01'), "
                    + "('REF-TERMINAL-MISMATCH', 'wx_h5', 'CH-T2', 'ACCEPTED', 'warn', NULL, "
                    + "'2026-09-11 12:01:01'), "
                    + "('REF-TERMINAL-CHANNEL-MISMATCH', 'wx_h5', 'CH-OTHER', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:06:00', '2026-09-11 12:06:01')");
            statement.executeUpdate("INSERT INTO t_payment_order "
                    + "SELECT DISTINCT payment_order_id, 100.00 FROM t_refund_order "
                    + "WHERE payment_order_id LIKE 'PAY-T%'");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundStatus("TERMINAL_FACT_MISMATCH");
            query.setRefundMethod("终态测试");

            assertEquals(Arrays.asList(
                    "REF-TERMINAL-CHANNEL-MISMATCH",
                    "REF-TERMINAL-SUCCESS-ORDER",
                    "REF-TERMINAL-CHANNEL-ORDER",
                    "REF-TERMINAL-CHANNEL-MISSING",
                    "REF-TERMINAL-NO-ATTEMPT",
                    "REF-TERMINAL-MISMATCH"),
                    refundOrderIds(mapper.findAll(query)));
            assertEquals(6L, mapper.count(query));
            assertEquals(Arrays.asList(
                    "REF-TERMINAL-CHANNEL-MISMATCH",
                    "REF-TERMINAL-SUCCESS-ORDER",
                    "REF-TERMINAL-CHANNEL-ORDER",
                    "REF-TERMINAL-CHANNEL-MISSING",
                    "REF-TERMINAL-NO-ATTEMPT",
                    "REF-TERMINAL-MISMATCH"),
                    refundOrderIds(mapper.findAllForExport(query)));

            query.setRefundStatus("UNVERIFIED_SUCCESS");
            assertEquals(Arrays.asList(
                    "REF-TERMINAL-CHANNEL-MISMATCH",
                    "REF-TERMINAL-SUCCESS-ORDER",
                    "REF-TERMINAL-CHANNEL-ORDER",
                    "REF-TERMINAL-CHANNEL-MISSING",
                    "REF-TERMINAL-MISMATCH"), refundOrderIds(mapper.findAll(query)));
            assertEquals(5L, mapper.count(query));
            assertEquals(refundOrderIds(mapper.findAll(query)), refundOrderIds(mapper.findAllForExport(query)));
            query.setRefundStatus("TERMINAL_FACT_MISMATCH");

            RefundQueryDTO successfulQuery = new RefundQueryDTO();
            successfulQuery.setRefundStatus("SUCCESS");
            successfulQuery.setRefundMethod("终态测试");
            RefundListItemDTO terminalRow = mapper.findAll(successfulQuery).stream()
                    .filter(item -> "REF-TERMINAL-OK".equals(item.getRefundOrderId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing terminal refund list row"));
            assertEquals("wx_h5", terminalRow.getChannelCode());
            assertEquals("CH-T1", terminalRow.getChannelRefundNo());
            assertEquals("2026-09-11 12:30:00", terminalRow.getChannelSubmittedAt());
            assertEquals("2026-09-11 13:00:00", terminalRow.getTerminalAt());
            RefundListItemDTO exportedTerminalRow = mapper.findAllForExport(successfulQuery).stream()
                    .filter(item -> "REF-TERMINAL-OK".equals(item.getRefundOrderId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing terminal refund export row"));
            assertEquals("CH-T1", exportedTerminalRow.getChannelRefundNo());
            assertEquals("2026-09-11 12:30:00", exportedTerminalRow.getChannelSubmittedAt());
            RefundListItemDTO singleRow = mapper.findByRefundOrderId("REF-TERMINAL-OK");
            assertEquals("2026-09-11 13:00:00", singleRow.getTerminalAt());

            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status_type = 'warn' "
                    + "WHERE refund_order_id = 'REF-TERMINAL-OK'");
            session.clearCache();
            assertTrue(refundOrderIds(mapper.findAll(query)).contains("REF-TERMINAL-OK"));
            assertEquals(7L, mapper.count(query));
            assertTrue(refundOrderIds(mapper.findAllForExport(query)).contains("REF-TERMINAL-OK"));
        }
    }

    @Test
    void shouldExposeMissingTerminalStatusTypeForHistoricalRefundReview() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, payment_order_id, order_no, "
                    + "customer_name, refund_amount, refund_method, channel_code, channel_refund_no, "
                    + "channel_submitted_at, status, status_type, applied_at, success_at, terminal_at) VALUES "
                    + "('REF-TYPE-MISSING', 'PAY-TYPE', 'ORD-TYPE', '客户庚', 7.00, '终态空样式', "
                    + "'wx_h5', 'CH-TYPE', '2026-09-11 12:30:00', 'SUCCESS', NULL, "
                    + "'2026-09-11 12:00:00', '2026-09-11 13:00:00', '2026-09-11 13:00:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, attempt_status, "
                    + "completed_at, created_at) VALUES "
                    + "('REF-TYPE-MISSING', 'SUCCESS', '2026-09-11 13:00:00', '2026-09-11 12:30:00')");
            statement.executeUpdate("INSERT INTO t_payment_order VALUES ('PAY-TYPE', 100.00)");
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundStatus("TERMINAL_FACT_MISMATCH");
            query.setRefundMethod("终态空样式");
            RefundMapper mapper = session.getMapper(RefundMapper.class);

            assertEquals(Arrays.asList("REF-TYPE-MISSING"), refundOrderIds(mapper.findAll(query)));
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("REF-TYPE-MISSING"), refundOrderIds(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldExposeInvalidSuccessAmountsInDiagnosticListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, payment_order_id, order_no, "
                    + "customer_name, refund_amount, refund_method, channel_code, channel_refund_no, "
                    + "channel_submitted_at, status, status_type, applied_at, success_at, terminal_at) VALUES "
                    + "('REF-AMOUNT', 'PAY-AMOUNT', 'ORD-AMOUNT', '客户金额', 10.00, '金额诊断', "
                    + "'wx_h5', 'CH-AMOUNT', '2026-09-11 12:30:00', 'SUCCESS', 'success', "
                    + "'2026-09-11 12:00:00', '2026-09-11 13:00:00', '2026-09-11 13:00:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, "
                    + "channel_refund_no, attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-AMOUNT', 'wx_h5', 'CH-AMOUNT', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:00:00', '2026-09-11 12:30:00')");
            statement.executeUpdate("INSERT INTO t_payment_order VALUES ('PAY-AMOUNT', 10.00)");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundMethod("金额诊断");

            for (String amount : new String[] {"0.00", "-5.00", "11.00"}) {
                statement.executeUpdate("UPDATE t_refund_order SET refund_amount = " + amount
                        + " WHERE refund_order_id = 'REF-AMOUNT'");
                session.clearCache();
                for (String status : new String[] {"TERMINAL_FACT_MISMATCH", "UNVERIFIED_SUCCESS"}) {
                    query.setRefundStatus(status);
                    assertEquals(1L, mapper.count(query));
                    assertEquals("¥" + amount, mapper.findAll(query).get(0).getRefundAmount());
                    assertEquals("¥" + amount, mapper.findAllForExport(query).get(0).getRefundAmount());
                }
            }
            statement.executeUpdate("UPDATE t_refund_order SET refund_amount = 10.00 "
                    + "WHERE refund_order_id = 'REF-AMOUNT'");
            session.clearCache();
            assertEquals(0L, mapper.count(query));

            statement.executeUpdate("DELETE FROM t_payment_order WHERE payment_order_id = 'PAY-AMOUNT'");
            session.clearCache();
            assertEquals(1L, mapper.count(query));
            assertEquals("REF-AMOUNT", mapper.findAll(query).get(0).getRefundOrderId());
        }
    }

    @Test
    void shouldExposeCumulativelyOverpaidSuccessesInDiagnosticListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order VALUES ('PAY-CUMULATIVE', 25.00)");
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, payment_order_id, order_no, "
                    + "customer_name, refund_amount, refund_method, channel_code, channel_refund_no, "
                    + "channel_submitted_at, status, status_type, applied_at, success_at, terminal_at) VALUES "
                    + "('REF-CUMULATIVE-1', 'PAY-CUMULATIVE', 'ORD-CUMULATIVE', '客户甲', 10.00, '累计诊断', "
                    + "'wx_h5', 'CH-CUMULATIVE-1', '2026-09-11 12:30:00', 'SUCCESS', 'success', "
                    + "'2026-09-11 12:00:00', '2026-09-11 13:00:00', '2026-09-11 13:00:00'), "
                    + "('REF-CUMULATIVE-2', 'PAY-CUMULATIVE', 'ORD-CUMULATIVE', '客户乙', 20.00, '累计诊断', "
                    + "'wx_h5', 'CH-CUMULATIVE-2', '2026-09-11 12:31:00', 'SUCCESS', 'success', "
                    + "'2026-09-11 12:01:00', '2026-09-11 13:01:00', '2026-09-11 13:01:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-CUMULATIVE-1', 'wx_h5', 'CH-CUMULATIVE-1', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:00:00', '2026-09-11 12:30:00'), "
                    + "('REF-CUMULATIVE-2', 'wx_h5', 'CH-CUMULATIVE-2', 'SUCCESS', 'success', "
                    + "'2026-09-11 13:01:00', '2026-09-11 12:31:00')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setPaymentOrderId("PAY-CUMULATIVE");
            for (String status : new String[] {"TERMINAL_FACT_MISMATCH", "UNVERIFIED_SUCCESS"}) {
                query.setRefundStatus(status);
                assertEquals(2L, mapper.count(query));
                assertEquals(Arrays.asList("REF-CUMULATIVE-2", "REF-CUMULATIVE-1"),
                        refundOrderIds(mapper.findAll(query)));
                assertEquals(refundOrderIds(mapper.findAll(query)), refundOrderIds(mapper.findAllForExport(query)));
            }
            statement.executeUpdate("UPDATE t_payment_order SET amount = 30.00 "
                    + "WHERE payment_order_id = 'PAY-CUMULATIVE'");
            session.clearCache();
            assertEquals(0L, mapper.count(query));
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
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order (payment_order_id VARCHAR_IGNORECASE(64) PRIMARY KEY, "
                    + "amount DECIMAL(18, 2) NOT NULL)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "pay_scene VARCHAR(32) NOT NULL, created_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, order_no VARCHAR(64) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, refund_amount DECIMAL(18, 2) NOT NULL, "
                    + "refund_method VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, "
                    + "status_type VARCHAR(32), applied_at DATETIME NOT NULL, "
                    + "success_at DATETIME, terminal_at DATETIME, channel_code VARCHAR(64), "
                    + "channel_refund_no VARCHAR(128), channel_submitted_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "channel_code VARCHAR(64), channel_refund_no VARCHAR(128), "
                    + "attempt_status VARCHAR(32) NOT NULL, attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, status, status_type, applied_at) VALUES "
                    + "('REF-001', 'PAY-001', 'ORD-001', '客户一', 1.00, '原路退回', 'AUDITING', 'warning', "
                    + "'2026-09-12 11:00:00'), "
                    + "('REF-002', 'PAY-002', 'ORD-002', '客户二', 2.00, '原路退回', 'AUDITING', 'warning', "
                    + "'2026-09-12 11:00:00'), "
                    + "('REF-003', 'PAY-003', 'ORD-003', '客户三', 3.00, '原路退回', 'AUDITING', 'warning', "
                    + "'2026-09-12 11:00:00')");
            statement.executeUpdate("INSERT INTO t_prepay_order (payment_order_id, pay_scene, created_at) VALUES "
                    + "('PAY-001', 'HOME_SERVICE', TIMESTAMP '2026-09-12 10:00:00'), "
                    + "('PAY-002', 'HOME_SERVICE', TIMESTAMP '2026-09-12 10:00:00'), "
                    + "('PAY-003', 'HOME_SERVICE', TIMESTAMP '2026-09-12 10:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_order VALUES "
                    + "('PAY-001', 100.00), ('PAY-002', 100.00), ('PAY-003', 100.00)");
            session.commit();
        }
    }

    private List<String> refundOrderIds(List<RefundListItemDTO> refunds) {
        return refunds.stream().map(RefundListItemDTO::getRefundOrderId).collect(Collectors.toList());
    }
}
