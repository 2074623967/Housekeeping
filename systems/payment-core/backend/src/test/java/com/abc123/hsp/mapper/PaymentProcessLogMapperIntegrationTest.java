package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.hsp.dto.PaymentLogDetailDTO;
import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.dto.PaymentLogOverviewDTO;
import com.abc123.hsp.dto.PaymentLogQueryDTO;
import com.abc123.hsp.entity.PaymentProcessLogEntity;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
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
 * 独立支付处理日志的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentProcessLogMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_process_log;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentMapper.class);
        configuration.addMapper(PaymentLogMapper.class);
        parseMapper(configuration, "mapper/PaymentMapper.xml");
        parseMapper(configuration, "mapper/PaymentLogMapper.xml");
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldPersistIdempotentlyAndQueryIndependentProcessLog() {
        PaymentProcessLogEntity processLog = new PaymentProcessLogEntity();
        processLog.setLogNo("NTF-PROCESS-001");
        processLog.setPaymentOrderId("PAY-PROCESS-001");
        processLog.setOrderNo("ORD-PROCESS-001");
        processLog.setRequestNo("ATT-PROCESS-001");
        processLog.setSourceType("PAYMENT_NOTIFY");
        processLog.setSourceNo("NTF-PROCESS-001");
        processLog.setProcessStage("渠道回调");
        processLog.setLogLevel("ERROR");
        processLog.setLogLevelType("danger");
        processLog.setMessage("回调类型=QUERY_FAILED；处理状态=查单失败");
        processLog.setSource("wx_h5");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(1, mapper.insertPaymentProcessLog(processLog));
            assertEquals(0, mapper.insertPaymentProcessLog(processLog));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentLogMapper mapper = session.getMapper(PaymentLogMapper.class);
            PaymentLogQueryDTO query = new PaymentLogQueryDTO();
            query.setPaymentOrderId("PAY-PROCESS");
            query.setProcessStage("渠道回调");
            query.setLogLevel("ERROR");
            query.setSource("wx_h5");
            query.setKeyword("查单失败");

            List<PaymentLogListItemDTO> items = mapper.findAll(query);
            PaymentLogOverviewDTO overview = mapper.findOverviewSummary(query);

            assertEquals(1, items.size());
            assertEquals("NTF-PROCESS-001", items.get(0).getLogNo());
            assertEquals("ORD-PROCESS-001", items.get(0).getOrderNo());
            assertEquals(1L, mapper.count(query));
            assertEquals(1L, overview.getTotalLogCount());
            assertEquals(1L, overview.getErrorLogCount());
            assertEquals(1, overview.getCallbackErrorCount());
        }
    }

    @Test
    void shouldSearchLogIdentifiersAndMessageByLiteralSubstring() throws Exception {
        String suffix = "%_!\\\"";
        String paymentOrderId = "PAY-LOG-" + suffix;
        String orderNo = "ORD-LOG-" + suffix;
        String source = "source-" + suffix;
        String message = "message-" + suffix;
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             PreparedStatement insert = session.getConnection().prepareStatement(
                     "INSERT INTO t_payment_process_log (log_no, payment_order_id, order_no, "
                             + "source_type, source_no, process_stage, log_level, log_level_type, "
                             + "message, source, created_at) VALUES "
                             + "('LOG-LITERAL', ?, ?, 'LOCAL_TEST', 'LITERAL', "
                             + "'本地隔离', 'INFO', 'info', ?, ?, NOW())")) {
            insert.setString(1, paymentOrderId);
            insert.setString(2, orderNo);
            insert.setString(3, message);
            insert.setString(4, source);
            assertEquals(1, insert.executeUpdate());
            statement.executeUpdate("INSERT INTO t_payment_process_log (log_no, payment_order_id, "
                    + "order_no, source_type, source_no, process_stage, log_level, log_level_type, "
                    + "message, source, created_at) VALUES "
                    + "('LOG-PLAIN', 'PAY-LOG-PLAIN', 'ORD-LOG-PLAIN', 'LOCAL_TEST', 'PLAIN', "
                    + "'本地隔离', 'INFO', 'info', 'message-plain', 'source-plain', NOW())");

            PaymentLogMapper mapper = session.getMapper(PaymentLogMapper.class);
            for (String fragment : Arrays.asList("%", "_", "!", "\\", suffix)) {
                PaymentLogQueryDTO query = new PaymentLogQueryDTO();
                query.setProcessStage("本地隔离");
                query.setPaymentOrderId(fragment);
                assertLiteralLogResults(mapper, query);
                query.setPaymentOrderId(null);
                query.setOrderNo(fragment);
                assertLiteralLogResults(mapper, query);
                query.setOrderNo(null);
                query.setSource(fragment);
                assertLiteralLogResults(mapper, query);
                query.setSource(null);
                query.setKeyword(fragment);
                assertLiteralLogResults(mapper, query);
            }
        }
    }

    private void assertLiteralLogResults(PaymentLogMapper mapper, PaymentLogQueryDTO query) {
        assertEquals(1L, mapper.count(query), query.toString());
        assertEquals(Arrays.asList("LOG-LITERAL"), logNos(mapper.findAll(query)));
        assertEquals(Arrays.asList("LOG-LITERAL"), logNos(mapper.findAllForExport(query)));
        assertEquals(1L, mapper.findOverviewSummary(query).getTotalLogCount());
    }

    @Test
    void shouldNotIgnoreNonDuplicateDataErrors() {
        PaymentProcessLogEntity invalidLog = new PaymentProcessLogEntity();
        invalidLog.setLogNo("LOG-INVALID-001");
        invalidLog.setPaymentOrderId("PAY-INVALID-001");
        invalidLog.setSourceType("PAYMENT_EVENT");
        invalidLog.setSourceNo("EVT-INVALID-001");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertThrows(PersistenceException.class, () -> mapper.insertPaymentProcessLog(invalidLog));
        }
    }

    @Test
    void shouldUseStableIdTieBreakerForEveryListSort() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentLogMapper mapper = session.getMapper(PaymentLogMapper.class);
            PaymentLogQueryDTO query = new PaymentLogQueryDTO();
            query.setPaymentOrderId("PAY-LOG-STABLE");
            query.setPageSize(1);

            assertEquals("LOG-STABLE-003", mapper.findAll(query).get(0).getLogNo());
            query.setPageNo(2);
            assertEquals("LOG-STABLE-002", mapper.findAll(query).get(0).getLogNo());
            query.setPageNo(3);
            assertEquals("LOG-STABLE-001", mapper.findAll(query).get(0).getLogNo());

            query.setSortField("logLevel");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList("LOG-STABLE-001", "LOG-STABLE-002", "LOG-STABLE-003"),
                    logNos(mapper.findAllForExport(query)));
            query.setSortField("processStage");
            query.setSortOrder("desc");
            assertEquals(Arrays.asList("LOG-STABLE-003", "LOG-STABLE-002", "LOG-STABLE-001"),
                    logNos(mapper.findAllForExport(query)));
            assertEquals(3L, mapper.count(query));
        }
    }

    @Test
    void shouldFindPaymentLogDetailByUniqueLogNo() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentLogMapper mapper = session.getMapper(PaymentLogMapper.class);

            PaymentLogDetailDTO detail = mapper.findDetail("LOG-STABLE-001");

            assertEquals("LOG-STABLE-001", detail.getLogNo());
            assertEquals("PAY-LOG-STABLE", detail.getPaymentOrderId());
            assertEquals("ORD-001", detail.getOrderNo());
            assertEquals("ORD-001", detail.getStandardOrderNo());
            assertEquals("REQ-001", detail.getRequestNo());
            assertEquals("PAYMENT_ATTEMPT", detail.getSourceType());
            assertEquals("ATT-001", detail.getSourceNo());
            assertEquals("支付提交", detail.getProcessStage());
            assertEquals("INFO", detail.getLogLevel());
            assertEquals("info", detail.getLogLevelType());
            assertEquals("稳定排序日志一", detail.getMessage());
            assertEquals("payment-core", detail.getSource());
            assertEquals("2026-09-12 12:00:00", detail.getCreatedAt());
            assertNull(mapper.findDetail("LOG-MISSING"));
        }
    }

    @Test
    void shouldNotReadPaymentLogThroughCaseVariantIdentifier() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentLogMapper mapper = session.getMapper(PaymentLogMapper.class);
            assertNull(mapper.findDetail("log-stable-001"));
            assertEquals("LOG-STABLE-001", mapper.findDetail("LOG-STABLE-001").getLogNo());
        }
    }

    @Test
    void shouldNotBorrowSuccessFactIssueFromCaseVariantPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-LOG-STABLE'");
            statement.executeUpdate("UPDATE t_payment_process_log SET payment_order_id = 'pay-log-stable' "
                    + "WHERE log_no = 'LOG-STABLE-001'");
            PaymentLogMapper mapper = session.getMapper(PaymentLogMapper.class);
            PaymentLogQueryDTO query = new PaymentLogQueryDTO();
            query.setPaymentOrderId("pay-log-stable");

            assertNull(mapper.findDetail("LOG-STABLE-001").getSuccessFactIssue());
            assertNull(mapper.findAll(query).stream()
                    .filter(item -> "LOG-STABLE-001".equals(item.getLogNo()))
                    .findFirst().get().getSuccessFactIssue());
            assertNull(mapper.findAllForExport(query).stream()
                    .filter(item -> "LOG-STABLE-001".equals(item.getLogNo()))
                    .findFirst().get().getSuccessFactIssue());
            assertEquals(3L, mapper.count(query));
        }
    }

    @Test
    void shouldNotLinkVirtualLogToCollidingStandardOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentLogMapper mapper = session.getMapper(PaymentLogMapper.class);
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'VIRTUAL' "
                    + "WHERE payment_order_id = 'PAY-LOG-STABLE'");
            assertEquals("ORD-001", mapper.findDetail("LOG-STABLE-001").getOrderNo());
            assertNull(mapper.findDetail("LOG-STABLE-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'standard_order' "
                    + "WHERE payment_order_id = 'PAY-LOG-STABLE'");
            session.clearCache();
            assertNull(mapper.findDetail("LOG-STABLE-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'STANDARD_ORDER', "
                    + "order_no = 'OTHER-ORDER' WHERE payment_order_id = 'PAY-LOG-STABLE'");
            session.clearCache();
            assertNull(mapper.findDetail("LOG-STABLE-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-001' "
                    + "WHERE payment_order_id = 'PAY-LOG-STABLE'");
            session.clearCache();
            assertEquals("ORD-001", mapper.findDetail("LOG-STABLE-001").getStandardOrderNo());
        }
    }

    @Test
    void shouldProjectPaymentSuccessIssuesAcrossLogListExportAndDetail() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentLogMapper mapper = session.getMapper(PaymentLogMapper.class);
            PaymentLogQueryDTO query = new PaymentLogQueryDTO();
            query.setPaymentOrderId("PAY-LOG-STABLE");

            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findDetail("LOG-STABLE-001").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = '2026-09-12 12:00:00.001' "
                    + "WHERE payment_order_id = 'PAY-LOG-STABLE'");
            session.clearCache();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findDetail("LOG-STABLE-001").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-LOG-STABLE'");
            session.clearCache();
            assertEquals("SUCCESS_TIME_MISSING", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_TIME_MISSING", mapper.findDetail("LOG-STABLE-001").getSuccessFactIssue());
        }
    }

    private void parseMapper(Configuration configuration, String resource) throws Exception {
        try (InputStream mapperXml = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentProcessLogMapperIntegrationTest.class.getName() + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_process_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE)");
            statement.executeUpdate("INSERT INTO t_order (order_no) VALUES ('ORD-001')");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "order_no VARCHAR(64), "
                    + "amount DECIMAL(18,2) DEFAULT 1.00, status VARCHAR(32) NOT NULL, "
                    + "status_type VARCHAR(32), channel_code VARCHAR(64), "
                    + "payment_method VARCHAR(32) DEFAULT '微信支付', "
                    + "channel_transaction_no VARCHAR(128), success_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_prepay_order (payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR(64), amount DECIMAL(18,2), "
                    + "source_kind VARCHAR(16) DEFAULT 'STANDARD_ORDER')");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL, "
                    + "channel_code VARCHAR(64), payment_method VARCHAR(32) DEFAULT '微信支付', "
                    + "attempt_status VARCHAR(32), attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_order "
                    + "(payment_order_id, status, status_type, channel_code, channel_transaction_no, "
                    + "success_at, created_at) VALUES ('PAY-LOG-STABLE', 'SUCCESS', 'success', "
                    + "'wx_h5', 'WX-STABLE', '2026-09-12 12:00:00', '2026-09-12 11:59:00')");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(payment_order_id, channel_code, attempt_status, attempt_status_type, completed_at, created_at) "
                    + "VALUES ('PAY-LOG-STABLE', 'wx_h5', '成功', 'success', "
                    + "'2026-09-12 12:00:00', '2026-09-12 11:59:30')");
            statement.executeUpdate("UPDATE t_payment_order SET order_no = 'ORD-001' "
                    + "WHERE payment_order_id = 'PAY-LOG-STABLE'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "SELECT payment_order_id, order_no, amount FROM t_payment_order");
            statement.execute("CREATE TABLE t_payment_process_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, log_no VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, order_no VARCHAR(64), request_no VARCHAR(64), "
                    + "source_type VARCHAR(32) NOT NULL, source_no VARCHAR(64) NOT NULL, "
                    + "process_stage VARCHAR(32) NOT NULL, log_level VARCHAR(16) NOT NULL, "
                    + "log_level_type VARCHAR(16) NOT NULL, message TEXT NOT NULL, "
                    + "source VARCHAR(64) NOT NULL, created_at DATETIME NOT NULL, "
                    + "UNIQUE(source_type, source_no))");
            statement.executeUpdate("INSERT INTO t_payment_process_log "
                    + "(log_no, payment_order_id, order_no, request_no, source_type, source_no, "
                    + "process_stage, log_level, log_level_type, message, source, created_at) VALUES "
                    + "('LOG-STABLE-001', 'PAY-LOG-STABLE', 'ORD-001', 'REQ-001', "
                    + "'PAYMENT_ATTEMPT', 'ATT-001', '支付提交', 'INFO', 'info', "
                    + "'稳定排序日志一', 'payment-core', '2026-09-12 12:00:00'), "
                    + "('LOG-STABLE-002', 'PAY-LOG-STABLE', 'ORD-002', 'REQ-002', "
                    + "'PAYMENT_ATTEMPT', 'ATT-002', '支付提交', 'INFO', 'info', "
                    + "'稳定排序日志二', 'payment-core', '2026-09-12 12:00:00'), "
                    + "('LOG-STABLE-003', 'PAY-LOG-STABLE', 'ORD-003', 'REQ-003', "
                    + "'PAYMENT_ATTEMPT', 'ATT-003', '支付提交', 'INFO', 'info', "
                    + "'稳定排序日志三', 'payment-core', '2026-09-12 12:00:00')");
            session.commit();
        }
    }

    private List<String> logNos(List<PaymentLogListItemDTO> logs) {
        return logs.stream().map(PaymentLogListItemDTO::getLogNo).collect(Collectors.toList());
    }

    /**
     * H2 测试环境中的 MySQL DATE_FORMAT 等价实现。
     */
    public static String dateFormat(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
