package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Statement;
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
 * 支付单最近渠道返回码快照的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentReturnCodeSnapshotMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_return_code_snapshot;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldPersistSnapshotAndReadItFromPaymentDetailInNewSession() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getMapper(PaymentMapper.class).updatePaymentReturnCodeSnapshot(
                    "PAY-SNAPSHOT-001",
                    "USERPAYING",
                    "PAYMENT-CHANNEL-1001",
                    "用户支付中",
                    "等待回调或主动查单",
                    "是",
                    "否",
                    "v2026.09.1",
                    true);
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentDetailDTO detail = session.getMapper(PaymentMapper.class)
                    .findDetail("PAY-SNAPSHOT-001");

            assertEquals("USERPAYING", detail.getChannelReturnCode());
            assertEquals("PAYMENT-CHANNEL-1001", detail.getStandardizedCode());
            assertEquals("用户支付中", detail.getStandardizedMessage());
            assertEquals("等待回调或主动查单", detail.getHandlingSuggestion());
            assertEquals("是", detail.getRetryable());
            assertEquals("否", detail.getManualInterventionRequired());
            assertEquals("v2026.09.1", detail.getReturnCodeMappingVersion());
            assertTrue(detail.getReturnCodeMappingMatched());
            assertEquals("BILL-SNAPSHOT-TARGET", detail.getBillNo());
            assertEquals("HOME_CLEAN", detail.getPayScene());
            assertTrue(detail.getPrepayAmountBindingValid());
        }
    }

    @Test
    void shouldNotUpdateReturnCodeSnapshotThroughCaseVariantPaymentIdentifier() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.updatePaymentReturnCodeSnapshot(
                    "pay-snapshot-001", "ALIAS", "ALIAS", "别名", "核查", "否", "是", "v-alias", false));
            assertNull(mapper.findDetail("PAY-SNAPSHOT-001").getChannelReturnCode());
        }
    }

    @Test
    void shouldNotOverwriteDisputedChannelTransactionWithStaleQuerySnapshot() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(payment_order_id, attempt_status, created_at) VALUES "
                    + "('PAY-SNAPSHOT-001', '渠道流水归属冲突，待人工核查', NOW())");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.updatePaymentReturnCodeSnapshot(
                    "PAY-SNAPSHOT-001", "USERPAYING", null, null, null, null, null, null, null));
            assertNull(mapper.findDetail("PAY-SNAPSHOT-001").getChannelReturnCode());

            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '待回调' "
                    + "WHERE payment_order_id = 'PAY-SNAPSHOT-001'");
            assertEquals(1, mapper.updatePaymentReturnCodeSnapshot(
                    "PAY-SNAPSHOT-001", "USERPAYING", null, null, null, null, null, null, null));
        }
    }

    @Test
    void shouldOnlySettleBillBoundToPaymentPrepay() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.updateBillAfterPayment(
                    "BILL-SNAPSHOT-TARGET", new BigDecimal("88.00"), "已结清", "success",
                    "ORD-SNAPSHOT-001", "测试客户", BigDecimal.ZERO));

            try (Statement statement = session.getConnection().createStatement();
                 java.sql.ResultSet resultSet = statement.executeQuery(
                         "SELECT bill_no, paid_amount, bill_status FROM t_bill ORDER BY bill_no")) {
                assertTrue(resultSet.next());
                assertEquals("BILL-SNAPSHOT-OTHER", resultSet.getString("bill_no"));
                assertEquals(0, resultSet.getBigDecimal("paid_amount").compareTo(BigDecimal.ZERO));
                assertEquals("待支付", resultSet.getString("bill_status"));
                assertTrue(resultSet.next());
                assertEquals("BILL-SNAPSHOT-TARGET", resultSet.getString("bill_no"));
                assertEquals(0, resultSet.getBigDecimal("paid_amount").compareTo(new BigDecimal("88.00")));
                assertEquals("已结清", resultSet.getString("bill_status"));
            }
        }
    }

    @Test
    void shouldRejectCaseVariantPrepayOrderNumberInPaymentDetail() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertTrue(mapper.findDetail("PAY-SNAPSHOT-001").getPrepayAmountBindingValid());
            statement.executeUpdate("UPDATE t_prepay_order SET order_no='ord-snapshot-001' "
                    + "WHERE payment_order_id='PAY-SNAPSHOT-001'");
            session.clearCache();
            org.junit.jupiter.api.Assertions.assertFalse(
                    mapper.findDetail("PAY-SNAPSHOT-001").getPrepayAmountBindingValid());
        }
    }

    @Test
    void shouldNotReadPaymentDetailOrSubmissionThroughCaseVariantIdentifier() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertTrue(mapper.findDetail("PAY-SNAPSHOT-001") != null);
            assertTrue(mapper.findSubmissionFact("PAY-SNAPSHOT-001") != null);
            assertNull(mapper.findDetail("pay-snapshot-001"));
            assertNull(mapper.findSubmissionFact("pay-snapshot-001"));
        }
    }

    @Test
    void shouldNotAttachCaseVariantPrepayOrAttemptToPaymentDetail() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(payment_order_id, attempt_status, created_at) VALUES "
                    + "('PAY-SNAPSHOT-001', '已提交', NOW()), "
                    + "('pay-snapshot-001', '成功', NOW())");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, bill_no, order_no, amount, pay_scene, payment_order_id, created_at) "
                    + "VALUES ('PRE-SNAPSHOT-ALIAS', 'BILL-SNAPSHOT-OTHER', 'ORD-SNAPSHOT-001', "
                    + "88.00, 'HOME_CLEAN', 'pay-snapshot-001', NOW())");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            PaymentDetailDTO detail = mapper.findDetail("PAY-SNAPSHOT-001");
            assertEquals("BILL-SNAPSHOT-TARGET", detail.getBillNo());
            assertTrue(detail.getPrepayAmountBindingValid());
            assertEquals("已提交", detail.getLatestAttemptStatus());
            assertEquals("已提交", mapper.findSubmissionFact("PAY-SNAPSHOT-001").getLatestAttemptStatus());
        }
    }

    @Test
    void shouldNotShowCaseVariantPrepayOrAttemptInPaymentLists() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(payment_order_id, attempt_status, created_at) VALUES "
                    + "('PAY-SNAPSHOT-001', '已提交', NOW()), "
                    + "('pay-snapshot-001', '成功', NOW())");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, bill_no, order_no, amount, pay_scene, payment_order_id, created_at) "
                    + "VALUES ('PRE-SNAPSHOT-ALIAS', 'BILL-SNAPSHOT-OTHER', 'ORD-SNAPSHOT-001', "
                    + "88.00, 'OTHER_SCENE', 'pay-snapshot-001', NOW())");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();

            assertEquals("HOME_CLEAN", mapper.findAll(query).get(0).getPayScene());
            assertEquals("已提交", mapper.findAll(query).get(0).getLatestAttemptStatus());
            assertEquals("HOME_CLEAN", mapper.findAllForExport(query).get(0).getPayScene());
            assertEquals("已提交", mapper.findAllForExport(query).get(0).getLatestAttemptStatus());
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
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_bill");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR(64))");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), order_no VARCHAR_IGNORECASE(64), "
                    + "customer_name VARCHAR(128), amount DECIMAL(18,2), payment_method VARCHAR(64), "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(128), "
                    + "last_channel_return_code VARCHAR(64), last_standardized_code VARCHAR(64), "
                    + "last_standardized_message VARCHAR(256), last_handling_suggestion VARCHAR(256), "
                    + "last_retryable VARCHAR(16), last_manual_intervention_required VARCHAR(16), "
                    + "last_mapping_version VARCHAR(32), last_mapping_matched TINYINT, "
                    + "status VARCHAR(32), status_type VARCHAR(32), success_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64), "
                    + "bill_no VARCHAR(64), order_no VARCHAR_IGNORECASE(64), amount DECIMAL(18,2), "
                    + "pay_scene VARCHAR(32), payment_order_id VARCHAR(64), "
                    + "source_kind VARCHAR(16) DEFAULT 'UNKNOWN', created_at DATETIME)");
            statement.execute("CREATE TABLE t_bill ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_no VARCHAR(64), order_no VARCHAR(64), "
                    + "customer_name VARCHAR(128), bill_amount DECIMAL(18,2), "
                    + "paid_amount DECIMAL(18,2), bill_status VARCHAR(32), bill_status_type VARCHAR(32))");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "channel_code VARCHAR(64), payment_method VARCHAR(64), terminal VARCHAR(64), "
                    + "client_ip VARCHAR(64), idempotency_key VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), ops_config_snapshot_version BIGINT, "
                    + "request_payload CLOB, response_payload CLOB, completed_at DATETIME, created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, amount, "
                    + "payment_method, channel_code, channel_transaction_no, status, status_type, created_at) VALUES "
                    + "('PAY-SNAPSHOT-001', 'ORD-SNAPSHOT-001', '测试客户', 88.00, "
                    + "'微信支付', 'wx_h5', 'CH-SNAPSHOT-001', 'WAIT_CALLBACK', 'warn', NOW())");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, bill_no, order_no, amount, pay_scene, payment_order_id, created_at) VALUES "
                    + "('PRE-SNAPSHOT-001', 'BILL-SNAPSHOT-TARGET', 'ORD-SNAPSHOT-001', 88.00, "
                    + "'HOME_CLEAN', 'PAY-SNAPSHOT-001', NOW())");
            statement.executeUpdate("INSERT INTO t_bill "
                    + "(bill_no, order_no, customer_name, bill_amount, paid_amount, bill_status, bill_status_type) "
                    + "VALUES ('BILL-SNAPSHOT-TARGET', 'ORD-SNAPSHOT-001', '测试客户', 88.00, "
                    + "0.00, '待支付', 'warn'), "
                    + "('BILL-SNAPSHOT-OTHER', 'ORD-SNAPSHOT-001', '测试客户', 88.00, "
                    + "0.00, '待支付', 'warn')");
            session.commit();
        }
    }

    /**
     * H2 测试环境中的 MySQL FORMAT 等价实现。
     */
    public static String format(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale).toPlainString();
    }
}
