package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.PaymentDetailDTO;
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
        }
    }

    @Test
    void shouldOnlySettleBillBoundToPaymentPrepay() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.updateBillAfterPayment(
                    "BILL-SNAPSHOT-TARGET", new BigDecimal("88.00"), "已结清", "success"));

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
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), order_no VARCHAR(64), "
                    + "customer_name VARCHAR(128), amount DECIMAL(18,2), payment_method VARCHAR(64), "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(128), "
                    + "last_channel_return_code VARCHAR(64), last_standardized_code VARCHAR(64), "
                    + "last_standardized_message VARCHAR(256), last_handling_suggestion VARCHAR(256), "
                    + "last_retryable VARCHAR(16), last_manual_intervention_required VARCHAR(16), "
                    + "last_mapping_version VARCHAR(32), last_mapping_matched TINYINT, "
                    + "status VARCHAR(32), status_type VARCHAR(32), success_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64), "
                    + "bill_no VARCHAR(64), payment_order_id VARCHAR(64), created_at DATETIME)");
            statement.execute("CREATE TABLE t_bill ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_no VARCHAR(64), order_no VARCHAR(64), "
                    + "paid_amount DECIMAL(18,2), bill_status VARCHAR(32), bill_status_type VARCHAR(32))");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), terminal VARCHAR(64), "
                    + "client_ip VARCHAR(64), idempotency_key VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), ops_config_snapshot_version BIGINT, "
                    + "request_payload CLOB, response_payload CLOB, completed_at DATETIME, created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, amount, "
                    + "payment_method, channel_code, channel_transaction_no, status, status_type, created_at) VALUES "
                    + "('PAY-SNAPSHOT-001', 'ORD-SNAPSHOT-001', '测试客户', 88.00, "
                    + "'微信支付', 'wx_h5', 'CH-SNAPSHOT-001', 'WAIT_CALLBACK', 'warn', NOW())");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, bill_no, payment_order_id, created_at) VALUES "
                    + "('PRE-SNAPSHOT-001', 'BILL-SNAPSHOT-TARGET', 'PAY-SNAPSHOT-001', NOW())");
            statement.executeUpdate("INSERT INTO t_bill "
                    + "(bill_no, order_no, paid_amount, bill_status, bill_status_type) VALUES "
                    + "('BILL-SNAPSHOT-TARGET', 'ORD-SNAPSHOT-001', 0.00, '待支付', 'warn'), "
                    + "('BILL-SNAPSHOT-OTHER', 'ORD-SNAPSHOT-001', 0.00, '待支付', 'warn')");
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
