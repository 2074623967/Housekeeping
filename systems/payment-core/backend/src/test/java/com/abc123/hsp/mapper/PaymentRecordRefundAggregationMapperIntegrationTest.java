package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentRecordDetailDTO;
import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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
 * 统一支付记录成功退款次数与金额聚合的 Mapper 集成测试。
 */
public class PaymentRecordRefundAggregationMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_record_refund;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentRecordMapper.class);
        configuration.addMapper(PaymentMapper.class);
        parseMapper(configuration, "mapper/PaymentRecordMapper.xml");
        parseMapper(configuration, "mapper/PaymentMapper.xml");
        parseMapper(configuration, "mapper/RefundMapper.xml");
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndFixtures();
    }

    @Test
    void shouldAggregateOnlySuccessfulRefundsInPaymentRecordList() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            List<PaymentRecordRowDTO> rows = session.getMapper(PaymentRecordMapper.class).findAll(query);

            PaymentRecordRowDTO refunded = findRow(rows, "PAY-REFUNDED");
            assertEquals(2, rows.size());
            assertEquals(2, refunded.getRefundCount());
            assertEquals("¥50.00", refunded.getRefundedAmount());
            assertEquals(4, refunded.getUnverifiedRefundCount());
            assertNull(refunded.getPayScene());
            assertEquals("MULTIPLE_PREPAY_BINDINGS", refunded.getPrepayBindingIssue());

            PaymentRecordRowDTO untouched = findRow(rows, "PAY-NO-REFUND");
            assertEquals(0, untouched.getRefundCount());
            assertEquals("¥0.00", untouched.getRefundedAmount());
            assertEquals(0, untouched.getUnverifiedRefundCount());
            assertEquals(2, session.getMapper(PaymentRecordMapper.class).count(query));
            assertEquals(2, session.getMapper(PaymentRecordMapper.class).findAllForExport(query).size());
        }
    }

    @Test
    void shouldNotCountTrustedRefundFromPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_order SET payment_order_id = 'pay-refunded' "
                    + "WHERE refund_order_id = 'REF-SUCCESS-2'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            for (PaymentRecordRowDTO row : Arrays.asList(
                    findRow(mapper.findAll(query), "PAY-REFUNDED"),
                    findRow(mapper.findAllForExport(query), "PAY-REFUNDED"),
                    mapper.findDetail("PAY-REFUNDED"))) {
                assertEquals(1, row.getRefundCount());
                assertEquals("¥20.00", row.getRefundedAmount());
                assertEquals(4, row.getUnverifiedRefundCount());
            }
        }
    }

    @Test
    void shouldExcludeCaseVariantTerminalStatusesFromTrustedRefundSummary() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            statement.executeUpdate("UPDATE t_refund_order SET status='success' "
                    + "WHERE refund_order_id='REF-SUCCESS-1'");
            PaymentRecordRowDTO listed = findRow(mapper.findAll(query), "PAY-REFUNDED");
            assertEquals(1, listed.getRefundCount());
            assertEquals("¥30.00", listed.getRefundedAmount());
            assertEquals(5, listed.getUnverifiedRefundCount());

            statement.executeUpdate("UPDATE t_refund_order SET status='SUCCESS' "
                    + "WHERE refund_order_id='REF-SUCCESS-1'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status='success' "
                    + "WHERE refund_order_id='REF-SUCCESS-1'");
            session.clearCache();
            PaymentRecordRowDTO exported = findRow(mapper.findAllForExport(query), "PAY-REFUNDED");
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");
            assertEquals(1, exported.getRefundCount());
            assertEquals("¥30.00", exported.getRefundedAmount());
            assertEquals(5, detail.getUnverifiedRefundCount());
        }
    }

    @Test
    void shouldExcludeMismatchedRefundChannelBindingsFromTrustedSummary() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_code='WX_H5' "
                    + "WHERE refund_order_id='REF-SUCCESS-1'");
            PaymentRecordRowDTO listed = findRow(mapper.findAll(query), "PAY-REFUNDED");
            assertEquals(1, listed.getRefundCount());
            assertEquals("¥30.00", listed.getRefundedAmount());
            assertEquals(5, listed.getUnverifiedRefundCount());

            statement.executeUpdate("UPDATE t_refund_attempt SET channel_code='wx_h5', channel_refund_no='ch-1' "
                    + "WHERE refund_order_id='REF-SUCCESS-1'");
            session.clearCache();
            PaymentRecordRowDTO exported = findRow(mapper.findAllForExport(query), "PAY-REFUNDED");
            assertEquals(1, exported.getRefundCount());
            assertEquals("¥30.00", exported.getRefundedAmount());

            statement.executeUpdate("UPDATE t_refund_order SET channel_refund_no=' ' "
                    + "WHERE refund_order_id='REF-SUCCESS-1'");
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_refund_no=' ' "
                    + "WHERE refund_order_id='REF-SUCCESS-1'");
            session.clearCache();
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");
            assertEquals(1, detail.getRefundCount());
            assertEquals(5, detail.getUnverifiedRefundCount());
        }
    }

    @Test
    void shouldUseSameSuccessfulRefundSummaryInPaymentRecordDetail() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordDetailDTO detail = session.getMapper(PaymentRecordMapper.class)
                    .findDetail("PAY-REFUNDED");

            assertNotNull(detail);
            assertEquals(2, detail.getRefundCount());
            assertEquals("¥50.00", detail.getRefundedAmount());
            assertEquals(4, detail.getUnverifiedRefundCount());
            assertNull(detail.getPayScene());
            assertNull(detail.getExpireTime());
            assertEquals("MULTIPLE_PREPAY_BINDINGS", detail.getPrepayBindingIssue());
        }
    }

    @Test
    void shouldExcludeAnomalousSuccessRefundsFromEveryRecordProjection() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordRowDTO listed = findRow(mapper.findAll(query), "PAY-REFUNDED");
            PaymentRecordRowDTO exported = findRow(mapper.findAllForExport(query), "PAY-REFUNDED");
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");

            assertEquals(2, listed.getRefundCount());
            assertEquals("¥50.00", listed.getRefundedAmount());
            assertEquals(listed.getRefundCount(), exported.getRefundCount());
            assertEquals(listed.getRefundedAmount(), exported.getRefundedAmount());
            assertEquals(Integer.valueOf(4), exported.getUnverifiedRefundCount());
            assertEquals(listed.getRefundCount(), detail.getRefundCount());
            assertEquals(listed.getRefundedAmount(), detail.getRefundedAmount());
            assertEquals(listed.getUnverifiedRefundCount(), detail.getUnverifiedRefundCount());
        }
    }

    @Test
    void shouldNotCountSuccessRefundWithDifferentLatestAttemptChannelReference() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_refund_no = 'CH-OTHER' "
                    + "WHERE refund_order_id = 'REF-SUCCESS-2'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            PaymentRecordRowDTO listed = findRow(mapper.findAll(query), "PAY-REFUNDED");
            PaymentRecordRowDTO exported = findRow(mapper.findAllForExport(query), "PAY-REFUNDED");
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");

            assertEquals(1, listed.getRefundCount());
            assertEquals("¥20.00", listed.getRefundedAmount());
            assertEquals(5, listed.getUnverifiedRefundCount());
            assertEquals(listed.getRefundCount(), exported.getRefundCount());
            assertEquals(listed.getRefundedAmount(), exported.getRefundedAmount());
            assertEquals(listed.getUnverifiedRefundCount(), detail.getUnverifiedRefundCount());
            session.rollback();
        }
    }

    @Test
    void shouldNotCountSuccessRefundWithIncorrectLatestAttemptStatusType() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status_type = 'warn' "
                    + "WHERE refund_order_id = 'REF-SUCCESS-2'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordRowDTO listed = findRow(
                    mapper.findAll(new PaymentRecordQueryDTO()), "PAY-REFUNDED");
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");

            assertEquals(1, listed.getRefundCount());
            assertEquals("¥20.00", listed.getRefundedAmount());
            assertEquals(5, listed.getUnverifiedRefundCount());
            assertEquals(listed.getRefundCount(), detail.getRefundCount());
            assertEquals(listed.getUnverifiedRefundCount(), detail.getUnverifiedRefundCount());
            session.rollback();
        }
    }

    @Test
    void shouldNotTreatNonPositiveSuccessRefundAmountAsRefundedMoney() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            for (String amount : new String[] {"0.00", "-5.00"}) {
                statement.executeUpdate("UPDATE t_refund_order SET refund_amount = " + amount
                        + " WHERE refund_order_id = 'REF-SUCCESS-2'");
                session.clearCache();
                PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
                PaymentRecordRowDTO listed = findRow(
                        mapper.findAll(new PaymentRecordQueryDTO()), "PAY-REFUNDED");
                PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");
                assertEquals(1, listed.getRefundCount());
                assertEquals("¥20.00", listed.getRefundedAmount());
                assertEquals(5, listed.getUnverifiedRefundCount());
                assertEquals(listed.getRefundCount(), detail.getRefundCount());
                assertEquals(listed.getRefundedAmount(), detail.getRefundedAmount());
            }
            session.rollback();
        }
    }

    @Test
    void shouldNotCountSuccessRefundExceedingOriginalPaidAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET amount = 25.00 "
                    + "WHERE payment_order_id = 'PAY-REFUNDED'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordRowDTO listed = findRow(
                    mapper.findAll(new PaymentRecordQueryDTO()), "PAY-REFUNDED");
            PaymentRecordRowDTO exported = findRow(
                    mapper.findAllForExport(new PaymentRecordQueryDTO()), "PAY-REFUNDED");
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");

            assertEquals(1, listed.getRefundCount());
            assertEquals("¥20.00", listed.getRefundedAmount());
            assertEquals(5, listed.getUnverifiedRefundCount());
            assertEquals(listed.getRefundedAmount(), exported.getRefundedAmount());
            assertEquals(listed.getUnverifiedRefundCount(), detail.getUnverifiedRefundCount());
            session.rollback();
        }
    }

    @Test
    void shouldQuarantineAllOtherwiseValidSuccessRefundsWhenTheirTotalExceedsPaidAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET amount = 40.00 "
                    + "WHERE payment_order_id = 'PAY-REFUNDED'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordRowDTO listed = findRow(
                    mapper.findAll(new PaymentRecordQueryDTO()), "PAY-REFUNDED");
            PaymentRecordRowDTO exported = findRow(
                    mapper.findAllForExport(new PaymentRecordQueryDTO()), "PAY-REFUNDED");
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");

            assertEquals(0, listed.getRefundCount());
            assertEquals("¥0.00", listed.getRefundedAmount());
            assertEquals(6, listed.getUnverifiedRefundCount());
            assertEquals(listed.getRefundCount(), exported.getRefundCount());
            assertEquals(listed.getRefundedAmount(), detail.getRefundedAmount());
            assertEquals(listed.getUnverifiedRefundCount(), detail.getUnverifiedRefundCount());
            session.rollback();
        }
    }

    @Test
    void shouldNotCountSuccessRefundWhenAttemptCompletesAfterSuccessWithinSameSecond() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_order SET terminal_at = '2026-09-10 08:09:00.002' "
                    + "WHERE refund_order_id = 'REF-SUCCESS-2'");
            statement.executeUpdate("UPDATE t_refund_attempt SET completed_at = '2026-09-10 08:09:00.001' "
                    + "WHERE refund_order_id = 'REF-SUCCESS-2'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            PaymentRecordRowDTO listed = findRow(mapper.findAll(query), "PAY-REFUNDED");
            PaymentRecordRowDTO exported = findRow(mapper.findAllForExport(query), "PAY-REFUNDED");
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-REFUNDED");

            assertEquals(1, listed.getRefundCount());
            assertEquals("¥20.00", listed.getRefundedAmount());
            assertEquals(5, listed.getUnverifiedRefundCount());
            assertEquals(listed.getRefundCount(), exported.getRefundCount());
            assertEquals(listed.getRefundedAmount(), exported.getRefundedAmount());
            assertEquals(listed.getUnverifiedRefundCount(), detail.getUnverifiedRefundCount());
            session.rollback();
        }
    }

    private PaymentRecordRowDTO findRow(List<PaymentRecordRowDTO> rows, String paymentOrderId) {
        return rows.stream()
                .filter(row -> paymentOrderId.equals(row.getPaymentOrderId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到支付记录: " + paymentOrderId));
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

    private void createTablesAndFixtures() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR '"
                    + PaymentRecordRefundAggregationMapperIntegrationTest.class.getName()
                    + ".formatDate'");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR '"
                    + PaymentRecordRefundAggregationMapperIntegrationTest.class.getName()
                    + ".formatAmount'");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR(64))");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR(64), customer_name VARCHAR(128), amount DECIMAL(18,2), "
                    + "payment_method VARCHAR(32), channel_code VARCHAR(64), "
                    + "channel_transaction_no VARCHAR(128), last_channel_return_code VARCHAR(64), status VARCHAR(32), "
                    + "status_type VARCHAR(32), success_at DATETIME, created_at DATETIME, updated_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR(64), amount DECIMAL(18,2), "
                    + "pay_scene VARCHAR(32), created_at DATETIME, expires_at DATETIME, "
                    + "source_kind VARCHAR(16) DEFAULT 'VIRTUAL')");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "attempt_no VARCHAR(64), channel_code VARCHAR(64), "
                    + "source_app_id VARCHAR(64), gateway_code VARCHAR(64), "
                    + "payment_method VARCHAR(32), terminal VARCHAR(32), client_ip VARCHAR(64), "
                    + "idempotency_key VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), completed_at DATETIME, request_payload VARCHAR(512), "
                    + "response_payload VARCHAR(512), created_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), refund_amount DECIMAL(18,2), "
                    + "status VARCHAR_IGNORECASE(32), status_type VARCHAR(32), applied_at DATETIME, "
                    + "channel_code VARCHAR_IGNORECASE(64), channel_refund_no VARCHAR_IGNORECASE(128), channel_submitted_at DATETIME, "
                    + "success_at DATETIME, terminal_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "channel_code VARCHAR_IGNORECASE(64), channel_refund_no VARCHAR_IGNORECASE(128), "
                    + "attempt_status VARCHAR_IGNORECASE(32), attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "event_type VARCHAR(64), event_topic VARCHAR(128), created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "channel_transaction_no, status, status_type, created_at, updated_at) VALUES "
                    + "('PAY-REFUNDED', 'ORD-REFUNDED', '张女士', 100.00, '微信', 'wx_h5', "
                    + "'WX-REFUNDED', 'SUCCESS', 'success', TIMESTAMP '2026-09-10 08:00:00', TIMESTAMP '2026-09-10 08:01:00'), "
                    + "('PAY-NO-REFUND', 'ORD-NO-REFUND', '李女士', 80.00, '支付宝', 'alipay_h5', "
                    + "'ALI-NO-REFUND', 'SUCCESS', 'success', TIMESTAMP '2026-09-10 07:00:00', TIMESTAMP '2026-09-10 07:01:00')");
            statement.executeUpdate("UPDATE t_payment_order SET success_at = created_at "
                    + "WHERE status = 'SUCCESS'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, pay_scene, created_at, expires_at) VALUES "
                    + "('PAY-REFUNDED', 'HOME_CLEAN', TIMESTAMP '2026-09-10 08:00:00', TIMESTAMP '2026-09-10 09:00:00'), "
                    + "('PAY-NO-REFUND', 'HOME_REPAIR', TIMESTAMP '2026-09-10 07:00:00', TIMESTAMP '2026-09-10 08:00:00'), "
                    + "('PAY-REFUNDED', 'HOME_REPAIR', TIMESTAMP '2026-09-09 08:00:00', TIMESTAMP '2026-09-09 08:30:00')");
            statement.executeUpdate("UPDATE t_prepay_order SET order_no = "
                    + "(SELECT order_no FROM t_payment_order WHERE payment_order_id = t_prepay_order.payment_order_id), "
                    + "amount = (SELECT amount FROM t_payment_order "
                    + "WHERE payment_order_id = t_prepay_order.payment_order_id)");
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, payment_order_id, refund_amount, status, status_type, applied_at, "
                    + "channel_code, channel_refund_no, channel_submitted_at, success_at, terminal_at) VALUES "
                    + "('REF-SUCCESS-1', 'PAY-REFUNDED', 20.00, 'SUCCESS', 'success', "
                    + "'2026-09-10 08:02:00', 'wx_h5', 'CH-1', '2026-09-10 08:03:00', "
                    + "'2026-09-10 08:05:00', '2026-09-10 08:05:00'), "
                    + "('REF-SUCCESS-2', 'PAY-REFUNDED', 30.00, 'SUCCESS', 'success', "
                    + "'2026-09-10 08:06:00', 'wx_h5', 'CH-2', '2026-09-10 08:07:00', "
                    + "'2026-09-10 08:09:00', '2026-09-10 08:09:00'), "
                    + "('REF-MISSING-TIME', 'PAY-REFUNDED', 60.00, 'SUCCESS', 'success', "
                    + "'2026-09-10 08:10:00', 'wx_h5', 'CH-MISSING', '2026-09-10 08:11:00', "
                    + "NULL, '2026-09-10 08:12:00'), "
                    + "('REF-LATE-ATTEMPT', 'PAY-REFUNDED', 70.00, 'SUCCESS', 'success', "
                    + "'2026-09-10 08:13:00', 'wx_h5', 'CH-LATE', '2026-09-10 08:14:00', "
                    + "'2026-09-10 08:15:00', '2026-09-10 08:15:00'), "
                    + "('REF-NO-ATTEMPT', 'PAY-REFUNDED', 80.00, 'SUCCESS', 'success', "
                    + "'2026-09-10 08:18:00', 'wx_h5', 'CH-NONE', '2026-09-10 08:19:00', "
                    + "'2026-09-10 08:20:00', '2026-09-10 08:20:00'), "
                    + "('REF-WRONG-TYPE', 'PAY-REFUNDED', 90.00, 'SUCCESS', 'warn', "
                    + "'2026-09-10 08:21:00', 'wx_h5', 'CH-TYPE', '2026-09-10 08:22:00', "
                    + "'2026-09-10 08:23:00', '2026-09-10 08:23:00'), "
                    + "('REF-PROCESSING', 'PAY-REFUNDED', 10.00, 'PROCESSING', 'warn', "
                    + "'2026-09-10 08:16:00', NULL, NULL, NULL, NULL, NULL), "
                    + "('REF-FAIL', 'PAY-REFUNDED', 40.00, 'FAIL', 'danger', "
                    + "'2026-09-10 08:17:00', NULL, NULL, NULL, NULL, NULL)");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, attempt_status, "
                    + "completed_at, created_at) VALUES "
                    + "('REF-SUCCESS-1', 'SUCCESS', '2026-09-10 08:05:00', '2026-09-10 08:03:00'), "
                    + "('REF-SUCCESS-2', 'SUCCESS', '2026-09-10 08:09:00', '2026-09-10 08:07:00'), "
                    + "('REF-MISSING-TIME', 'SUCCESS', '2026-09-10 08:12:00', '2026-09-10 08:11:00'), "
                    + "('REF-LATE-ATTEMPT', 'SUCCESS', '2026-09-10 08:15:00.001', '2026-09-10 08:14:00'), "
                    + "('REF-WRONG-TYPE', 'SUCCESS', '2026-09-10 08:23:00', '2026-09-10 08:22:00')");
            statement.executeUpdate("UPDATE t_refund_attempt SET "
                    + "channel_code = (SELECT channel_code FROM t_refund_order "
                    + "WHERE t_refund_order.refund_order_id = t_refund_attempt.refund_order_id), "
                    + "channel_refund_no = (SELECT channel_refund_no FROM t_refund_order "
                    + "WHERE t_refund_order.refund_order_id = t_refund_attempt.refund_order_id), "
                    + "attempt_status_type = 'success'");
            session.commit();
        }
    }

    public static String formatDate(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static String formatAmount(BigDecimal amount, int decimals) {
        return amount.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }
}
