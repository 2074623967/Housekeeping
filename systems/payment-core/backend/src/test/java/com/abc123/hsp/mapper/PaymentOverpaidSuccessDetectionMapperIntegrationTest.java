package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueResponsibilitySummaryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
 * 同订单成功收款超过订单应收后的异常与日终差异集成测试。
 */
public class PaymentOverpaidSuccessDetectionMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_overpaid_success;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentIssueMapper.class);
        configuration.addMapper(PaymentDayEndMapper.class);
        parseMapper(configuration, "mapper/PaymentIssueMapper.xml");
        parseMapper(configuration, "mapper/PaymentDayEndMapper.xml");
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndFixtures();
    }

    @Test
    void shouldExposeOnlyOrderWhoseSuccessfulPaymentsExceedReceivable() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueRowDTO issue = mapper.findByIssueNo("ISSUE-OVERPAID-ORD-OVERPAID");

            assertEquals("PAY-OVERPAID-2", issue.getPaymentOrderId());
            assertEquals("订单成功收款超额", issue.getIssueType());
            assertEquals("P1", issue.getSeverity());
            assertTrue(issue.getIssueSummary().contains("订单应收=100.00"));
            assertTrue(issue.getIssueSummary().contains("超额=10.00"));

            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("订单成功收款超额");
            assertEquals(1L, mapper.count(query));
            query.setOrderNo("ORD-SPLIT");
            assertEquals(0L, mapper.count(query));
        }
    }

    @Test
    void shouldRouteOverpaidSuccessToClearingAndBlockDayEndReadiness() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentIssueMapper issueMapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("订单成功收款超额");
            List<PaymentIssueResponsibilitySummaryDTO> summaries = issueMapper.responsibilitySummary(query);

            assertEquals(1, summaries.size());
            assertEquals("账务清结算联动组", summaries.get(0).getGroupName());
            assertEquals("danger", summaries.get(0).getGroupType());
            assertEquals(1, summaries.get(0).getP1Count());

            PaymentDayEndMapper dayEndMapper = session.getMapper(PaymentDayEndMapper.class);
            String today = LocalDate.now().toString();
            assertEquals(1, dayEndMapper.countPaymentSuccessGapByDate(today));
            assertEquals(0, dayEndMapper.sumPaymentSuccessGapAmountByDate(today)
                    .compareTo(new BigDecimal("30.00")));
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

    private void createTablesAndFixtures() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR '"
                    + PaymentOverpaidSuccessDetectionMapperIntegrationTest.class.getName()
                    + ".formatDate'");
            statement.execute("CREATE ALIAS IF NOT EXISTS STR_TO_DATE FOR '"
                    + PaymentOverpaidSuccessDetectionMapperIntegrationTest.class.getName()
                    + ".parseDate'");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_config");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_payment_notify_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64), "
                    + "order_amount DECIMAL(18,2))");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR(64), customer_name VARCHAR(128), amount DECIMAL(18,2), "
                    + "payment_method VARCHAR(32), channel_code VARCHAR(64), status VARCHAR(32), "
                    + "status_type VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "attempt_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, notify_no VARCHAR(64), "
                    + "payment_order_id VARCHAR(64), channel_code VARCHAR(64), notify_type VARCHAR(32), "
                    + "notify_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64), "
                    + "payment_order_id VARCHAR(64), event_type VARCHAR(64), event_topic VARCHAR(128), "
                    + "publish_status VARCHAR(32), retry_count INT, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_channel_config ("
                    + "channel_code VARCHAR(64), status VARCHAR(32))");
            statement.executeUpdate("INSERT INTO t_order(order_no, order_amount) VALUES "
                    + "('ORD-SPLIT', 100.00), ('ORD-OVERPAID', 100.00)");
            statement.executeUpdate("INSERT INTO t_payment_channel_config(channel_code, status) "
                    + "VALUES ('wx_h5', 'ENABLED')");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "status, status_type, created_at) VALUES "
                    + "('PAY-SPLIT-1', 'ORD-SPLIT', '张女士', 60.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW()), "
                    + "('PAY-SPLIT-2', 'ORD-SPLIT', '张女士', 40.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW()), "
                    + "('PAY-OVERPAID-1', 'ORD-OVERPAID', '李女士', 80.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW()), "
                    + "('PAY-OVERPAID-2', 'ORD-OVERPAID', '李女士', 30.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_notify_log("
                    + "notify_no, payment_order_id, channel_code, notify_type, notify_status, created_at) "
                    + "SELECT CONCAT('NTF-', payment_order_id), payment_order_id, channel_code, "
                    + "'SUCCESS', '已收口', NOW() FROM t_payment_order");
            statement.executeUpdate("INSERT INTO t_payment_event("
                    + "event_no, payment_order_id, event_type, event_topic, publish_status, retry_count, created_at) "
                    + "SELECT CONCAT('EVT-', payment_order_id), payment_order_id, 'PAYMENT_SUCCESS', "
                    + "'payment.success.v1', 'SUCCESS', 0, NOW() FROM t_payment_order");
            session.commit();
        }
    }

    public static String formatDate(Timestamp timestamp, String pattern) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(timestamp);
    }

    public static Timestamp parseDate(String value, String pattern) throws java.text.ParseException {
        return new Timestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                .parse(value).getTime());
    }
}
