package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.Statement;
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
        parseMapper(configuration, "mapper/RefundMapper.xml");
        parseMapper(configuration, "mapper/PaymentEventMapper.xml");
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createEventTable();
        createRefundTable();
    }

    @Test
    void shouldReuseStableRefundEventAndExposePendingEventForAutomaticDispatch() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            refundMapper.upsertRefundSubmitEvent("EVTR-STABLE", "REF-001", "PAY-001");
            session.commit();
        }
        markEventFailed();
        insertUnsupportedPendingEvent();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            refundMapper.upsertRefundSubmitEvent("EVTR-STABLE", "REF-001", "PAY-001");

            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            List<String> dueEventNos = eventMapper.findDueFailedEventNos(100);
            PaymentEventListItemDTO event = eventMapper.findByEventNoForUpdate("EVTR-STABLE");

            assertEquals(1, countRefundEventRows(session));
            assertEquals(1, dueEventNos.size());
            assertEquals("EVTR-STABLE", dueEventNos.get(0));
            assertEquals("REFUND_SUBMIT", event.getEventType());
            assertEquals("REF-001", event.getBizNo());
            assertEquals("PENDING", event.getPublishStatus());
            assertEquals(0, event.getRetryCount());
            session.rollback();
        }
    }

    @Test
    void shouldBindAcceptedRefundTraceOnlyOnce() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);

            int firstBinding = refundMapper.bindRefundChannelAccepted(
                    "REF-TRACE", "wx_h5", "RCH-TRACE");
            int conflictingBinding = refundMapper.bindRefundChannelAccepted(
                    "REF-TRACE", "wx_h5", "RCH-OTHER");

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
    void shouldExcludeUnknownRefundSubmitFromEveryRetryCandidate() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            refundMapper.upsertRefundSubmitEvent("EVTR-UNKNOWN", "REF-001", "PAY-001");

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
            refundMapper.upsertRefundSubmitEvent("EVTR-RECOVER", "REF-UNKNOWN", "PAY-UNKNOWN");
            assertEquals(1, eventMapper.markPublishUnknown("EVTR-RECOVER"));

            RefundCallbackSourceDTO before =
                    refundMapper.findCallbackSourceByRefundOrderId("REF-UNKNOWN");
            assertNull(before.getChannelRefundNo());
            assertEquals("UNKNOWN", before.getSubmitEventStatus());
            assertEquals(0, refundMapper.bindUnknownRefundChannelAccepted(
                    "REF-TRACE", "wx_h5", "RCH-NOT-ALLOWED"));
            assertEquals(1, refundMapper.bindUnknownRefundChannelAccepted(
                    "REF-UNKNOWN", "wx_h5", "RCH-RECOVERED"));
            assertEquals(1, refundMapper.markUnknownRefundSubmitEventConfirmed("REF-UNKNOWN"));

            RefundCallbackSourceDTO after = refundMapper.findCallbackSourceForUpdate("REF-UNKNOWN");
            assertEquals("RCH-RECOVERED", after.getChannelRefundNo());
            assertEquals("SUCCESS", after.getSubmitEventStatus());
            assertEquals("SUCCESS", eventMapper.findByEventNoForUpdate("EVTR-RECOVER").getPublishStatus());
            session.rollback();
        }
    }

    @Test
    void shouldLockCallbackSourceAndFinalizeOnlyFromProcessing() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper refundMapper = session.getMapper(RefundMapper.class);
            refundMapper.bindRefundChannelAccepted("REF-TRACE", "wx_h5", "RCH-TRACE");

            RefundCallbackSourceDTO snapshot =
                    refundMapper.findCallbackSourceByRefundOrderId("REF-TRACE");
            RefundCallbackSourceDTO source = refundMapper.findCallbackSourceForUpdate("REF-TRACE");
            int finalized = refundMapper.updateRefundStatus(
                    "REF-TRACE", "PROCESSING", "SUCCESS", "success", true);
            int repeated = refundMapper.updateRefundStatus(
                    "REF-TRACE", "PROCESSING", "FAIL", "danger", false);

            assertEquals("PROCESSING", snapshot.getStatus());
            assertEquals("wx_h5", snapshot.getChannelCode());
            assertEquals("RCH-TRACE", snapshot.getChannelRefundNo());
            assertEquals(snapshot.getStatus(), source.getStatus());
            assertEquals("wx_h5", source.getChannelCode());
            assertEquals("RCH-TRACE", source.getChannelRefundNo());
            assertEquals(1, finalized);
            assertEquals(0, repeated);
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
                    + "event_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "event_type VARCHAR(64) NOT NULL, "
                    + "payment_order_id VARCHAR(64) NOT NULL, "
                    + "biz_no VARCHAR(64) NOT NULL, "
                    + "downstream_system VARCHAR(255) NOT NULL, "
                    + "event_topic VARCHAR(128) NOT NULL, "
                    + "publish_status VARCHAR(32) NOT NULL, "
                    + "publish_status_type VARCHAR(32) NOT NULL, "
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
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "refund_order_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "channel_code VARCHAR(64) NULL, "
                    + "channel_refund_no VARCHAR(128) NULL, "
                    + "channel_submitted_at TIMESTAMP NULL, "
                    + "status VARCHAR(32) NOT NULL, "
                    + "status_type VARCHAR(32) NOT NULL DEFAULT 'warn', "
                    + "success_at TIMESTAMP NULL, "
                    + "UNIQUE(channel_code, channel_refund_no))");
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, channel_code, status) "
                    + "VALUES ('REF-TRACE', NULL, 'PROCESSING'), "
                    + "('REF-UNKNOWN', 'wx_h5', 'PROCESSING')");
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
