package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentRecordDetailDTO;
import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
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
 * 支付记录列表与最近支付尝试的 MySQL 兼容 SQL 集成测试。
 */
class PaymentRecordListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_record_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentRecordMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentRecordMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentRecordMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseIdAsStableTieBreakerForEveryListSort() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE");
            query.setPageSize(1);

            assertEquals("PAY-RECORD-STABLE-003", mapper.findAll(query).get(0).getPaymentOrderId());
            query.setPageNo(2);
            assertEquals("PAY-RECORD-STABLE-002", mapper.findAll(query).get(0).getPaymentOrderId());
            query.setPageNo(3);
            assertEquals("PAY-RECORD-STABLE-001", mapper.findAll(query).get(0).getPaymentOrderId());
            assertEquals(3L, mapper.count(query));

            query.setPageNo(1);
            query.setPageSize(20);
            query.setSortField("paymentAmount");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList(
                            "PAY-RECORD-STABLE-001",
                            "PAY-RECORD-STABLE-002",
                            "PAY-RECORD-STABLE-003"),
                    paymentOrderIds(mapper.findAll(query)));

            query.setSortField("paidAt");
            query.setSortOrder("desc");
            assertEquals(Arrays.asList(
                            "PAY-RECORD-STABLE-003",
                            "PAY-RECORD-STABLE-002",
                            "PAY-RECORD-STABLE-001"),
                    paymentOrderIds(mapper.findAll(query)));
        }
    }

    @Test
    void shouldSelectNewestAttemptByIdWhenCreationTimesMatch() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordDetailDTO detail = session.getMapper(PaymentRecordMapper.class)
                    .findDetail("PAY-RECORD-STABLE-003");

            assertEquals("NEW_TERMINAL", detail.getLatestTerminal());
            assertEquals("NEW_KEY", detail.getLatestIdempotencyKey());
            assertEquals("SUCCESS", detail.getLatestAttemptStatus());
            assertEquals("new-response", detail.getLatestResponsePayload());
        }
    }

    private void createFixture() throws Exception {
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
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) UNIQUE, "
                    + "order_no VARCHAR(64), customer_name VARCHAR(128), amount DECIMAL(18,2), "
                    + "payment_method VARCHAR(32), channel_code VARCHAR(64), "
                    + "channel_transaction_no VARCHAR(128), status VARCHAR(32), "
                    + "status_type VARCHAR(32), success_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "payment_method VARCHAR(32), terminal VARCHAR(32), client_ip VARCHAR(64), "
                    + "idempotency_key VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), request_payload VARCHAR(512), "
                    + "response_payload VARCHAR(512), created_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "payment_order_id VARCHAR(64), refund_amount DECIMAL(18,2), status VARCHAR(32))");
            statement.executeUpdate("INSERT INTO t_payment_order "
                    + "(payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "channel_transaction_no, status, status_type, created_at) VALUES "
                    + "('PAY-RECORD-STABLE-001', 'ORDER-RECORD-STABLE-001', '稳定用户甲', 100.00, "
                    + "'微信', 'wx_h5', 'CHANNEL-001', 'SUCCESS', 'success', '2026-09-12 16:00:00'), "
                    + "('PAY-RECORD-STABLE-002', 'ORDER-RECORD-STABLE-002', '稳定用户乙', 100.00, "
                    + "'微信', 'wx_h5', 'CHANNEL-002', 'SUCCESS', 'success', '2026-09-12 16:00:00'), "
                    + "('PAY-RECORD-STABLE-003', 'ORDER-RECORD-STABLE-003', '稳定用户丙', 100.00, "
                    + "'微信', 'wx_h5', 'CHANNEL-003', 'SUCCESS', 'success', '2026-09-12 16:00:00')");
            statement.executeUpdate("UPDATE t_payment_order SET success_at = "
                    + "TIMESTAMPADD(SECOND, id, created_at)");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(payment_order_id, payment_method, terminal, client_ip, idempotency_key, "
                    + "attempt_status, attempt_status_type, request_payload, response_payload, created_at) VALUES "
                    + "('PAY-RECORD-STABLE-003', '微信', 'OLD_TERMINAL', '127.0.0.1', 'OLD_KEY', "
                    + "'PROCESSING', 'warning', 'old-request', 'old-response', '2026-09-12 16:01:00'), "
                    + "('PAY-RECORD-STABLE-003', '微信', 'NEW_TERMINAL', '127.0.0.2', 'NEW_KEY', "
                    + "'SUCCESS', 'success', 'new-request', 'new-response', '2026-09-12 16:01:00')");
            session.commit();
        }
    }

    private List<String> paymentOrderIds(List<PaymentRecordRowDTO> records) {
        return records.stream()
                .map(PaymentRecordRowDTO::getPaymentOrderId)
                .collect(Collectors.toList());
    }
}
