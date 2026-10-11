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
import java.sql.PreparedStatement;
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
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentEventMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentEventMapper.xml",
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
    void shouldRejectAliasedPublishStatesBeforeTerminalWritesAndAudit() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'failed' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.markPublishSuccess("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPublishFailed("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPublishDeadLetter("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPublishUnknown("EVT-AUDIT-001"));

            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'PENDING ' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.markPublishSuccess("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPublishFailed("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPublishDeadLetter("EVT-AUDIT-001"));
            assertEquals(0, mapper.markPublishUnknown("EVT-AUDIT-001"));

            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'UNKNOWN ' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.markPublishSuccess("EVT-AUDIT-001"));
            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'SUCCESS ' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.updateLastRepublishAudit("EVT-AUDIT-001", "finance-ops"));
            session.clearCache();
            PaymentEventListItemDTO unchanged = mapper.findByEventNo("EVT-AUDIT-001");
            assertEquals("SUCCESS ", unchanged.getPublishStatus());
            assertEquals(1, unchanged.getRetryCount());
            assertNull(unchanged.getLastRepublishOperator());
        }
    }

    @Test
    void shouldRejectAliasedTypeStageAndStatusForPaymentSuccessHttpProgress() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'payment_success' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.markPaymentSuccessClearingAccepted("EVT-AUDIT-001"));
            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'PAYMENT_SUCCESS', "
                    + "payment_success_http_stage = 'pending' WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.markPaymentSuccessClearingAccepted("EVT-AUDIT-001"));
            statement.executeUpdate("UPDATE t_payment_event SET payment_success_http_stage = 'PENDING', "
                    + "publish_status = 'failed' WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.markPaymentSuccessClearingAccepted("EVT-AUDIT-001"));
            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'FAILED' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(1, mapper.markPaymentSuccessClearingAccepted("EVT-AUDIT-001"));
            statement.executeUpdate("UPDATE t_payment_event SET payment_success_http_stage = 'clearing_accepted' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.markPaymentSuccessHttpCompleted("EVT-AUDIT-001"));
            statement.executeUpdate("UPDATE t_payment_event SET payment_success_http_stage = 'CLEARING_ACCEPTED' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(1, mapper.markPaymentSuccessHttpCompleted("EVT-AUDIT-001"));
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
    void shouldExcludeAliasedStatusAndTypeFromRetryCandidatesAndDueCount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, "
                    + "downstream_system, event_topic, publish_status, publish_status_type, retry_count, "
                    + "event_payload, created_at) VALUES "
                    + "('EVT-ALIAS-FAILED-CASE', 'PAYMENT_SUCCESS', 'PAY-A1', 'ORD-A1', "
                    + "'accounting-system', 'payment.trade.succeeded.v1', 'failed', 'danger', 1, '{}', NOW()), "
                    + "('EVT-ALIAS-FAILED-SPACE', 'PAYMENT_SUCCESS', 'PAY-A2', 'ORD-A2', "
                    + "'accounting-system', 'payment.trade.succeeded.v1', 'FAILED ', 'danger', 1, '{}', NOW()), "
                    + "('EVT-ALIAS-PENDING-CASE', 'PAYMENT_SUCCESS', 'PAY-A3', 'ORD-A3', "
                    + "'accounting-system', 'payment.trade.succeeded.v1', 'pending', 'warn', 0, '{}', NOW()), "
                    + "('EVT-ALIAS-TYPE-CASE', 'payment_success', 'PAY-A4', 'ORD-A4', "
                    + "'accounting-system', 'payment.trade.succeeded.v1', 'PENDING', 'warn', 0, '{}', NOW()), "
                    + "('EVT-ALIAS-SUCCESS', 'PAYMENT_PENDING', 'PAY-A5', 'ORD-A5', "
                    + "'accounting-system', 'payment.pending.v1', 'success', 'success', 0, '{}', NOW()), "
                    + "('EVT-ALIAS-UNKNOWN', 'PAYMENT_PENDING', 'PAY-A6', 'ORD-A6', "
                    + "'accounting-system', 'payment.pending.v1', 'UNKNOWN ', 'warn', 0, '{}', NOW()), "
                    + "('EVT-ALIAS-DEAD', 'PAYMENT_PENDING', 'PAY-A7', 'ORD-A7', "
                    + "'accounting-system', 'payment.pending.v1', 'dead_letter', 'danger', 0, '{}', NOW())");

            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            assertEquals(Collections.singletonList("EVT-AUDIT-001"), mapper.findAllFailedEventNos(10));
            assertEquals(Collections.singletonList("EVT-AUDIT-001"), mapper.findDueFailedEventNos(10));
            PaymentEventOverviewDTO overview = mapper.findOverview(new PaymentEventQueryDTO());
            assertEquals(1, overview.getDueRetryEventCount());
            assertEquals(1L, overview.getFailedEventCount());
            assertEquals(1L, overview.getFailedOrDeadLetterCount());
            assertEquals(1L, overview.getPendingEventCount());
            assertEquals(0L, overview.getSuccessEventCount());
            assertEquals(0L, overview.getUnknownEventCount());
            assertEquals(0L, overview.getDeadLetterEventCount());
            assertEquals(Integer.valueOf(4), overview.getPaymentSuccessEventCount());

            PaymentEventQueryDTO failedQuery = new PaymentEventQueryDTO();
            failedQuery.setPublishStatus("FAILED_OR_DEAD_LETTER");
            assertEquals(1L, mapper.count(failedQuery));
            assertEquals(1, mapper.findAll(failedQuery).size());
            assertEquals(1, mapper.findAllForExport(failedQuery).size());
            assertEquals(1L, mapper.findOverview(failedQuery).getFailedOrDeadLetterCount());
            failedQuery.setPublishStatus("FAILED");
            assertEquals(1L, mapper.count(failedQuery));
            assertEquals(1, mapper.findAll(failedQuery).size());
            failedQuery.setPublishStatus("PENDING");
            assertEquals(1L, mapper.count(failedQuery));
            failedQuery.setEventType("PAYMENT_SUCCESS");
            assertEquals(0L, mapper.count(failedQuery));
        }
    }

    @Test
    void shouldReturnZeroEventCountersWhenEventTableIsEmpty() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_payment_event");
            PaymentEventOverviewDTO overview = session.getMapper(PaymentEventMapper.class)
                    .findOverview(new PaymentEventQueryDTO());

            assertEquals(0L, overview.getTotalEventCount());
            assertEquals(0L, overview.getSuccessEventCount());
            assertEquals(0L, overview.getPendingEventCount());
            assertEquals(0L, overview.getFailedEventCount());
            assertEquals(0L, overview.getDeadLetterEventCount());
            assertEquals(0L, overview.getUnknownEventCount());
            assertEquals(0L, overview.getFailedOrDeadLetterCount());
            assertEquals(0, overview.getDistinctDownstreamCount());
            assertEquals(0, overview.getDueRetryEventCount());
            assertEquals(0, overview.getPaymentSuccessEventCount());
            assertNull(overview.getLatestPublishedAt());
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

    @Test
    void shouldNotTreatRefundOrVirtualBusinessNumberAsStandardOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'REFUND_SUBMIT' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals("ORD-001", mapper.findDetail("EVT-AUDIT-001").getBizNo());
            assertNull(mapper.findDetail("EVT-AUDIT-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'payment_success' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            session.clearCache();
            assertNull(mapper.findDetail("EVT-AUDIT-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'PAYMENT_SUCCESS' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'VIRTUAL' "
                    + "WHERE payment_order_id = 'PAY-001'");
            session.clearCache();
            assertNull(mapper.findDetail("EVT-AUDIT-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'standard_order' "
                    + "WHERE payment_order_id = 'PAY-001'");
            session.clearCache();
            assertNull(mapper.findDetail("EVT-AUDIT-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'STANDARD_ORDER' "
                    + "WHERE payment_order_id = 'PAY-001'");
            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'OTHER-ORDER' "
                    + "WHERE payment_order_id = 'PAY-001'");
            session.clearCache();
            assertNull(mapper.findDetail("EVT-AUDIT-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-001' "
                    + "WHERE payment_order_id = 'PAY-001'");
            session.clearCache();
            assertEquals("ORD-001", mapper.findDetail("EVT-AUDIT-001").getStandardOrderNo());
        }
    }

    @Test
    void shouldNotReadPaymentEventThroughNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            assertNotNull(mapper.findByEventNoForUpdate("EVT-AUDIT-001"));
            assertNull(mapper.findByEventNoForUpdate("evt-audit-001"));
            assertNull(mapper.findByEventNo("evt-audit-001"));
            assertNull(mapper.findDetail("evt-audit-001"));
        }
    }

    @Test
    void shouldNotChangePaymentEventThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            String alias = "evt-audit-001";
            assertEquals(0, mapper.markPublishSuccess(alias));
            assertEquals(0, mapper.markPublishFailed(alias));
            assertEquals(0, mapper.markPublishDeadLetter(alias));
            assertEquals(0, mapper.markPublishUnknown(alias));
            assertEquals(0, mapper.markPaymentSuccessClearingAccepted(alias));
            statement.executeUpdate("UPDATE t_payment_event SET payment_success_http_stage = "
                    + "'CLEARING_ACCEPTED' WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.markPaymentSuccessHttpCompleted(alias));
            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'SUCCESS' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            assertEquals(0, mapper.updateLastRepublishAudit(alias, "finance-ops"));
            session.clearCache();
            PaymentEventListItemDTO unchanged = mapper.findByEventNo("EVT-AUDIT-001");
            assertEquals("SUCCESS", unchanged.getPublishStatus());
            assertEquals(1, unchanged.getRetryCount());
            assertEquals("CLEARING_ACCEPTED", unchanged.getPaymentSuccessHttpStage());
            assertNull(unchanged.getLastRepublishOperator());
        }
    }

    @Test
    void shouldProjectOwningPaymentIssueWithoutChangingEventDeliveryFacts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            PaymentEventQueryDTO query = new PaymentEventQueryDTO();
            query.setPaymentOrderId("PAY-001");
            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findDetail("EVT-AUDIT-001").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = "
                    + "TIMESTAMP '2026-09-27 09:00:01.124' WHERE payment_order_id = 'PAY-001'");
            session.clearCache();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findDetail("EVT-AUDIT-001").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-001'");
            session.clearCache();
            assertEquals("SUCCESS_TIME_MISSING", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_TIME_MISSING", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_TIME_MISSING", mapper.findDetail("EVT-AUDIT-001").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id = 'PAY-ORPHAN' "
                    + "WHERE event_no = 'EVT-AUDIT-001'");
            session.clearCache();
            query.setPaymentOrderId("PAY-ORPHAN");
            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findDetail("EVT-AUDIT-001").getSuccessFactIssue());
        }
    }

    @Test
    void shouldNotProjectAnotherPaymentOrOrderThroughEventIdentifierAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = "
                    + "TIMESTAMP '2026-09-27 09:00:01.124' WHERE payment_order_id = 'PAY-001'");
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            for (String[] identifiers : Arrays.asList(
                    new String[] {"pay-001", "ord-001"},
                    new String[] {"PAY-001 ", "ORD-001 "})) {
                try (PreparedStatement update = session.getConnection().prepareStatement(
                        "UPDATE t_payment_event SET payment_order_id = ?, biz_no = ? "
                                + "WHERE event_no = 'EVT-AUDIT-001'")) {
                    update.setString(1, identifiers[0]);
                    update.setString(2, identifiers[1]);
                    assertEquals(1, update.executeUpdate());
                }
                session.clearCache();
                PaymentEventQueryDTO query = new PaymentEventQueryDTO();
                assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());
                assertNull(mapper.findAllForExport(query).get(0).getSuccessFactIssue());
                PaymentEventDetailDTO detail = mapper.findDetail("EVT-AUDIT-001");
                assertNull(detail.getSuccessFactIssue());
                assertNull(detail.getStandardOrderNo());
            }

            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id = 'PAY-001', "
                    + "biz_no = 'ORD-001' WHERE event_no = 'EVT-AUDIT-001'");
            session.clearCache();
            PaymentEventDetailDTO exact = mapper.findDetail("EVT-AUDIT-001");
            assertEquals("SUCCESS_FACT_MISMATCH", exact.getSuccessFactIssue());
            assertEquals("ORD-001", exact.getStandardOrderNo());
        }
    }

    @Test
    void shouldNotCountCaseVariantPrepayOrAttemptAsOwningSuccessFacts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "VALUES ('pay-001', 'ORD-001', 1.00)");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(payment_order_id, channel_code, attempt_status, attempt_status_type, created_at, completed_at) "
                    + "VALUES ('pay-001', 'wx_h5', '成功', 'success', "
                    + "TIMESTAMP '2026-09-27 09:00:01', TIMESTAMP '2026-09-27 09:00:01.000')");
            PaymentEventMapper mapper = session.getMapper(PaymentEventMapper.class);
            PaymentEventQueryDTO query = new PaymentEventQueryDTO();
            query.setPaymentOrderId("PAY-001");

            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findDetail("EVT-AUDIT-001").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = "
                    + "TIMESTAMP '2026-09-27 09:00:01.124' WHERE payment_order_id = 'PAY-001' "
                    + "AND CAST(payment_order_id AS BINARY(256)) = CAST('PAY-001' AS BINARY(256))");
            session.clearCache();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findDetail("EVT-AUDIT-001").getSuccessFactIssue());
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
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "payment_order_id VARCHAR_IGNORECASE(64), order_no VARCHAR(64), "
                    + "amount DECIMAL(18,2) DEFAULT 1.00, "
                    + "status VARCHAR(32), status_type VARCHAR(32), "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(64), "
                    + "payment_method VARCHAR(32) DEFAULT '微信支付', "
                    + "created_at TIMESTAMP, success_at TIMESTAMP)");
            statement.execute("CREATE TABLE t_prepay_order (payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR(64), amount DECIMAL(18,2), "
                    + "source_kind VARCHAR(16) DEFAULT 'STANDARD_ORDER')");
            statement.execute("CREATE TABLE t_payment_attempt (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "payment_order_id VARCHAR_IGNORECASE(64), channel_code VARCHAR(64), "
                    + "payment_method VARCHAR(32) DEFAULT '微信支付', attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), created_at TIMESTAMP, completed_at TIMESTAMP)");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR_IGNORECASE(64) NOT NULL UNIQUE)");
            statement.executeUpdate("INSERT INTO t_order (order_no) VALUES ('ORD-001')");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "event_type VARCHAR_IGNORECASE(64) NOT NULL, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "biz_no VARCHAR_IGNORECASE(64) NOT NULL, downstream_system VARCHAR(256) NOT NULL, "
                    + "event_topic VARCHAR(128) NOT NULL, publish_status VARCHAR_IGNORECASE(32) NOT NULL, "
                    + "publish_status_type VARCHAR(32) NOT NULL, "
                    + "payment_success_http_stage VARCHAR_IGNORECASE(32) NOT NULL DEFAULT 'PENDING', "
                    + "retry_count INT NOT NULL, "
                    + "last_published_at DATETIME, next_retry_at DATETIME, "
                    + "last_republish_operator VARCHAR(64), last_republished_at DATETIME, "
                    + "event_payload VARCHAR(2048) NOT NULL, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, "
                    + "downstream_system, event_topic, publish_status, publish_status_type, retry_count, "
                    + "event_payload, created_at) VALUES ('EVT-AUDIT-001', 'PAYMENT_SUCCESS', 'PAY-001', "
                    + "'ORD-001', 'accounting-system', 'payment.trade.succeeded.v1', 'FAILED', 'danger', 1, "
                    + "'{}', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_order (payment_order_id, status, status_type, "
                    + "channel_code, channel_transaction_no, created_at, success_at) VALUES "
                    + "('PAY-001', 'SUCCESS', 'success', 'wx_h5', 'CH-001', "
                    + "TIMESTAMP '2026-09-27 09:00:00', TIMESTAMP '2026-09-27 09:00:01.123')");
            statement.executeUpdate("INSERT INTO t_payment_attempt (payment_order_id, channel_code, attempt_status, "
                    + "attempt_status_type, created_at, completed_at) VALUES "
                    + "('PAY-001', 'wx_h5', '成功', 'success', TIMESTAMP '2026-09-27 09:00:00', "
                    + "TIMESTAMP '2026-09-27 09:00:01.000')");
            statement.executeUpdate("UPDATE t_payment_order SET order_no = 'ORD-001' WHERE payment_order_id = 'PAY-001'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "SELECT payment_order_id, order_no, amount FROM t_payment_order");
            session.commit();
        }
    }

    private List<String> eventNos(List<PaymentEventListItemDTO> events) {
        return events.stream().map(PaymentEventListItemDTO::getEventNo).collect(Collectors.toList());
    }
}
