package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentAlertProviderReviewDTO;
import com.abc123.hsp.entity.PaymentAlertProviderConfigEntity;
import com.abc123.hsp.entity.PaymentAlertProviderReviewEntity;
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

/** 告警通知供应商复核 Mapper 的 MySQL 兼容 SQL 集成测试。 */
class PaymentConfigAlertProviderReviewMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_alert_provider_review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
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
            PaymentAlertProviderConfigEntity provider =
                    mapper.findAlertProviderByCodeForUpdate("ALERT_IM_WECOM");
            assertEquals("企业消息", provider.getProviderName());
            assertEquals(Integer.valueOf(10), provider.getRoutePriority());

            assertEquals(1, mapper.insertAlertProviderReview(review("APR-TEST-001")));
            assertEquals("APR-TEST-001",
                    mapper.findPendingAlertProviderReviewForUpdate("ALERT_IM_WECOM").getRequestNo());
            PaymentAlertProviderReviewEntity locked =
                    mapper.findAlertProviderReviewForUpdate("APR-TEST-001");
            assertEquals("wecom-alert-v2", locked.getEndpointAlias());
            assertEquals("每分钟80条", locked.getRateLimitPolicy());

            provider.setProviderName("企业消息主通道");
            provider.setEndpointAlias("wecom-alert-v2");
            provider.setRoutePriority(Integer.valueOf(5));
            provider.setRateLimitPolicy("每分钟80条");
            provider.setStatus("DISABLED");
            provider.setStatusType("danger");
            assertEquals(1, mapper.updateAlertProvider(provider));

            PaymentAlertProviderConfigEntity created = provider("ALERT_SMS_BACKUP");
            created.setChannelCode("SMS");
            assertEquals(1, mapper.insertAlertProvider(created));
            assertEquals("SMS",
                    mapper.findAlertProviderByCodeForUpdate("ALERT_SMS_BACKUP").getChannelCode());

            assertEquals(1, mapper.completeAlertProviderReview(
                    "APR-TEST-001", "APPROVED", "success", "checker-b", "确认调整"));
            assertNull(mapper.findPendingAlertProviderReviewForUpdate("ALERT_IM_WECOM"));
            assertEquals(1, mapper.insertAlertProviderReview(review("APR-TEST-002")));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            java.util.List<PaymentAlertProviderReviewDTO> reviews =
                    session.getMapper(PaymentConfigMapper.class).findAlertProviderReviews();
            PaymentAlertProviderReviewDTO completed = reviews.stream()
                    .filter(item -> "APR-TEST-001".equals(item.getRequestNo()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("APPROVED", completed.getReviewStatus());
            assertEquals("UPDATE", completed.getOperationType());
            assertEquals("企业消息主通道", completed.getProviderName());
            assertEquals("wecom-alert-v2", completed.getEndpointAlias());
            assertEquals(Integer.valueOf(5), completed.getRoutePriority());
            assertEquals("SELF_DECLARED", completed.getIdentityTrustLevel());
            assertEquals(2, reviews.size());
        }
    }

    private PaymentAlertProviderReviewEntity review(String requestNo) {
        PaymentAlertProviderReviewEntity review = new PaymentAlertProviderReviewEntity();
        review.setRequestNo(requestNo);
        review.setProviderCode("ALERT_IM_WECOM");
        review.setOperationType("UPDATE");
        review.setProviderName("企业消息主通道");
        review.setChannelCode("IM");
        review.setEndpointAlias("wecom-alert-v2");
        review.setTemplateCode("PAYMENT_ALERT");
        review.setTemplateBody("异常：${issueNo}");
        review.setRouteRule("P1/P2");
        review.setRoutePriority(Integer.valueOf(5));
        review.setRetryPolicy("最多3次");
        review.setRateLimitPolicy("每分钟80条");
        review.setPendingProviderCode("ALERT_IM_WECOM");
        review.setBaseFingerprint(String.join("", java.util.Collections.nCopies(64, "a")));
        review.setCandidateStatus("DISABLED");
        review.setCandidateStatusType("danger");
        review.setRequester("maker-a");
        review.setRequestReason("调整告警通知供应商");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentAlertProviderConfigEntity provider(String providerCode) {
        PaymentAlertProviderConfigEntity provider = new PaymentAlertProviderConfigEntity();
        provider.setProviderCode(providerCode);
        provider.setProviderName("企业消息");
        provider.setChannelCode("IM");
        provider.setEndpointAlias("wecom-alert");
        provider.setTemplateCode("PAYMENT_ALERT");
        provider.setTemplateBody("异常：${issueNo}");
        provider.setRouteRule("P1/P2");
        provider.setRoutePriority(Integer.valueOf(10));
        provider.setRetryPolicy("最多3次");
        provider.setRateLimitPolicy("每分钟100条");
        provider.setStatus("ENABLED");
        provider.setStatusType("success");
        return provider;
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_alert_provider_review");
            statement.execute("DROP TABLE IF EXISTS t_payment_alert_provider_config");
            statement.execute("CREATE TABLE t_payment_alert_provider_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, provider_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "provider_name VARCHAR(64) NOT NULL, channel_code VARCHAR(32) NOT NULL, "
                    + "endpoint_alias VARCHAR(64) NOT NULL, template_code VARCHAR(64) NOT NULL, "
                    + "template_body VARCHAR(1024) NOT NULL, route_rule VARCHAR(255) NOT NULL, "
                    + "route_priority INT NOT NULL, retry_policy VARCHAR(128) NOT NULL, "
                    + "rate_limit_policy VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, "
                    + "status_type VARCHAR(32) NOT NULL, updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_alert_provider_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "provider_code VARCHAR(64) NOT NULL, operation_type VARCHAR(32) NOT NULL, "
                    + "provider_name VARCHAR(64) NOT NULL, channel_code VARCHAR(32) NOT NULL, "
                    + "endpoint_alias VARCHAR(64) NOT NULL, template_code VARCHAR(64) NOT NULL, "
                    + "template_body VARCHAR(1024) NOT NULL, route_rule VARCHAR(255) NOT NULL, "
                    + "route_priority INT NOT NULL, retry_policy VARCHAR(128) NOT NULL, "
                    + "rate_limit_policy VARCHAR(128) NOT NULL, pending_provider_code VARCHAR(64) UNIQUE, "
                    + "base_fingerprint VARCHAR(64), candidate_status VARCHAR(32) NOT NULL, "
                    + "candidate_status_type VARCHAR(32) NOT NULL, requester VARCHAR(64) NOT NULL, "
                    + "request_reason VARCHAR(255) NOT NULL, review_status VARCHAR(32) NOT NULL, "
                    + "review_status_type VARCHAR(32) NOT NULL, reviewer VARCHAR(64), "
                    + "review_comment VARCHAR(255), created_at DATETIME NOT NULL, "
                    + "reviewed_at DATETIME, updated_at DATETIME NOT NULL)");
            statement.execute("INSERT INTO t_payment_alert_provider_config ("
                    + "provider_code, provider_name, channel_code, endpoint_alias, template_code, "
                    + "template_body, route_rule, route_priority, retry_policy, rate_limit_policy, "
                    + "status, status_type, updated_at) VALUES ("
                    + "'ALERT_IM_WECOM', '企业消息', 'IM', 'wecom-alert', 'PAYMENT_ALERT', "
                    + "'异常：${issueNo}', 'P1/P2', 10, '最多3次', '每分钟100条', "
                    + "'ENABLED', 'success', NOW())");
            session.commit();
        }
    }
}
