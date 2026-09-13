package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewDTO;
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentIssueDutyRosterReviewEntity;
import java.io.InputStream;
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

/** 异常告警值班路由复核 Mapper 的 MySQL 兼容 SQL 集成测试。 */
class PaymentConfigIssueDutyRosterReviewMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_issue_duty_roster_review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentConfigMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentConfigMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentConfigMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldPersistApplyCompleteReleaseAndListFullCandidate() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentIssueDutyRosterEntity roster =
                    mapper.findIssueDutyRosterByCodeForUpdate("DUTY_CALLBACK_P1");
            assertEquals("支付后端值班", roster.getReceiver());
            assertEquals(Integer.valueOf(30), roster.getEscalationTimeoutMinutes());
            assertEquals(1, mapper.findIssueDutyRostersByScopeForUpdate(
                    roster.getIssueType(), roster.getSeverity()).size());

            assertEquals(1, mapper.insertIssueDutyRosterReview(review("DRR-TEST-001")));
            assertEquals("DRR-TEST-001",
                    mapper.findPendingIssueDutyRosterReviewForUpdate("DUTY_CALLBACK_P1").getRequestNo());
            PaymentIssueDutyRosterReviewEntity locked =
                    mapper.findIssueDutyRosterReviewForUpdate("DRR-TEST-001");
            assertEquals("夜间跨日班", locked.getScheduleTag());
            assertEquals("NON_WORKDAY_ONLY", locked.getHolidayStrategy());

            roster.setReceiver("支付联合值班");
            roster.setScheduleTag("夜间跨日班");
            roster.setEffectiveStartHour(Integer.valueOf(22));
            roster.setEffectiveEndHour(Integer.valueOf(6));
            roster.setWeekdayScope("5,6,7");
            roster.setHolidayStrategy("NON_WORKDAY_ONLY");
            roster.setStatus("DISABLED");
            roster.setStatusType("danger");
            assertEquals(1, mapper.updateIssueDutyRoster(roster));

            PaymentIssueDutyRosterEntity created = roster("DUTY_NEW_P1");
            assertEquals(1, mapper.insertIssueDutyRoster(created));
            assertEquals("待支付未收口",
                    mapper.findIssueDutyRosterByCodeForUpdate("DUTY_NEW_P1").getIssueType());

            assertEquals(1, mapper.completeIssueDutyRosterReview(
                    "DRR-TEST-001", "APPROVED", "success", "checker-b", "确认调整"));
            assertNull(mapper.findPendingIssueDutyRosterReviewForUpdate("DUTY_CALLBACK_P1"));
            assertEquals(1, mapper.insertIssueDutyRosterReview(review("DRR-TEST-002")));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            java.util.List<PaymentIssueDutyRosterReviewDTO> reviews =
                    session.getMapper(PaymentConfigMapper.class).findIssueDutyRosterReviews();
            PaymentIssueDutyRosterReviewDTO completed = reviews.stream()
                    .filter(item -> "DRR-TEST-001".equals(item.getRequestNo()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("APPROVED", completed.getReviewStatus());
            assertEquals("UPDATE", completed.getOperationType());
            assertEquals("支付联合值班", completed.getReceiver());
            assertEquals(Integer.valueOf(22), completed.getEffectiveStartHour());
            assertEquals(Integer.valueOf(6), completed.getEffectiveEndHour());
            assertEquals("5,6,7", completed.getWeekdayScope());
            assertEquals("SELF_DECLARED", completed.getIdentityTrustLevel());
            assertEquals(2, reviews.size());
        }
    }

    private PaymentIssueDutyRosterReviewEntity review(String requestNo) {
        PaymentIssueDutyRosterReviewEntity review = new PaymentIssueDutyRosterReviewEntity();
        review.setRequestNo(requestNo);
        review.setRosterCode("DUTY_CALLBACK_P1");
        review.setOperationType("UPDATE");
        review.setIssueType("待回调未收口");
        review.setSeverity("P1");
        review.setResponsibilityGroup("支付后端值班组");
        review.setReceiver("支付联合值班");
        review.setNotifyChannels("IN_APP,IM,SMS");
        review.setEscalationLevel("L2");
        review.setEscalationReceiver("支付技术总监");
        review.setEscalationPolicy("15分钟未确认升级支付技术总监");
        review.setEscalationTimeoutMinutes(Integer.valueOf(15));
        review.setScheduleTag("夜间跨日班");
        review.setEffectiveStartHour(Integer.valueOf(22));
        review.setEffectiveEndHour(Integer.valueOf(6));
        review.setWeekdayScope("5,6,7");
        review.setHolidayStrategy("NON_WORKDAY_ONLY");
        review.setPendingRosterCode("DUTY_CALLBACK_P1");
        review.setBaseFingerprint(String.join("", java.util.Collections.nCopies(64, "a")));
        review.setCandidateStatus("DISABLED");
        review.setCandidateStatusType("danger");
        review.setRequester("maker-a");
        review.setRequestReason("调整夜间值班路由");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentIssueDutyRosterEntity roster(String rosterCode) {
        PaymentIssueDutyRosterEntity roster = new PaymentIssueDutyRosterEntity();
        roster.setRosterCode(rosterCode);
        roster.setIssueType("待支付未收口");
        roster.setSeverity("P1");
        roster.setResponsibilityGroup("支付后端值班组");
        roster.setReceiver("支付后端值班");
        roster.setNotifyChannels("IN_APP,IM,SMS");
        roster.setEscalationLevel("L1");
        roster.setEscalationReceiver("支付技术负责人");
        roster.setEscalationPolicy("30分钟未确认升级支付技术负责人");
        roster.setEscalationTimeoutMinutes(Integer.valueOf(30));
        roster.setScheduleTag("全时段");
        roster.setEffectiveStartHour(Integer.valueOf(0));
        roster.setEffectiveEndHour(Integer.valueOf(23));
        roster.setWeekdayScope("1,2,3,4,5,6,7");
        roster.setHolidayStrategy("ALL_DAYS");
        roster.setStatus("ENABLED");
        roster.setStatusType("success");
        return roster;
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_duty_roster_review");
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_duty_roster");
            statement.execute("CREATE TABLE t_payment_issue_duty_roster ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, roster_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "issue_type VARCHAR(64) NOT NULL, severity VARCHAR(32) NOT NULL, "
                    + "responsibility_group VARCHAR(64) NOT NULL, receiver VARCHAR(64) NOT NULL, "
                    + "notify_channels VARCHAR(64) NOT NULL, escalation_level VARCHAR(32) NOT NULL, "
                    + "escalation_receiver VARCHAR(64) NOT NULL, escalation_policy VARCHAR(128) NOT NULL, "
                    + "escalation_timeout_minutes INT NOT NULL, schedule_tag VARCHAR(64) NOT NULL, "
                    + "effective_start_hour TINYINT NOT NULL, effective_end_hour TINYINT NOT NULL, "
                    + "weekday_scope VARCHAR(32) NOT NULL, holiday_strategy VARCHAR(32) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, "
                    + "updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_issue_duty_roster_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "roster_code VARCHAR(64) NOT NULL, operation_type VARCHAR(32) NOT NULL, "
                    + "issue_type VARCHAR(64) NOT NULL, severity VARCHAR(32) NOT NULL, "
                    + "responsibility_group VARCHAR(64) NOT NULL, receiver VARCHAR(64) NOT NULL, "
                    + "notify_channels VARCHAR(64) NOT NULL, escalation_level VARCHAR(32) NOT NULL, "
                    + "escalation_receiver VARCHAR(64) NOT NULL, escalation_policy VARCHAR(128) NOT NULL, "
                    + "escalation_timeout_minutes INT NOT NULL, schedule_tag VARCHAR(64) NOT NULL, "
                    + "effective_start_hour TINYINT NOT NULL, effective_end_hour TINYINT NOT NULL, "
                    + "weekday_scope VARCHAR(32) NOT NULL, holiday_strategy VARCHAR(32) NOT NULL, "
                    + "pending_roster_code VARCHAR(64) UNIQUE, base_fingerprint VARCHAR(64), "
                    + "candidate_status VARCHAR(32) NOT NULL, candidate_status_type VARCHAR(32) NOT NULL, "
                    + "requester VARCHAR(64) NOT NULL, request_reason VARCHAR(255) NOT NULL, "
                    + "review_status VARCHAR(32) NOT NULL, review_status_type VARCHAR(32) NOT NULL, "
                    + "reviewer VARCHAR(64), review_comment VARCHAR(255), created_at DATETIME NOT NULL, "
                    + "reviewed_at DATETIME, updated_at DATETIME NOT NULL)");
            statement.execute("INSERT INTO t_payment_issue_duty_roster ("
                    + "roster_code, issue_type, severity, responsibility_group, receiver, notify_channels, "
                    + "escalation_level, escalation_receiver, escalation_policy, escalation_timeout_minutes, "
                    + "schedule_tag, effective_start_hour, effective_end_hour, weekday_scope, holiday_strategy, "
                    + "status, status_type, updated_at) VALUES ("
                    + "'DUTY_CALLBACK_P1', '待回调未收口', 'P1', '支付后端值班组', '支付后端值班', "
                    + "'IN_APP,IM,SMS', 'L1', '支付技术负责人', '30分钟未确认升级支付技术负责人', 30, "
                    + "'交易链路白班', 0, 23, '1,2,3,4,5', 'WORKDAY_ONLY', 'ENABLED', 'success', NOW())");
            session.commit();
        }
    }
}
