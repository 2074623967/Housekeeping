package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.PaymentEventDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventOverviewDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import java.io.InputStream;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
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
 * 支付事件成功重发审计的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentEventRepublishAuditMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_event_republish_audit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentEventMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentEventMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentEventMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldPersistAndReadLastSuccessfulRepublishOperator() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            assertEquals(0, mapper.updateLastRepublishAudit("EVT-AUDIT-001", "finance-ops"));
            assertEquals(1, mapper.markPublishSuccess("EVT-AUDIT-001"));
            assertEquals(1, mapper.updateLastRepublishAudit("EVT-AUDIT-001", "finance-ops"));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentEventListItemDTO event = session.getMapper(PaymentEventMapper.class)
                    .findByEventNo("EVT-AUDIT-001");

            assertEquals("finance-ops", event.getLastRepublishOperator());
            assertNotNull(event.getLastRepublishedAt());
        }
    }

    @Test
    void shouldKeepSuccessfulEventTerminalWhenLateFailureUpdatesArrive() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            assertEquals(1, mapper.markPublishSuccess("EVT-AUDIT-001"));
            PaymentEventListItemDTO successful = mapper.findByEventNo("EVT-AUDIT-001");

            assertEquals(0, mapper.markPublishDeadLetter("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPublishUnknown("EVT-AUDIT-001"));
            String failedUpdateSql = sqlSessionFactory.getConfiguration()
                    .getMappedStatement(PaymentEventMapper.class.getName() + ".markPublishFailed")
                    .getBoundSql(Collections.singletonMap("eventNo", "EVT-AUDIT-001"))
                    .getSql()
                    .replaceAll("\\s+", " ");
            assertTrue(failedUpdateSql.contains("AND publish_status IN ('PENDING', 'FAILED')"));

            PaymentEventListItemDTO unchanged = mapper.findByEventNo("EVT-AUDIT-001");
            assertEquals("SUCCESS", unchanged.getPublishStatus());
            assertEquals(successful.getRetryCount(), unchanged.getRetryCount());
        }
    }

    @Test
    void shouldAllowReviewedUnknownEventToResolveAsSuccess() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_event SET publish_status='UNKNOWN', "
                    + "publish_status_type='warn' WHERE event_no='EVT-AUDIT-001'");
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);

            assertEquals(1, mapper.markPublishSuccess("EVT-AUDIT-001"));
            assertEquals("SUCCESS", mapper.findByEventNo("EVT-AUDIT-001").getPublishStatus());
        }
    }

    @Test
    void shouldLimitRetryCandidatesToConfiguredBatchSize() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, "
                    + "downstream_system, event_topic, publish_status, publish_status_type, retry_count, "
                    + "event_payload, created_at) VALUES ('EVT-AUDIT-002', 'PAYMENT_SUCCESS', 'PAY-002', "
                    + "'ORD-002', 'accounting-system', 'payment.trade.succeeded.v1', 'FAILED', 'danger', 1, "
                    + "'{}', DATEADD('SECOND', 1, NOW()))");
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);

            assertEquals(Collections.singletonList("EVT-AUDIT-001"), mapper.findAllFailedEventNos(1));
            assertEquals(Arrays.asList("EVT-AUDIT-001", "EVT-AUDIT-002"), mapper.findDueFailedEventNos(2));
        }
    }

    @Test
    void shouldUseAutomaticRetryEligibilityForOverview() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, "
                    + "downstream_system, event_topic, publish_status, publish_status_type, retry_count, "
                    + "next_retry_at, event_payload, created_at) VALUES "
                    + "('EVT-PENDING-PAYMENT', 'PAYMENT_SUCCESS', 'PAY-002', 'ORD-002', "
                    + "'accounting-system', 'payment.trade.succeeded.v1', 'PENDING', 'warn', 0, NULL, '{}', NOW()), "
                    + "('EVT-PENDING-REFUND', 'REFUND_SUBMIT', 'PAY-003', 'RFA-003', "
                    + "'gateway-access', 'refund.submit.v1', 'PENDING', 'warn', 0, NULL, '{}', NOW()), "
                    + "('EVT-PENDING-UNSUPPORTED', 'PAYMENT_PENDING', 'PAY-004', 'ORD-004', "
                    + "'payment-core-ops', 'payment.pending.v1', 'PENDING', 'warn', 0, NULL, '{}', NOW()), "
                    + "('EVT-FAILED-FUTURE', 'PAYMENT_SUCCESS', 'PAY-005', 'ORD-005', "
                    + "'accounting-system', 'payment.trade.succeeded.v1', 'FAILED', 'danger', 1, "
                    + "DATEADD('DAY', 1, NOW()), '{}', NOW())");
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);

            PaymentEventQueryDTO query = new PaymentEventQueryDTO();
            PaymentEventOverviewDTO overview = mapper.findOverview(query);

            assertEquals(3, overview.getDueRetryEventCount());
            assertEquals(Arrays.asList(
                    "EVT-AUDIT-001", "EVT-PENDING-PAYMENT", "EVT-PENDING-REFUND"),
                    mapper.findDueFailedEventNos(10));
        }
    }

    @Test
    void shouldUseStableIdTieBreakerForListAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_event SET created_at='2026-09-12 10:00:00', "
                    + "retry_count=1, next_retry_at=NULL WHERE event_no='EVT-AUDIT-001'");
            statement.executeUpdate("INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, "
                    + "downstream_system, event_topic, publish_status, publish_status_type, retry_count, "
                    + "next_retry_at, event_payload, created_at) VALUES "
                    + "('EVT-AUDIT-002', 'PAYMENT_SUCCESS', 'PAY-002', 'ORD-002', 'accounting-system', "
                    + "'payment.trade.succeeded.v1', 'FAILED', 'danger', 1, NULL, '{}', '2026-09-12 10:00:00'), "
                    + "('EVT-AUDIT-003', 'PAYMENT_SUCCESS', 'PAY-003', 'ORD-003', 'accounting-system', "
                    + "'payment.trade.succeeded.v1', 'FAILED', 'danger', 1, NULL, '{}', '2026-09-12 10:00:00')");
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);

            PaymentEventQueryDTO query = new PaymentEventQueryDTO();
            query.setPageSize(1);
            query.setSortField("createdAt");
            query.setSortOrder("desc");
            assertEquals("EVT-AUDIT-003", mapper.findAll(query).get(0).getEventNo());
            query.setPageNo(2);
            assertEquals("EVT-AUDIT-002", mapper.findAll(query).get(0).getEventNo());
            query.setPageNo(3);
            assertEquals("EVT-AUDIT-001", mapper.findAll(query).get(0).getEventNo());

            query.setSortField("retryCount");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList("EVT-AUDIT-001", "EVT-AUDIT-002", "EVT-AUDIT-003"),
                    eventNos(mapper.findAllForExport(query)));
            query.setSortField("nextRetryAt");
            query.setSortOrder("desc");
            assertEquals(Arrays.asList("EVT-AUDIT-003", "EVT-AUDIT-002", "EVT-AUDIT-001"),
                    eventNos(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldPersistPaymentSuccessHttpStagesInOrder() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);

            assertEquals("PENDING", mapper.findByEventNoForUpdate("EVT-AUDIT-001")
                    .getPaymentSuccessHttpStage());
            assertEquals(1, mapper.markPaymentSuccessClearingAccepted("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPaymentSuccessClearingAccepted("EVT-AUDIT-001"));
            assertEquals(1, mapper.markPaymentSuccessHttpCompleted("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPaymentSuccessHttpCompleted("EVT-AUDIT-001"));
            assertEquals("COMPLETED", mapper.findByEventNo("EVT-AUDIT-001")
                    .getPaymentSuccessHttpStage());
        }
    }

    @Test
    void shouldFindPaymentEventDetailByUniqueEventNo() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);

            PaymentEventDetailDTO detail = mapper.findDetail("EVT-AUDIT-001");

            assertEquals("EVT-AUDIT-001", detail.getEventNo());
            assertEquals("PAYMENT_SUCCESS", detail.getEventType());
            assertEquals("PAY-001", detail.getPaymentOrderId());
            assertEquals("ORD-001", detail.getBizNo());
            assertEquals("ORD-001", detail.getStandardOrderNo());
            assertEquals("payment.trade.succeeded.v1", detail.getEventTopic());
            assertEquals("accounting-system", detail.getDownstreamSystem());
            assertEquals("FAILED", detail.getPublishStatus());
            assertEquals("danger", detail.getPublishStatusType());
            assertEquals("PENDING", detail.getPaymentSuccessHttpStage());
            assertEquals(1, detail.getRetryCount());
            assertEquals("{}", detail.getEventPayload());
            assertNotNull(detail.getCreatedAt());
            assertNull(mapper.findDetail("EVT-MISSING"));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE)");
            statement.executeUpdate("INSERT INTO t_order (order_no) VALUES ('ORD-001')");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64) NOT NULL, "
                    + "event_type VARCHAR(64) NOT NULL, payment_order_id VARCHAR(64) NOT NULL, "
                    + "biz_no VARCHAR(64) NOT NULL, downstream_system VARCHAR(256) NOT NULL, "
                    + "event_topic VARCHAR(128) NOT NULL, publish_status VARCHAR(32) NOT NULL, "
                    + "publish_status_type VARCHAR(32) NOT NULL, "
                    + "payment_success_http_stage VARCHAR(32) NOT NULL DEFAULT 'PENDING', "
                    + "retry_count INT NOT NULL, "
                    + "last_published_at DATETIME, next_retry_at DATETIME, "
                    + "last_republish_operator VARCHAR(64), last_republished_at DATETIME, "
                    + "event_payload VARCHAR(2048) NOT NULL, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, "
                    + "downstream_system, event_topic, publish_status, publish_status_type, retry_count, "
                    + "event_payload, created_at) VALUES ('EVT-AUDIT-001', 'PAYMENT_SUCCESS', 'PAY-001', "
                    + "'ORD-001', 'accounting-system', 'payment.trade.succeeded.v1', 'FAILED', 'danger', 1, "
                    + "'{}', NOW())");
            session.commit();
        }
    }

    private List<String> eventNos(List<PaymentEventListItemDTO> events) {
        return events.stream().map(PaymentEventListItemDTO::getEventNo).collect(Collectors.toList());
    }
}
