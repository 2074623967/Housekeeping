package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.abc123.hsp.dto.PaymentRecordDetailDTO;
import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
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
        parseMapper(configuration, "mapper/PaymentRecordMapper.xml");
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndFixtures();
    }

    @Test
    void shouldAggregateOnlySuccessfulRefundsInPaymentRecordList() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            List<PaymentRecordRowDTO> rows = session.getMapper(PaymentRecordMapper.class).findAll(query);

            PaymentRecordRowDTO refunded = findRow(rows, "PAY-REFUNDED");
            assertEquals(2, refunded.getRefundCount());
            assertEquals("¥50.00", refunded.getRefundedAmount());

            PaymentRecordRowDTO untouched = findRow(rows, "PAY-NO-REFUND");
            assertEquals(0, untouched.getRefundCount());
            assertEquals("¥0.00", untouched.getRefundedAmount());
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
            assertEquals("2026-09-10 09:00:00", detail.getExpireTime());
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
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR(64), customer_name VARCHAR(128), amount DECIMAL(18,2), "
                    + "payment_method VARCHAR(32), channel_code VARCHAR(64), "
                    + "channel_transaction_no VARCHAR(128), status VARCHAR(32), "
                    + "status_type VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "payment_method VARCHAR(32), terminal VARCHAR(32), client_ip VARCHAR(64), "
                    + "idempotency_key VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), request_payload VARCHAR(512), "
                    + "response_payload VARCHAR(512), created_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "payment_order_id VARCHAR(64), refund_amount DECIMAL(18,2), "
                    + "status VARCHAR(32))");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "channel_transaction_no, status, status_type, created_at) VALUES "
                    + "('PAY-REFUNDED', 'ORD-REFUNDED', '张女士', 100.00, '微信', 'wx_h5', "
                    + "'WX-REFUNDED', 'SUCCESS', 'success', TIMESTAMP '2026-09-10 08:00:00'), "
                    + "('PAY-NO-REFUND', 'ORD-NO-REFUND', '李女士', 80.00, '支付宝', 'alipay_h5', "
                    + "'ALI-NO-REFUND', 'SUCCESS', 'success', TIMESTAMP '2026-09-10 07:00:00')");
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, payment_order_id, refund_amount, status) VALUES "
                    + "('REF-SUCCESS-1', 'PAY-REFUNDED', 20.00, 'SUCCESS'), "
                    + "('REF-SUCCESS-2', 'PAY-REFUNDED', 30.00, 'SUCCESS'), "
                    + "('REF-PROCESSING', 'PAY-REFUNDED', 10.00, 'PROCESSING'), "
                    + "('REF-FAIL', 'PAY-REFUNDED', 40.00, 'FAIL')");
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
