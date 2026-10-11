package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundAttemptItemDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundRetrySourceDTO;
import com.abc123.hsp.service.impl.LocalRefundChannelSubmitServiceImpl;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
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
 * 退款渠道下发 outbox 正式 Mapper 契约测试。
 */
class RefundSubmitOutboxMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @Test
    void shouldExposeUnsubmittedRefundAttemptWithoutSubmittedTime() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundAttemptItemDTO pending = mapper.findAttempts("REF-TRACE").get(0);
            assertEquals("PENDING", pending.getAttemptStatus());
            assertEquals("-", pending.getSubmittedAt());
            assertEquals("-", pending.getCompletedAt());

            assertEquals(1, mapper.markRefundAttemptFailed("RFA-TRACE"));
            session.clearCache();
            RefundAttemptItemDTO failed = mapper.findAttempts("REF-TRACE").get(0);
            assertEquals("FAILED", failed.getAttemptStatus());
            assertEquals("-", failed.getSubmittedAt());
            assertEquals("-", failed.getCompletedAt());
            session.rollback();
        }
    }

    @Test
    void shouldNotReadOrFinalizeRefundThroughOrderIdAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertNotNull(mapper.findCallbackSourceForUpdate("REF-TRACE"));
            assertNull(mapper.findCallbackSourceForUpdate("ref-trace"));
            assertNull(mapper.findCallbackSourceByRefundOrderId("ref-trace"));
            assertNull(mapper.findRetrySourceForUpdate("ref-trace"));
            assertNull(mapper.findChannelSubmitRequestByRefundOrderId("ref-trace"));
            assertEquals(0, mapper.findAttempts("ref-trace").size());
            assertEquals(0, mapper.updateRefundStatus(
                    "ref-trace", "PROCESSING", "FAIL", "danger", false));
            session.clearCache();
            assertEquals("PROCESSING", mapper.findCallbackSourceForUpdate("REF-TRACE").getStatus());
        }
    }

    @Test
    void shouldNotRestartFailedRefundThroughOrderIdAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_order SET status = 'FAIL' "
                    + "WHERE refund_order_id = 'REF-TRACE'");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(0, mapper.restartFailedRefund("ref-trace"));
            session.clearCache();
            assertEquals("FAIL", mapper.findCallbackSourceForUpdate("REF-TRACE").getStatus());
            assertEquals(1, mapper.restartFailedRefund("REF-TRACE"));
        }
    }

    @Test
    void shouldNotReadOrAdvanceRefundAttemptThroughAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertNotNull(mapper.findChannelSubmitRequestByAttemptNo("RFA-TRACE"));
            assertNull(mapper.findChannelSubmitRequestByAttemptNo("rfa-trace"));
            assertEquals(0, mapper.bindRefundAttemptAccepted("rfa-trace", "RCH-ALIAS"));
            assertEquals(0, mapper.markRefundAttemptFailed("rfa-trace"));
            assertEquals(0, mapper.markRefundAttemptUnknown("rfa-trace"));

            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-TRACE", "RCH-TRACE"));
            assertEquals(0, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "rfa-trace", "wx_h5", "RCH-TRACE"));
            statement.executeUpdate("UPDATE t_refund_order SET status = 'SUCCESS', "
                    + "terminal_at = NOW() WHERE refund_order_id = 'REF-TRACE'");
            assertEquals(0, mapper.finalizeRefundAttempt("rfa-trace", "SUCCESS", "success"));
            assertEquals(1, mapper.finalizeRefundAttempt("RFA-TRACE", "SUCCESS", "success"));
        }
    }

    @Test
    void shouldNotJoinRefundSourcesThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertNotNull(mapper.findDetailByRefundOrderId("REF-TRACE"));
            assertNotNull(mapper.findChannelSubmitRequestByRefundOrderId("REF-TRACE"));
            statement.executeUpdate("UPDATE t_refund_order SET payment_order_id = 'pay-trace' "
                    + "WHERE refund_order_id = 'REF-TRACE'");
            session.clearCache();
            assertNotNull(mapper.findByRefundOrderId("REF-TRACE"));
            assertNull(mapper.findDetailByRefundOrderId("REF-TRACE"));
            assertNull(mapper.findChannelSubmitRequestByRefundOrderId("REF-TRACE"));
            assertNull(mapper.findCallbackSourceByRefundOrderId("REF-TRACE"));
            assertNull(mapper.findCallbackSourceForUpdate("REF-TRACE"));
        }
    }

    @Test
    void shouldNotBorrowAttemptThroughRefundNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_attempt SET refund_order_id = 'ref-trace' "
                    + "WHERE refund_attempt_no = 'RFA-TRACE'");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertNull(mapper.findChannelSubmitRequestByAttemptNo("RFA-TRACE"));
            assertNull(mapper.findRetrySourceForUpdate("REF-TRACE").getLatestAttemptStatus());
            assertNull(mapper.findCallbackSourceByRefundOrderId("REF-TRACE").getRefundAttemptNo());
            assertNull(mapper.findCallbackSourceForUpdate("REF-TRACE").getRefundAttemptNo());
        }
    }

    @Test
    void shouldNotBorrowRefundSubmitEventThroughAttemptNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, event_type, payment_order_id, biz_no, downstream_system, event_topic, "
                    + "publish_status, publish_status_type, retry_count, event_payload, created_at) VALUES "
                    + "('EVT-ALIEN-BIZ', 'REFUND_SUBMIT', 'PAY-TRACE', 'rfa-trace', 'gateway-access', "
                    + "'payment.refund.submitted.v1', 'SUCCESS', 'success', 0, '{}', NOW())");
            assertNoRefundSubmitEventStatus(session.getMapper(RefundMapper.class));
        }
    }

    @Test
    void shouldNotBorrowRefundSubmitEventFromAnotherPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, event_type, payment_order_id, biz_no, downstream_system, event_topic, "
                    + "publish_status, publish_status_type, retry_count, event_payload, created_at) VALUES "
                    + "('EVT-ALIEN-PAY', 'REFUND_SUBMIT', 'PAY-UNKNOWN', 'RFA-TRACE', 'gateway-access', "
                    + "'payment.refund.submitted.v1', 'SUCCESS', 'success', 0, '{}', NOW())");
            assertNoRefundSubmitEventStatus(session.getMapper(RefundMapper.class));
        }
    }

    @Test
    void shouldNotBorrowRefundSubmitEventThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, event_type, payment_order_id, biz_no, downstream_system, event_topic, "
                    + "publish_status, publish_status_type, retry_count, event_payload, created_at) VALUES "
                    + "('EVT-ALIAS-PAY', 'REFUND_SUBMIT', 'pay-trace', 'RFA-TRACE', 'gateway-access', "
                    + "'payment.refund.submitted.v1', 'SUCCESS', 'success', 0, '{}', NOW())");
            assertNoRefundSubmitEventStatus(session.getMapper(RefundMapper.class));
        }
    }

    @Test
    void shouldNotBorrowRefundSubmitEventThroughTypeAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, event_type, payment_order_id, biz_no, downstream_system, event_topic, "
                    + "publish_status, publish_status_type, retry_count, event_payload, created_at) VALUES "
                    + "('EVT-ALIAS-TYPE', 'refund_submit', 'PAY-TRACE', 'RFA-TRACE', 'gateway-access', "
                    + "'payment.refund.submitted.v1', 'SUCCESS', 'success', 0, '{}', NOW())");
            assertNoRefundSubmitEventStatus(session.getMapper(RefundMapper.class));
        }
    }

    @Test
    void shouldNotBorrowRefundSubmitEventFromAnotherDownstream() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, event_type, payment_order_id, biz_no, downstream_system, event_topic, "
                    + "publish_status, publish_status_type, retry_count, event_payload, created_at) VALUES "
                    + "('EVT-ALIEN-DOWNSTREAM', 'REFUND_SUBMIT', 'PAY-TRACE', 'RFA-TRACE', 'other-system', "
                    + "'payment.refund.submitted.v1', 'SUCCESS', 'success', 0, '{}', NOW())");
            assertNoRefundSubmitEventStatus(session.getMapper(RefundMapper.class));
        }
    }

    @Test
    void shouldNotBorrowRefundSubmitEventFromAnotherTopic() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event "
                    + "(event_no, event_type, payment_order_id, biz_no, downstream_system, event_topic, "
                    + "publish_status, publish_status_type, retry_count, event_payload, created_at) VALUES "
                    + "('EVT-ALIEN-TOPIC', 'REFUND_SUBMIT', 'PAY-TRACE', 'RFA-TRACE', 'gateway-access', "
                    + "'payment.refund.other.v1', 'SUCCESS', 'success', 0, '{}', NOW())");
            assertNoRefundSubmitEventStatus(session.getMapper(RefundMapper.class));
        }
    }

    private void assertNoRefundSubmitEventStatus(RefundMapper mapper) {
        assertNull(mapper.findCallbackSourceByRefundOrderId("REF-TRACE").getSubmitEventStatus());
        assertNull(mapper.findCallbackSourceForUpdate("REF-TRACE").getSubmitEventStatus());
    }

    @Test
    void shouldNotFindRefundAttemptThroughChannelAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_refund_no = 'RCH-TRACE' "
                    + "WHERE refund_attempt_no = 'RFA-TRACE'");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals("RFA-TRACE", mapper.findAttemptNoByChannelRefundNo("wx_h5", "RCH-TRACE"));
            assertNull(mapper.findAttemptNoByChannelRefundNo("WX_H5", "RCH-TRACE"));
            assertNull(mapper.findAttemptNoByChannelRefundNo("wx_h5", "rch-trace"));
        }
    }

    @Test
    void shouldPreserveFractionalTerminalTimesInTaskRetrySnapshot() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_order SET status='FAIL', status_type='danger', "
                    + "channel_code='wx_h5', channel_refund_no='RCH-TRACE', "
                    + "applied_at='2026-10-02 09:59:59.900', "
                    + "channel_submitted_at='2026-10-02 10:00:00.100', "
                    + "terminal_at='2026-10-02 10:00:00.123' WHERE refund_order_id='REF-TRACE'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status='FAIL', "
                    + "attempt_status_type='danger', channel_refund_no='RCH-TRACE', "
                    + "completed_at='2026-10-02 10:00:00.124' WHERE refund_attempt_no='RFA-TRACE'");

            RefundRetrySourceDTO source = session.getMapper(RefundMapper.class)
                    .findRetrySourceForUpdate("REF-TRACE");

            assertEquals("2026-10-02 10:00:00.123", source.getTerminalAt());
            assertEquals("2026-10-02 10:00:00.124", source.getLatestAttemptCompletedAt());
            session.rollback();
        }
    }

    @Test
    void shouldExposeConsistentTerminalTimesAfterSuccessfulRefundCallback() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-TRACE", "RCH-TRACE"));
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-TRACE"));
            assertEquals(1, mapper.updateRefundStatus(
                    "REF-TRACE", "PROCESSING", "SUCCESS", "success", true));
            assertEquals(1, mapper.finalizeRefundAttempt("RFA-TRACE", "SUCCESS", "success"));

            RefundCallbackSourceDTO source = mapper.findCallbackSourceByRefundOrderId("REF-TRACE");
            LocalDateTime terminalAt = LocalDateTime.parse(source.getTerminalAt().replace(' ', 'T'));
            LocalDateTime completedAt = LocalDateTime.parse(source.getAttemptCompletedAt().replace(' ', 'T'));
            assertEquals(terminalAt, completedAt);
            session.rollback();
        }
    }

    @Test
    void shouldRequireTerminalRefundBeforeFinalizingFailedAttempt() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-FAILED"));
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-FAILED"));
            assertEquals(0, mapper.finalizeRefundAttempt("RFA-UNKNOWN", "FAIL", "danger"));
            assertEquals(1, mapper.updateRefundStatus(
                    "REF-UNKNOWN", "PROCESSING", "FAIL", "danger", false));
            assertEquals(1, mapper.finalizeRefundAttempt("RFA-UNKNOWN", "FAIL", "danger"));

            RefundCallbackSourceDTO source = mapper.findCallbackSourceByRefundOrderId("REF-UNKNOWN");
            assertEquals("FAIL", source.getStatus());
            assertEquals("FAIL", source.getAttemptStatus());
            assertEquals(source.getTerminalAt(), source.getAttemptCompletedAt());
            assertNull(source.getSuccessAt());
            session.rollback();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:refund_submit_outbox;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RefundMapper.class);
        configuration.addMapper(PaymentEventMapper.class);
        configuration.addMapper(PaymentMapper.class);
        parseMapper(configuration, "mapper/RefundMapper.xml");
        parseMapper(configuration, "mapper/PaymentEventMapper.xml");
        parseMapper(configuration, "mapper/PaymentMapper.xml");
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createEventTable();
        createRefundTable();
    }

    @Test
    void shouldReuseStableRefundEventAndExposePendingEventForAutomaticDispatch() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            refundMapper.upsertRefundSubmitEvent("EVTR-STABLE", "RFA-STABLE", "PAY-001");
            session.commit();
        }
        markEventFailed();
        insertUnsupportedPendingEvent();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            refundMapper.upsertRefundSubmitEvent("EVTR-STABLE", "RFA-STABLE", "PAY-001");

            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            List<String> dueEventNos = eventMapper.findDueFailedEventNos(100);
            PaymentEventListItemDTO event = eventMapper.findByEventNoForUpdate("EVTR-STABLE");

            assertEquals(1, countRefundEventRows(session));
            assertEquals(1, dueEventNos.size());
            assertEquals("EVTR-STABLE", dueEventNos.get(0));
            assertEquals("REFUND_SUBMIT", event.getEventType());
            assertEquals("RFA-STABLE", event.getBizNo());
            assertEquals("gateway-access", event.getDownstreamSystem());
            assertEquals("payment.refund.submitted.v1", event.getEventTopic());
            assertEquals("PENDING", event.getPublishStatus());
            assertEquals(0, event.getRetryCount());
            session.rollback();
        }
    }

    @Test
    void shouldNotReactivateAnotherRefundSubmitEventThroughUniqueKeyAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1, mapper.upsertRefundSubmitEvent("EVTR-COLLIDE", "RFA-TRACE", "PAY-TRACE"));
            assertEquals(true, mapper.isMatchingPendingRefundSubmitEvent(
                    "EVTR-COLLIDE", "RFA-TRACE", "PAY-TRACE"));
            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'FAILED', "
                    + "retry_count = 4, event_payload = '旧退款事件' WHERE event_no = 'EVTR-COLLIDE'");

            mapper.upsertRefundSubmitEvent("evtr-collide", "RFA-TRACE", "PAY-TRACE");
            assertEquals(false, mapper.isMatchingPendingRefundSubmitEvent(
                    "evtr-collide", "RFA-TRACE", "PAY-TRACE"));
            mapper.upsertRefundSubmitEvent("EVTR-COLLIDE", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(false, mapper.isMatchingPendingRefundSubmitEvent(
                    "EVTR-COLLIDE", "RFA-UNKNOWN", "PAY-UNKNOWN"));
            try (ResultSet row = statement.executeQuery("SELECT event_no, payment_order_id, biz_no, "
                    + "publish_status, retry_count, event_payload FROM t_payment_event "
                    + "WHERE event_no = 'EVTR-COLLIDE'")) {
                assertEquals(true, row.next());
                assertEquals("EVTR-COLLIDE", row.getString("event_no"));
                assertEquals("PAY-TRACE", row.getString("payment_order_id"));
                assertEquals("RFA-TRACE", row.getString("biz_no"));
                assertEquals("FAILED", row.getString("publish_status"));
                assertEquals(4, row.getInt("retry_count"));
                assertEquals("旧退款事件", row.getString("event_payload"));
            }
        }
    }

    @Test
    void shouldBindAcceptedRefundTraceOnlyOnce() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);

            assertEquals(1, refundMapper.bindRefundAttemptAccepted("RFA-TRACE", "RCH-TRACE"));
            int firstBinding = refundMapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-TRACE");
            int conflictingBinding = refundMapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-OTHER");

            assertEquals(1, firstBinding);
            assertEquals(0, conflictingBinding);
            try (Statement statement = session.getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT channel_code, channel_refund_no, channel_submitted_at "
                                 + "FROM t_refund_order WHERE refund_order_id='REF-TRACE'")) {
                resultSet.next();
                assertEquals("wx_h5", resultSet.getString("channel_code"));
                assertEquals("RCH-TRACE", resultSet.getString("channel_refund_no"));
                org.junit.jupiter.api.Assertions.assertNotNull(
                        resultSet.getTimestamp("channel_submitted_at"));
            }
            session.rollback();
        }
    }

    @Test
    void shouldNotBindAcceptedRefundOverExistingOrderChannelTrace() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-TRACE", "RCH-TRACE"));

            statement.executeUpdate("UPDATE t_refund_order SET channel_code = 'alipay_h5' "
                    + "WHERE refund_order_id = 'REF-TRACE'");
            assertEquals(0, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-TRACE"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_code = NULL, "
                    + "channel_submitted_at = NOW() WHERE refund_order_id = 'REF-TRACE'");
            assertEquals(0, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-TRACE"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_submitted_at = NULL "
                    + "WHERE refund_order_id = 'REF-TRACE'");
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-TRACE"));
            session.rollback();
        }
    }

    @Test
    void shouldNotBindAcceptedRefundWithStatusAliasOrMissingAttemptTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-TRACE", "RCH-STATUS-GUARD"));

            statement.executeUpdate("UPDATE t_refund_order SET status = 'processing' "
                    + "WHERE refund_order_id = 'REF-TRACE'");
            assertEquals(0, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-STATUS-GUARD"));
            statement.executeUpdate("UPDATE t_refund_order SET status = 'PROCESSING' "
                    + "WHERE refund_order_id = 'REF-TRACE'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'accepted' "
                    + "WHERE refund_attempt_no = 'RFA-TRACE'");
            assertEquals(0, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-STATUS-GUARD"));
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'ACCEPTED', "
                    + "submitted_at = NULL WHERE refund_attempt_no = 'RFA-TRACE'");
            assertEquals(0, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-STATUS-GUARD"));
            statement.executeUpdate("UPDATE t_refund_attempt SET submitted_at = NOW() "
                    + "WHERE refund_attempt_no = 'RFA-TRACE'");
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-STATUS-GUARD"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecordUnknownChannelOverExistingOrderChannelTrace() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-TRACE-GUARD", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));

            statement.executeUpdate("UPDATE t_refund_order SET channel_code = 'alipay_h5' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-TRACE-GUARD", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_code = NULL, "
                    + "channel_submitted_at = NOW() WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-TRACE-GUARD", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_submitted_at = NULL "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(1, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-TRACE-GUARD", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecordUnknownChannelWithStatusAliasOrMissingAttemptTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-UNKNOWN-STATUS-GUARD", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));

            statement.executeUpdate("UPDATE t_refund_order SET status = 'processing' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-UNKNOWN-STATUS-GUARD", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            statement.executeUpdate("UPDATE t_refund_order SET status = 'PROCESSING' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'unknown' "
                    + "WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-UNKNOWN-STATUS-GUARD", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'UNKNOWN', "
                    + "submitted_at = NULL WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-UNKNOWN-STATUS-GUARD", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            statement.executeUpdate("UPDATE t_refund_attempt SET submitted_at = NOW() "
                    + "WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            assertEquals(1, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-UNKNOWN-STATUS-GUARD", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            session.rollback();
        }
    }

    @Test
    void shouldExcludeUnknownRefundSubmitFromEveryRetryCandidate() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            refundMapper.upsertRefundSubmitEvent("EVTR-UNKNOWN", "RFA-UNKNOWN-EVENT", "PAY-001");

            assertEquals(1, eventMapper.markPublishUnknown("EVTR-UNKNOWN"));
            PaymentEventListItemDTO event = eventMapper.findByEventNoForUpdate("EVTR-UNKNOWN");

            assertEquals("UNKNOWN", event.getPublishStatus());
            assertEquals(1, event.getRetryCount());
            try (Statement statement = session.getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT publish_status_type, next_retry_at FROM t_payment_event "
                                 + "WHERE event_no='EVTR-UNKNOWN'")) {
                resultSet.next();
                assertEquals("warn", resultSet.getString("publish_status_type"));
                assertNull(resultSet.getTimestamp("next_retry_at"));
            }
            assertFalse(eventMapper.findDueFailedEventNos(100).contains("EVTR-UNKNOWN"));
            assertFalse(eventMapper.findAllFailedEventNos(100).contains("EVTR-UNKNOWN"));
            try (Statement statement = session.getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT COUNT(*) FROM t_payment_event WHERE publish_status='UNKNOWN'")) {
                resultSet.next();
                assertEquals(1, resultSet.getInt(1));
            }
            session.rollback();
        }
    }

    @Test
    void shouldRecoverAcceptedTraceOnlyForUnknownRefundSubmit() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            refundMapper.upsertRefundSubmitEvent("EVTR-RECOVER", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVTR-RECOVER"));
            assertEquals(1, refundMapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(0, refundMapper.finalizeRefundAttempt(
                    "RFA-UNKNOWN", "SUCCESS", "success"));
            assertEquals(1, refundMapper.recordUnknownRefundSubmitChannel(
                    "EVTR-RECOVER", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));

            RefundCallbackSourceDTO before =
                    refundMapper.findCallbackSourceByRefundOrderId("REF-UNKNOWN");
            assertEquals("wx_h5", before.getChannelCode());
            assertNull(before.getChannelRefundNo());
            assertEquals("UNKNOWN", before.getSubmitEventStatus());
            assertEquals(0, refundMapper.bindUnknownRefundChannelAccepted(
                    "REF-TRACE", "RFA-UNKNOWN", "wx_h5", "RCH-NOT-ALLOWED"));
            assertEquals(1, refundMapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-RECOVERED"));
            assertEquals(1, refundMapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-RECOVERED"));
            assertEquals(1, refundMapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));

            RefundCallbackSourceDTO after = refundMapper.findCallbackSourceForUpdate("REF-UNKNOWN");
            assertEquals("RCH-RECOVERED", after.getChannelRefundNo());
            assertEquals("SUCCESS", after.getSubmitEventStatus());
            assertEquals("SUCCESS", eventMapper.findByEventNoForUpdate("EVTR-RECOVER").getPublishStatus());
            session.rollback();
        }
    }

    @Test
    void shouldNotRecoverUnknownAcceptanceOverConflictingOrderChannelTrace() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-RECOVERY-GUARD", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-RECOVERY-GUARD"));
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-RECOVERY-GUARD"));

            statement.executeUpdate("UPDATE t_refund_order SET channel_code = 'alipay_h5' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-RECOVERY-GUARD"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_code = 'wx_h5', "
                    + "channel_submitted_at = NOW() WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-RECOVERY-GUARD"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_submitted_at = NULL "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(1, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-RECOVERY-GUARD"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecoverUnknownAcceptanceThroughRefundStatusAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-REFUND-STATE-ALIAS", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-REFUND-STATE-ALIAS"));
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-STATE-ALIAS"));

            statement.executeUpdate("UPDATE t_refund_order SET status = 'processing' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-STATE-ALIAS"));
            statement.executeUpdate("UPDATE t_refund_order SET status = 'PROCESSING' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'accepted' "
                    + "WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-STATE-ALIAS"));
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'ACCEPTED' "
                    + "WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            assertEquals(1, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-STATE-ALIAS"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecordUnknownChannelWithoutNamedRefundSubmitEvent() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));

            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-MISSING", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            assertNull(mapper.findCallbackSourceByRefundOrderId("REF-UNKNOWN").getOrderChannelCode());
            session.rollback();
        }
    }

    @Test
    void shouldNotRecordUnknownChannelUsingAlienEventNumber() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-CORRECT-UNKNOWN", "RFA-UNKNOWN", "PAY-UNKNOWN");
            mapper.upsertRefundSubmitEvent("EVT-ALIEN-UNKNOWN", "RFA-UNKNOWN", "PAY-TRACE");
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));

            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-ALIEN-UNKNOWN", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            assertEquals(1, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-CORRECT-UNKNOWN", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecordUnknownChannelThroughEventNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-CASE-UNKNOWN", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));

            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "evt-case-unknown", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            assertEquals(1, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-CASE-UNKNOWN", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecordUnknownChannelForWrongEventDestinationOrState() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-DRIFT-UNKNOWN", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));

            statement.executeUpdate("UPDATE t_payment_event SET downstream_system = 'other-system' "
                    + "WHERE event_no = 'EVT-DRIFT-UNKNOWN'");
            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-DRIFT-UNKNOWN", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            statement.executeUpdate("UPDATE t_payment_event SET downstream_system = 'gateway-access', "
                    + "event_topic = 'payment.refund.other.v1' WHERE event_no = 'EVT-DRIFT-UNKNOWN'");
            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-DRIFT-UNKNOWN", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            statement.executeUpdate("UPDATE t_payment_event SET event_topic = 'payment.refund.submitted.v1' "
                    + "WHERE event_no = 'EVT-DRIFT-UNKNOWN'");
            assertEquals(1, eventMapper.markPublishSuccess("EVT-DRIFT-UNKNOWN"));
            assertEquals(0, mapper.recordUnknownRefundSubmitChannel(
                    "EVT-DRIFT-UNKNOWN", "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5"));
            session.rollback();
        }
    }

    @Test
    void shouldRequirePersistedChannelAcceptanceForSuccessfulRefundEvent() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-ACCEPTED-FACT", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishSuccess("EVT-ACCEPTED-FACT"));
            assertFalse(mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-ACCEPTED-FACT", "RFA-UNKNOWN"));

            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-ACCEPTED-FACT"));
            assertFalse(mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-ACCEPTED-FACT", "RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-ACCEPTED-FACT"));
            assertEquals(true, mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-ACCEPTED-FACT", "RFA-UNKNOWN"));

            statement.executeUpdate("UPDATE t_refund_order SET channel_refund_no = 'RCH-OTHER' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            session.clearCache();
            assertFalse(mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-ACCEPTED-FACT", "RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldNotProjectAttemptChannelAsRefundOrderAcceptedChannel() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-ATTEMPT-ONLY"));

            RefundChannelSubmitRequestDTO request = mapper.findChannelSubmitRequestByAttemptNo("RFA-UNKNOWN");
            assertEquals("wx_h5", request.getChannelCode());
            assertEquals("RCH-ATTEMPT-ONLY", request.getChannelRefundNo());
            assertNull(request.getSubmittedChannelCode());
            assertNull(request.getOrderChannelRefundNo());
            assertFalse(Boolean.TRUE.equals(request.getChannelSubmitRecorded()));

            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-ATTEMPT-ONLY"));
            RefundChannelSubmitRequestDTO accepted = mapper.findChannelSubmitRequestByAttemptNo("RFA-UNKNOWN");
            assertEquals("wx_h5", accepted.getSubmittedChannelCode());
            assertEquals("RCH-ATTEMPT-ONLY", accepted.getOrderChannelRefundNo());
            assertEquals(true, accepted.getChannelSubmitRecorded());
            session.rollback();
        }
    }

    @Test
    void shouldExposeRefundOrderAcceptanceWithoutAttemptTrace() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_order SET channel_code = 'wx_h5', "
                    + "channel_refund_no = 'RCH-ORDER-ONLY', channel_submitted_at = NOW() "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");

            RefundChannelSubmitRequestDTO request = session.getMapper(RefundMapper.class)
                    .findChannelSubmitRequestByAttemptNo("RFA-UNKNOWN");
            assertNull(request.getChannelRefundNo());
            assertEquals("wx_h5", request.getSubmittedChannelCode());
            assertEquals("RCH-ORDER-ONLY", request.getOrderChannelRefundNo());
            assertEquals(true, request.getChannelSubmitRecorded());
            session.rollback();
        }
    }

    @Test
    void shouldRejectSuccessfulRefundEventWithOtherPaymentOrStatusAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-PAYMENT-FACT"));
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-PAYMENT-FACT"));
            mapper.upsertRefundSubmitEvent("EVT-OTHER-PAY-SUCCESS", "RFA-UNKNOWN", "PAY-TRACE");
            mapper.upsertRefundSubmitEvent("EVT-PAY-ALIAS-SUCCESS", "RFA-UNKNOWN", "pay-unknown");
            mapper.upsertRefundSubmitEvent("EVT-STATUS-ALIAS-SUCCESS", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishSuccess("EVT-OTHER-PAY-SUCCESS"));
            assertEquals(1, eventMapper.markPublishSuccess("EVT-PAY-ALIAS-SUCCESS"));
            assertEquals(1, eventMapper.markPublishSuccess("EVT-STATUS-ALIAS-SUCCESS"));
            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'success' "
                    + "WHERE event_no = 'EVT-STATUS-ALIAS-SUCCESS'");

            assertFalse(mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-OTHER-PAY-SUCCESS", "RFA-UNKNOWN"));
            assertFalse(mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-PAY-ALIAS-SUCCESS", "RFA-UNKNOWN"));
            assertFalse(mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-STATUS-ALIAS-SUCCESS", "RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldPreserveTrustedSuccessfulEventAfterRefundTerminalCallback() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-TERMINAL-FACT", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-TERMINAL-FACT"));
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-TERMINAL-FACT"));
            assertEquals(1, eventMapper.markPublishSuccess("EVT-TERMINAL-FACT"));
            assertEquals(1, mapper.updateRefundStatus(
                    "REF-UNKNOWN", "PROCESSING", "SUCCESS", "success", true));
            assertEquals(1, mapper.finalizeRefundAttempt("RFA-UNKNOWN", "SUCCESS", "success"));

            assertEquals(true, mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-TERMINAL-FACT", "RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldNotTrustSuccessfulRefundEventWithRefundStatusAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-REFUND-STATUS-ALIAS", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-REFUND-STATUS"));
            assertEquals(1, mapper.bindRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-REFUND-STATUS"));
            assertEquals(1, eventMapper.markPublishSuccess("EVT-REFUND-STATUS-ALIAS"));
            statement.executeUpdate("UPDATE t_refund_order SET status = 'processing' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            session.clearCache();

            assertFalse(mapper.isMatchingSuccessfulRefundSubmitEvent("EVT-REFUND-STATUS-ALIAS", "RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecoverUnknownAcceptanceUsingAnotherPaymentEvent() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-OTHER-PAY", "RFA-UNKNOWN", "PAY-TRACE");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-OTHER-PAY"));
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-OTHER-PAY"));

            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-OTHER-PAY"));
            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            assertEquals("UNKNOWN", eventMapper.findByEventNoForUpdate("EVT-OTHER-PAY").getPublishStatus());
            session.rollback();
        }
    }

    @Test
    void shouldNotRecoverUnknownAcceptanceUsingPaymentNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-PAY-ALIAS", "RFA-UNKNOWN", "pay-unknown");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-PAY-ALIAS"));
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-PAY-ALIAS"));

            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-PAY-ALIAS"));
            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecoverUnknownAcceptanceUsingEventTypeAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-TYPE-ALIAS", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-TYPE-ALIAS"));
            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'refund_submit' "
                    + "WHERE event_no = 'EVT-TYPE-ALIAS'");
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-TYPE-ALIAS"));

            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-TYPE-ALIAS"));
            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecoverUnknownAcceptanceUsingOtherDestinationOrTopic() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-WRONG-DEST", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-WRONG-DEST"));
            statement.executeUpdate("UPDATE t_payment_event SET downstream_system = 'other-system' "
                    + "WHERE event_no = 'EVT-WRONG-DEST'");
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-WRONG-DEST"));

            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-WRONG-DEST"));
            statement.executeUpdate("UPDATE t_payment_event SET downstream_system = 'gateway-access', "
                    + "event_topic = 'payment.refund.other.v1' WHERE event_no = 'EVT-WRONG-DEST'");
            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-WRONG-DEST"));
            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldNotRecoverUnknownAcceptanceUsingStatusAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-STATUS-ALIAS", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-STATUS-ALIAS"));
            statement.executeUpdate("UPDATE t_payment_event SET publish_status = 'unknown' "
                    + "WHERE event_no = 'EVT-STATUS-ALIAS'");
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-STATUS-ALIAS"));

            assertEquals(0, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-STATUS-ALIAS"));
            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldConfirmOnlyMatchingUnknownRefundSubmitEvent() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-VALID-RECOVERY", "RFA-UNKNOWN", "PAY-UNKNOWN");
            mapper.upsertRefundSubmitEvent("EVT-ALIEN-RECOVERY", "RFA-UNKNOWN", "PAY-TRACE");
            mapper.upsertRefundSubmitEvent("EVT-ALIAS-RECOVERY", "RFA-UNKNOWN", "pay-unknown");
            mapper.upsertRefundSubmitEvent("EVT-TYPE-RECOVERY", "RFA-UNKNOWN", "PAY-UNKNOWN");
            mapper.upsertRefundSubmitEvent("EVT-DEST-RECOVERY", "RFA-UNKNOWN", "PAY-UNKNOWN");
            mapper.upsertRefundSubmitEvent("EVT-TOPIC-RECOVERY", "RFA-UNKNOWN", "PAY-UNKNOWN");
            statement.executeUpdate("UPDATE t_payment_event SET event_type = 'refund_submit' "
                    + "WHERE event_no = 'EVT-TYPE-RECOVERY'");
            statement.executeUpdate("UPDATE t_payment_event SET downstream_system = 'other-system' "
                    + "WHERE event_no = 'EVT-DEST-RECOVERY'");
            statement.executeUpdate("UPDATE t_payment_event SET event_topic = 'payment.refund.other.v1' "
                    + "WHERE event_no = 'EVT-TOPIC-RECOVERY'");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-VALID-RECOVERY"));
            assertEquals(1, eventMapper.markPublishUnknown("EVT-ALIEN-RECOVERY"));
            assertEquals(1, eventMapper.markPublishUnknown("EVT-ALIAS-RECOVERY"));
            assertEquals(1, eventMapper.markPublishUnknown("EVT-TYPE-RECOVERY"));
            assertEquals(1, eventMapper.markPublishUnknown("EVT-DEST-RECOVERY"));
            assertEquals(1, eventMapper.markPublishUnknown("EVT-TOPIC-RECOVERY"));
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-VALID-RECOVERY"));
            assertEquals(1, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-VALID-RECOVERY"));

            assertEquals(1, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            assertEquals("SUCCESS", eventMapper.findByEventNoForUpdate("EVT-VALID-RECOVERY").getPublishStatus());
            assertEquals("UNKNOWN", eventMapper.findByEventNoForUpdate("EVT-ALIEN-RECOVERY").getPublishStatus());
            assertEquals("UNKNOWN", eventMapper.findByEventNoForUpdate("EVT-ALIAS-RECOVERY").getPublishStatus());
            assertEquals("UNKNOWN", eventMapper.findByEventNoForUpdate("EVT-TYPE-RECOVERY").getPublishStatus());
            assertEquals("UNKNOWN", eventMapper.findByEventNoForUpdate("EVT-DEST-RECOVERY").getPublishStatus());
            assertEquals("UNKNOWN", eventMapper.findByEventNoForUpdate("EVT-TOPIC-RECOVERY").getPublishStatus());
            session.rollback();
        }
    }

    @Test
    void shouldNotConfirmUnknownRefundEventWithoutOrderAcceptanceTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-NO-ACCEPTANCE-TIME", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-NO-ACCEPTANCE-TIME"));
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-NO-ACCEPTANCE-TIME"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_code = 'wx_h5', "
                    + "channel_refund_no = 'RCH-NO-ACCEPTANCE-TIME' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");

            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_submitted_at = NOW() "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(1, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldNotConfirmUnknownRefundEventForSupersededAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-SUPERSEDED-UNKNOWN", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-SUPERSEDED-UNKNOWN"));
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-SUPERSEDED"));
            statement.executeUpdate("UPDATE t_refund_order SET channel_code = 'wx_h5', "
                    + "channel_refund_no = 'RCH-SUPERSEDED', channel_submitted_at = NOW() "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_attempt_no, refund_order_id, "
                    + "channel_code, attempt_status, attempt_status_type, created_at) VALUES "
                    + "('RFA-NEWER', 'REF-UNKNOWN', 'wx_h5', 'PENDING', 'warn', "
                    + "DATEADD('SECOND', 1, NOW()))");

            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            assertEquals("UNKNOWN", eventMapper.findByEventNoForUpdate(
                    "EVT-SUPERSEDED-UNKNOWN").getPublishStatus());
            session.rollback();
        }
    }

    @Test
    void shouldNotConfirmUnknownRefundEventThroughRefundStatusAliasOrMissingAttemptTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            mapper.upsertRefundSubmitEvent("EVT-CONFIRM-STATUS-ALIAS", "RFA-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVT-CONFIRM-STATUS-ALIAS"));
            assertEquals(1, mapper.markRefundAttemptUnknown("RFA-UNKNOWN"));
            assertEquals(1, mapper.bindRefundAttemptAccepted("RFA-UNKNOWN", "RCH-CONFIRM-STATUS"));
            assertEquals(1, mapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "RFA-UNKNOWN", "wx_h5", "RCH-CONFIRM-STATUS"));

            statement.executeUpdate("UPDATE t_refund_order SET status = 'processing' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            statement.executeUpdate("UPDATE t_refund_order SET status = 'PROCESSING' "
                    + "WHERE refund_order_id = 'REF-UNKNOWN'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'accepted' "
                    + "WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'ACCEPTED' "
                    + "WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            statement.executeUpdate("UPDATE t_refund_attempt SET submitted_at = NULL "
                    + "WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            assertEquals(0, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            statement.executeUpdate("UPDATE t_refund_attempt SET submitted_at = NOW() "
                    + "WHERE refund_attempt_no = 'RFA-UNKNOWN'");
            assertEquals(1, mapper.markUnknownRefundSubmitEventConfirmed("RFA-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldOrderCompleteFailedRefundCandidatesByTerminalTimeAndLimitBatch() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, status, applied_at, success_at, terminal_at) VALUES "
                    + "('REF-FAILED-LATER-APPLIED-EARLY', 'FAIL', NOW(), NULL, DATEADD('SECOND', 3, NOW())), "
                    + "('REF-FAILED-EARLIER-APPLIED-LATE', 'FAIL', DATEADD('SECOND', 2, NOW()), NULL, "
                    + "DATEADD('SECOND', 1, NOW())), "
                    + "('REF-FAILED-SAME-001', 'FAIL', DATEADD('SECOND', 3, NOW()), NULL, "
                    + "DATEADD('SECOND', 2, NOW())), "
                    + "('REF-FAILED-SAME-002', 'FAIL', DATEADD('SECOND', 4, NOW()), NULL, "
                    + "DATEADD('SECOND', 2, NOW())), "
                    + "('REF-MISSING-TERMINAL', 'FAIL', DATEADD('SECOND', -1, NOW()), NULL, NULL), "
                    + "('REF-MISSING-ATTEMPT', 'FAIL', NOW(), NULL, DATEADD('SECOND', 2, NOW())), "
                    + "('REF-MISMATCHED-ATTEMPT', 'FAIL', NOW(), NULL, DATEADD('SECOND', 2, NOW())), "
                    + "('REF-INCOMPLETE-ATTEMPT', 'FAIL', NOW(), NULL, DATEADD('SECOND', 2, NOW())), "
                    + "('REF-RESIDUAL-SUCCESS', 'FAIL', NOW(), NOW(), DATEADD('SECOND', 2, NOW()))");
            statement.executeUpdate("UPDATE t_refund_order SET refund_amount = 1.00, status_type = 'danger', "
                    + "channel_code = 'wx_h5', channel_refund_no = 'CHANNEL-' || refund_order_id, "
                    + "applied_at = DATEADD('SECOND', -10, NOW()), "
                    + "channel_submitted_at = DATEADD('SECOND', -9, NOW()) "
                    + "WHERE refund_order_id IN ('REF-FAILED-LATER-APPLIED-EARLY', "
                    + "'REF-FAILED-EARLIER-APPLIED-LATE', 'REF-FAILED-SAME-001', 'REF-FAILED-SAME-002')");
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, status, status_type, "
                    + "channel_code, channel_refund_no, channel_submitted_at, applied_at, terminal_at) VALUES "
                    + "('REF-CHANNEL-TIME-MISMATCH', 'FAIL', 'danger', 'wx_h5', 'CHANNEL-TIME-MISMATCH', "
                    + "DATEADD('SECOND', 1, NOW()), DATEADD('SECOND', -10, NOW()), NOW()), "
                    + "('REF-CHANNEL-REFERENCE-MISMATCH', 'FAIL', 'danger', 'wx_h5', 'CHANNEL-EXPECTED', "
                    + "DATEADD('SECOND', -9, NOW()), DATEADD('SECOND', -10, NOW()), "
                    + "DATEADD('SECOND', 2, NOW())), "
                    + "('REF-ATTEMPT-TYPE-MISMATCH', 'FAIL', 'danger', 'wx_h5', 'CHANNEL-TYPE', "
                    + "DATEADD('SECOND', -9, NOW()), DATEADD('SECOND', -10, NOW()), "
                    + "DATEADD('SECOND', 2, NOW()))");
            statement.executeUpdate("INSERT INTO t_refund_attempt("
                    + "refund_attempt_no, refund_order_id, channel_code, attempt_status, "
                    + "attempt_status_type, completed_at, created_at) VALUES "
                    + "('RFA-FAILED-LATER', 'REF-FAILED-LATER-APPLIED-EARLY', 'wx_h5', "
                    + "'FAIL', 'danger', NOW(), NOW()), "
                    + "('RFA-FAILED-EARLIER', 'REF-FAILED-EARLIER-APPLIED-LATE', 'wx_h5', "
                    + "'FAIL', 'danger', NOW(), NOW()), "
                    + "('RFA-FAILED-SAME-001', 'REF-FAILED-SAME-001', 'wx_h5', "
                    + "'FAIL', 'danger', NOW(), NOW()), "
                    + "('RFA-FAILED-SAME-002', 'REF-FAILED-SAME-002', 'wx_h5', "
                    + "'FAIL', 'danger', NOW(), NOW()), "
                    + "('RFA-MISMATCHED', 'REF-MISMATCHED-ATTEMPT', 'wx_h5', "
                    + "'ACCEPTED', 'warn', NOW(), NOW()), "
                    + "('RFA-INCOMPLETE', 'REF-INCOMPLETE-ATTEMPT', 'wx_h5', "
                    + "'FAIL', 'danger', NULL, NOW()), "
                    + "('RFA-RESIDUAL-SUCCESS', 'REF-RESIDUAL-SUCCESS', 'wx_h5', "
                    + "'FAIL', 'danger', NOW(), NOW())");
            statement.executeUpdate("INSERT INTO t_refund_attempt("
                    + "refund_attempt_no, refund_order_id, channel_code, attempt_status, "
                    + "attempt_status_type, completed_at, created_at) VALUES "
                    + "('RFA-CHANNEL-TIME-MISMATCH', 'REF-CHANNEL-TIME-MISMATCH', 'wx_h5', "
                    + "'FAIL', 'danger', NOW(), NOW()), "
                    + "('RFA-CHANNEL-REFERENCE-MISMATCH', 'REF-CHANNEL-REFERENCE-MISMATCH', 'wx_h5', "
                    + "'FAIL', 'danger', NOW(), NOW()), "
                    + "('RFA-ATTEMPT-TYPE-MISMATCH', 'REF-ATTEMPT-TYPE-MISMATCH', 'wx_h5', "
                    + "'FAIL', 'warn', NOW(), NOW())");
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_refund_no = "
                    + "(SELECT channel_refund_no FROM t_refund_order "
                    + "WHERE t_refund_order.refund_order_id = t_refund_attempt.refund_order_id) "
                    + "WHERE refund_order_id IN ('REF-FAILED-LATER-APPLIED-EARLY', "
                    + "'REF-FAILED-EARLIER-APPLIED-LATE', 'REF-FAILED-SAME-001', 'REF-FAILED-SAME-002')");
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_refund_no = 'CHANNEL-OTHER' "
                    + "WHERE refund_order_id = 'REF-CHANNEL-REFERENCE-MISMATCH'");
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_refund_no = 'CHANNEL-TYPE' "
                    + "WHERE refund_order_id = 'REF-ATTEMPT-TYPE-MISMATCH'");
            RefundMapper mapper = session.getMapper(RefundMapper.class);

            assertEquals(java.util.Collections.singletonList("REF-FAILED-EARLIER-APPLIED-LATE"),
                    mapper.findFailedRefundOrderIds(1));
            assertEquals(java.util.Arrays.asList(
                            "REF-FAILED-EARLIER-APPLIED-LATE",
                            "REF-FAILED-SAME-001",
                            "REF-FAILED-SAME-002",
                            "REF-FAILED-LATER-APPLIED-EARLY"),
                    mapper.findFailedRefundOrderIds(10));
        }
    }

    @Test
    void shouldLockCallbackSourceAndFinalizeOnlyFromProcessing() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            refundMapper.bindRefundAttemptAccepted("RFA-TRACE", "RCH-TRACE");
            refundMapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-TRACE");

            RefundCallbackSourceDTO snapshot =
                    refundMapper.findCallbackSourceByRefundOrderId("REF-TRACE");
            RefundCallbackSourceDTO source = refundMapper.findCallbackSourceForUpdate("REF-TRACE");
            int finalized = refundMapper.updateRefundStatus(
                    "REF-TRACE", "PROCESSING", "SUCCESS", "success", true);
            assertEquals(1, refundMapper.finalizeRefundAttempt(
                    "RFA-TRACE", "SUCCESS", "success"));
            int repeated = refundMapper.updateRefundStatus(
                    "REF-TRACE", "PROCESSING", "FAIL", "danger", false);

            assertEquals("PROCESSING", snapshot.getStatus());
            assertEquals(new java.math.BigDecimal("10.00"), snapshot.getRefundAmount());
            assertEquals(new java.math.BigDecimal("68.00"), snapshot.getPaidAmount());
            assertEquals("wx_h5", snapshot.getChannelCode());
            assertEquals("RCH-TRACE", snapshot.getChannelRefundNo());
            assertEquals(snapshot.getStatus(), source.getStatus());
            assertEquals(new java.math.BigDecimal("10.00"), source.getRefundAmount());
            assertEquals(new java.math.BigDecimal("68.00"), source.getPaidAmount());
            assertEquals("wx_h5", source.getChannelCode());
            assertEquals("RCH-TRACE", source.getChannelRefundNo());
            assertEquals(1, finalized);
            assertEquals(0, repeated);
            RefundCallbackSourceDTO terminal = refundMapper.findCallbackSourceForUpdate("REF-TRACE");
            assertEquals("SUCCESS", terminal.getStatus());
            assertEquals(new java.math.BigDecimal("10.00"), terminal.getRefundAmount());
            assertEquals(new java.math.BigDecimal("68.00"), terminal.getPaidAmount());
            assertEquals("SUCCESS", terminal.getAttemptStatus());
            assertNotNull(terminal.getSuccessAt());
            assertNotNull(terminal.getAttemptCompletedAt());
            session.rollback();
        }
    }

    @Test
    void shouldExposeCumulativeMismatchInBothTerminalCallbackSnapshots() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_order SET payment_order_id = 'PAY-TRACE', "
                    + "refund_amount = 40.00, channel_code = 'wx_h5', "
                    + "channel_refund_no = CASE WHEN refund_order_id = 'REF-TRACE' "
                    + "THEN 'CH-TRACE' ELSE 'CH-UNKNOWN' END, "
                    + "applied_at = '2026-10-02 09:00:00', channel_submitted_at = '2026-10-02 09:01:00', "
                    + "status = 'SUCCESS', status_type = 'success', success_at = '2026-10-02 10:00:00', "
                    + "terminal_at = '2026-10-02 10:00:00' "
                    + "WHERE refund_order_id IN ('REF-TRACE', 'REF-UNKNOWN')");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status = 'SUCCESS', "
                    + "attempt_status_type = 'success', "
                    + "channel_refund_no = CASE WHEN refund_order_id = 'REF-TRACE' "
                    + "THEN 'CH-TRACE' ELSE 'CH-UNKNOWN' END, "
                    + "created_at = '2026-10-02 09:01:00', completed_at = '2026-10-02 10:00:00' "
                    + "WHERE refund_order_id IN ('REF-TRACE', 'REF-UNKNOWN')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);

            assertEquals(Boolean.TRUE, mapper.findCallbackSourceByRefundOrderId("REF-TRACE")
                    .getTerminalFactMismatch());
            assertEquals(Boolean.TRUE, mapper.findCallbackSourceForUpdate("REF-UNKNOWN")
                    .getTerminalFactMismatch());

            statement.executeUpdate("UPDATE t_payment_order SET amount = 80.00 "
                    + "WHERE payment_order_id = 'PAY-TRACE'");
            session.clearCache();
            assertEquals(Boolean.FALSE, mapper.findCallbackSourceByRefundOrderId("REF-TRACE")
                    .getTerminalFactMismatch());
            assertEquals(Boolean.FALSE, mapper.findCallbackSourceForUpdate("REF-UNKNOWN")
                    .getTerminalFactMismatch());
            session.rollback();
        }
    }

    @Test
    void shouldCountInvalidActiveAmountsWithoutTreatingFailedRefundsAsReserved() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            for (String amount : new String[] {"0.00", "-5.00", "NULL"}) {
                statement.executeUpdate("UPDATE t_refund_order SET refund_amount = " + amount
                        + " WHERE refund_order_id = 'REF-TRACE'");
                session.clearCache();
                assertEquals(1L, mapper.countInvalidActiveRefundAmounts("PAY-TRACE"));
                assertEquals(0L, mapper.countInvalidActiveRefundAmounts("PAY-UNKNOWN"));
            }
            statement.executeUpdate("UPDATE t_refund_order SET status = 'FAIL' "
                    + "WHERE refund_order_id = 'REF-TRACE'");
            session.clearCache();
            assertEquals(0L, mapper.countInvalidActiveRefundAmounts("PAY-TRACE"));
            statement.executeUpdate("UPDATE t_refund_order SET status = 'REVIEWING' "
                    + "WHERE refund_order_id = 'REF-TRACE'");
            session.clearCache();
            assertEquals(1L, mapper.countInvalidActiveRefundAmounts("PAY-TRACE"));
            session.rollback();
        }
    }

    @Test
    void shouldResolvePreviousChannelTraceWithoutTreatingItAsCurrentAttempt() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            assertEquals(1, refundMapper.bindRefundAttemptAccepted("RFA-TRACE", "RCH-PREVIOUS"));
            assertEquals(1, refundMapper.bindRefundChannelAccepted(
                    "REF-TRACE", "RFA-TRACE", "wx_h5", "RCH-PREVIOUS"));
            assertEquals(1, refundMapper.insertRefundAttempt(
                    "RFA-TRACE-NEW", "REF-TRACE", "wx_h5"));

            RefundCallbackSourceDTO current =
                    refundMapper.findCallbackSourceByRefundOrderId("REF-TRACE");

            assertEquals("RFA-TRACE-NEW", current.getRefundAttemptNo());
            assertNull(current.getChannelRefundNo());
            assertEquals("RFA-TRACE",
                    refundMapper.findAttemptNoByChannelRefundNo("wx_h5", "RCH-PREVIOUS"));
            session.rollback();
        }
    }

    @Test
    void shouldKeepDemoChannelRefundNumbersDistinctInAttemptHistory() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            LocalRefundChannelSubmitServiceImpl localChannel = new LocalRefundChannelSubmitServiceImpl();
            String firstChannelRefundNo = localChannel.submit(
                    refundMapper.findChannelSubmitRequestByAttemptNo("RFA-TRACE")).getChannelRefundNo();
            assertEquals("RCH-RFA-TRACE", firstChannelRefundNo);
            assertEquals(1, refundMapper.bindRefundAttemptAccepted("RFA-TRACE", firstChannelRefundNo));
            assertEquals(1, refundMapper.insertRefundAttempt(
                    "RFA-TRACE-NEW", "REF-TRACE", "wx_h5"));

            String nextChannelRefundNo = localChannel.submit(
                    refundMapper.findChannelSubmitRequestByAttemptNo("RFA-TRACE-NEW")).getChannelRefundNo();
            assertEquals("RCH-RFA-TRACE-NEW", nextChannelRefundNo);
            assertEquals(1, refundMapper.bindRefundAttemptAccepted("RFA-TRACE-NEW", nextChannelRefundNo));
            assertEquals("RFA-TRACE", refundMapper.findAttemptNoByChannelRefundNo(
                    "wx_h5", firstChannelRefundNo));
            assertEquals("RFA-TRACE-NEW", refundMapper.findAttemptNoByChannelRefundNo(
                    "wx_h5", nextChannelRefundNo));
            session.rollback();
        }
    }

    @Test
    void shouldExposeChannelSubmitSnapshotOnlyForLatestAttemptWhenCreatedAtTies() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            assertEquals(1, refundMapper.insertRefundAttempt(
                    "RFA-TRACE-NEW", "REF-TRACE", "wx_h5"));

            RefundChannelSubmitRequestDTO previous =
                    refundMapper.findChannelSubmitRequestByAttemptNo("RFA-TRACE");
            RefundChannelSubmitRequestDTO latest =
                    refundMapper.findChannelSubmitRequestByAttemptNo("RFA-TRACE-NEW");

            assertNull(previous);
            assertNotNull(latest);
            assertEquals("RFA-TRACE-NEW", latest.getRefundAttemptNo());
            assertEquals("REF-TRACE", latest.getRefundOrderId());
            assertEquals("PAY-TRACE", latest.getPaymentOrderId());
            session.rollback();
        }
    }

    @Test
    void shouldRestartFailedRefundWithoutReusingPreviousAcceptedTrace() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_order "
                    + "SET status='FAIL', status_type='danger', channel_code='wx_h5', "
                    + "channel_refund_no='RCH-FAILED', channel_submitted_at=NOW(), success_at=NOW() "
                    + "WHERE refund_order_id='REF-TRACE'");
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);

            int restarted = refundMapper.restartFailedRefund("REF-TRACE");
            int repeated = refundMapper.restartFailedRefund("REF-TRACE");

            assertEquals(1, restarted);
            assertEquals(0, repeated);
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT status, status_type, channel_code, channel_refund_no, "
                            + "channel_submitted_at, success_at FROM t_refund_order "
                            + "WHERE refund_order_id='REF-TRACE'")) {
                resultSet.next();
                assertEquals("PROCESSING", resultSet.getString("status"));
                assertEquals("warn", resultSet.getString("status_type"));
                assertNull(resultSet.getString("channel_code"));
                assertNull(resultSet.getString("channel_refund_no"));
                assertNull(resultSet.getTimestamp("channel_submitted_at"));
                assertNull(resultSet.getTimestamp("success_at"));
            }
            session.rollback();
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

    private void createEventTable() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "event_no VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "event_type VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "biz_no VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "downstream_system VARCHAR(255) NOT NULL, "
                    + "event_topic VARCHAR(128) NOT NULL, "
                    + "publish_status VARCHAR_IGNORECASE(32) NOT NULL, "
                    + "publish_status_type VARCHAR(32) NOT NULL, "
                    + "payment_success_http_stage VARCHAR(32) NOT NULL DEFAULT 'PENDING', "
                    + "retry_count INT NOT NULL, "
                    + "last_published_at TIMESTAMP NULL, "
                    + "next_retry_at TIMESTAMP NULL, "
                    + "event_payload CLOB NOT NULL, "
                    + "created_at TIMESTAMP NOT NULL)");
            session.commit();
        }
    }

    private void createRefundTable() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName()
                    + ".format\"");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("CREATE TABLE t_prepay_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "payment_order_id VARCHAR(64), pay_scene VARCHAR(32), created_at TIMESTAMP)");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "amount DECIMAL(18, 2) NOT NULL, "
                    + "channel_code VARCHAR(64) NULL, "
                    + "payment_method VARCHAR(32), status VARCHAR(32))");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, amount, channel_code) "
                    + "VALUES ('PAY-TRACE', 68.00, 'wx_h5'), ('PAY-UNKNOWN', 88.00, 'wx_h5')");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "refund_order_id VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) DEFAULT 'PAY-TRACE', "
                    + "order_no VARCHAR(64) NULL, "
                    + "customer_name VARCHAR(64) NULL, "
                    + "refund_amount DECIMAL(18, 2) NULL, "
                    + "refund_method VARCHAR(32) NULL, "
                    + "refund_reason VARCHAR(255) NULL, "
                    + "channel_code VARCHAR(64) NULL, "
                    + "channel_refund_no VARCHAR(128) NULL, "
                    + "channel_submitted_at TIMESTAMP NULL, "
                    + "status VARCHAR_IGNORECASE(32) NOT NULL, "
                    + "status_type VARCHAR(32) NOT NULL DEFAULT 'warn', "
                    + "success_at TIMESTAMP NULL, "
                    + "terminal_at TIMESTAMP NULL, "
                    + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "UNIQUE(channel_code, channel_refund_no))");
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, refund_reason, channel_code, status) VALUES "
                    + "('REF-TRACE', 'PAY-TRACE', 'ORD-TRACE', '张女士', 10.00, "
                    + "'原路退款', '测试退款', NULL, 'PROCESSING'), "
                    + "('REF-UNKNOWN', 'PAY-UNKNOWN', 'ORD-UNKNOWN', '李女士', 20.00, "
                    + "'原路退款', '未知退款', NULL, 'PROCESSING')");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "refund_attempt_no VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "refund_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "channel_code VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "channel_refund_no VARCHAR_IGNORECASE(128) NULL, "
                    + "attempt_status VARCHAR_IGNORECASE(32) NOT NULL, "
                    + "attempt_status_type VARCHAR(32) NOT NULL, "
                    + "submitted_at TIMESTAMP NULL, "
                    + "completed_at TIMESTAMP NULL, "
                    + "created_at TIMESTAMP NOT NULL, "
                    + "UNIQUE(channel_code, channel_refund_no))");
            statement.executeUpdate("INSERT INTO t_refund_attempt("
                    + "refund_attempt_no, refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, created_at) VALUES "
                    + "('RFA-TRACE', 'REF-TRACE', 'wx_h5', NULL, 'PENDING', 'warn', NOW()), "
                    + "('RFA-UNKNOWN', 'REF-UNKNOWN', 'wx_h5', NULL, 'PENDING', 'warn', NOW())");
            session.commit();
        }
    }

    private void markEventFailed() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_event SET publish_status='FAILED', "
                    + "publish_status_type='danger', retry_count=2, next_retry_at=NOW() "
                    + "WHERE event_no='EVTR-STABLE'");
            session.commit();
        }
    }

    private void insertUnsupportedPendingEvent() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_event(event_no, event_type, payment_order_id, "
                    + "biz_no, downstream_system, event_topic, publish_status, publish_status_type, "
                    + "retry_count, next_retry_at, event_payload, created_at) VALUES ("
                    + "'EVT-CLOSED', 'PAYMENT_CLOSED', 'PAY-002', 'ORD-002', 'accounting-system', "
                    + "'payment.trade.closed.v1', 'PENDING', 'warn', 0, NOW(), '待下发', NOW())");
            session.commit();
        }
    }

    private int countRefundEventRows(SqlSession session) throws Exception {
        try (Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM t_payment_event WHERE event_type='REFUND_SUBMIT'")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
