package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicyVersionDTO;
import com.abc123.hsp.entity.PaymentControlPolicyEntity;
import com.abc123.hsp.entity.PaymentControlPolicyReviewEntity;
import com.abc123.hsp.entity.PaymentControlPolicyVersionEntity;
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

/** 支付控制策略复核 Mapper 的 MySQL 兼容 SQL 集成测试。 */
class PaymentConfigControlPolicyReviewMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_control_policy_review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldKeepTokenOutOfListsAndPersistCompletePolicyReview() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentControlPolicyDTO displayed = mapper.findControlPolicies().get(0);
            assertNull(displayed.getAccessTokenValue());
            assertTrue(displayed.getAccessTokenConfigured().booleanValue());
            PaymentControlPolicyEntity current =
                    mapper.findControlPolicyBySourceAppIdForUpdate("housekeeping-app-web");
            assertEquals("token-current", current.getAccessTokenValue());

            PaymentControlPolicyVersionEntity version = version(current);
            assertEquals(1, mapper.insertControlPolicyVersion(version));
            PaymentControlPolicyVersionDTO displayedVersion =
                    mapper.findControlPolicyVersions("housekeeping-app-web").get(0);
            assertEquals(Integer.valueOf(1), displayedVersion.getVersionNo());
            assertEquals(Boolean.TRUE, displayedVersion.getAccessTokenConfigured());
            assertEquals("token-current", mapper.findControlPolicyVersion(
                    "housekeeping-app-web", Integer.valueOf(1)).getAccessTokenValue());
            assertEquals(Integer.valueOf(1),
                    mapper.findLatestControlPolicyVersionNo("housekeeping-app-web"));

            assertEquals(1, mapper.insertControlPolicyReview(review("CPR-TEST-001")));
            assertEquals("CPR-TEST-001",
                    mapper.findPendingControlPolicyReviewForUpdate("housekeeping-app-web").getRequestNo());
            assertEquals("token-next",
                    mapper.findControlPolicyReviewForUpdate("CPR-TEST-001").getAccessTokenValue());

            current.setSourceAppName("家政 App 收银台新版");
            current.setAccessTokenValue("token-next");
            current.setSelfCheckStatus("WARN");
            current.setSelfCheckStatusType("warn");
            current.setSelfCheckMessage("配置已变更，待重新执行支付控制策略自检");
            assertEquals(1, mapper.updateControlPolicy(current));

            PaymentControlPolicyEntity created = policy("new-app");
            assertEquals(1, mapper.insertControlPolicy(created));
            assertEquals("token-current",
                    mapper.findControlPolicyBySourceAppIdForUpdate("new-app").getAccessTokenValue());

            assertEquals(1, mapper.completeControlPolicyReview(
                    "CPR-TEST-001", "APPROVED", "success", "checker-b", "确认变更"));
            assertNull(mapper.findPendingControlPolicyReviewForUpdate("housekeeping-app-web"));
            assertEquals(1, mapper.insertControlPolicyReview(review("CPR-TEST-002")));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            java.util.List<PaymentControlPolicyReviewDTO> reviews =
                    session.getMapper(PaymentConfigMapper.class).findControlPolicyReviews();
            PaymentControlPolicyReviewDTO completed = reviews.stream()
                    .filter(item -> "CPR-TEST-001".equals(item.getRequestNo()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("APPROVED", completed.getReviewStatus());
            assertEquals(Boolean.TRUE, completed.getAccessTokenConfigured());
            assertEquals("SELF_DECLARED", completed.getIdentityTrustLevel());
            assertEquals(2, reviews.size());
        }
    }

    private PaymentControlPolicyReviewEntity review(String requestNo) {
        PaymentControlPolicyReviewEntity review = new PaymentControlPolicyReviewEntity();
        review.setRequestNo(requestNo);
        review.setSourceAppId("housekeeping-app-web");
        review.setBaseVersionNo(Integer.valueOf(1));
        review.setOperationType("UPDATE");
        review.setSourceAppName("家政 App 收银台新版");
        review.setAllowedPaymentMethods("微信支付,支付宝");
        review.setAllowedChannelCodes("wx_h5,alipay_h5");
        review.setAllowedMerchantNos("MCH_WX_10001,MCH_ALI_20001");
        review.setMinuteSubmitLimit(Integer.valueOf(40));
        review.setInterfaceMinuteSubmitLimit(Integer.valueOf(20));
        review.setTokenAuthRequired("开启");
        review.setAccessTokenValue("token-next");
        review.setStrictMode("开启");
        review.setPendingSourceAppId("housekeeping-app-web");
        review.setBaseFingerprint(String.join("", java.util.Collections.nCopies(64, "a")));
        review.setCandidateStatus("ENABLED");
        review.setCandidateStatusType("success");
        review.setRequester("maker-a");
        review.setRequestReason("调整来源应用准入");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentControlPolicyVersionEntity version(PaymentControlPolicyEntity policy) {
        PaymentControlPolicyVersionEntity version = new PaymentControlPolicyVersionEntity();
        version.setSourceAppId(policy.getSourceAppId());
        version.setVersionNo(Integer.valueOf(1));
        version.setSourceAppName(policy.getSourceAppName());
        version.setAllowedPaymentMethods(policy.getAllowedPaymentMethods());
        version.setAllowedChannelCodes(policy.getAllowedChannelCodes());
        version.setAllowedMerchantNos(policy.getAllowedMerchantNos());
        version.setMinuteSubmitLimit(policy.getMinuteSubmitLimit());
        version.setInterfaceMinuteSubmitLimit(policy.getInterfaceMinuteSubmitLimit());
        version.setTokenAuthRequired(policy.getTokenAuthRequired());
        version.setAccessTokenValue(policy.getAccessTokenValue());
        version.setStrictMode(policy.getStrictMode());
        version.setStatus(policy.getStatus());
        version.setStatusType(policy.getStatusType());
        version.setChangeType("BASELINE");
        version.setChangeReason("测试基线");
        return version;
    }

    private PaymentControlPolicyEntity policy(String sourceAppId) {
        PaymentControlPolicyEntity policy = new PaymentControlPolicyEntity();
        policy.setSourceAppId(sourceAppId);
        policy.setSourceAppName("家政 App 收银台");
        policy.setAllowedPaymentMethods("微信支付,支付宝");
        policy.setAllowedChannelCodes("wx_h5,alipay_h5");
        policy.setAllowedMerchantNos("MCH_WX_10001,MCH_ALI_20001");
        policy.setMinuteSubmitLimit(Integer.valueOf(40));
        policy.setInterfaceMinuteSubmitLimit(Integer.valueOf(20));
        policy.setTokenAuthRequired("开启");
        policy.setAccessTokenValue("token-current");
        policy.setStrictMode("开启");
        policy.setSelfCheckStatus("PASS");
        policy.setSelfCheckStatusType("success");
        policy.setSelfCheckMessage("自检通过");
        policy.setStatus("ENABLED");
        policy.setStatusType("success");
        return policy;
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_control_policy_review");
            statement.execute("DROP TABLE IF EXISTS t_payment_control_policy_version");
            statement.execute("DROP TABLE IF EXISTS t_payment_control_policy");
            statement.execute("CREATE TABLE t_payment_control_policy ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, source_app_id VARCHAR(64) NOT NULL UNIQUE, "
                    + "source_app_name VARCHAR(128) NOT NULL, allowed_payment_methods VARCHAR(255) NOT NULL, "
                    + "allowed_channel_codes VARCHAR(255) NOT NULL, allowed_merchant_nos VARCHAR(255) NOT NULL, "
                    + "minute_submit_limit INT NOT NULL, interface_minute_submit_limit INT NOT NULL, "
                    + "token_auth_required VARCHAR(32) NOT NULL, access_token_value VARCHAR(128) NOT NULL, "
                    + "strict_mode VARCHAR(32) NOT NULL, self_check_status VARCHAR(32) NOT NULL, "
                    + "self_check_status_type VARCHAR(32) NOT NULL, self_check_message VARCHAR(255) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_control_policy_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "source_app_id VARCHAR(64) NOT NULL, base_version_no INT, source_version_no INT, "
                    + "operation_type VARCHAR(32) NOT NULL, "
                    + "source_app_name VARCHAR(128) NOT NULL, allowed_payment_methods VARCHAR(255) NOT NULL, "
                    + "allowed_channel_codes VARCHAR(255) NOT NULL, allowed_merchant_nos VARCHAR(255) NOT NULL, "
                    + "minute_submit_limit INT NOT NULL, interface_minute_submit_limit INT NOT NULL, "
                    + "token_auth_required VARCHAR(32) NOT NULL, access_token_value VARCHAR(128) NOT NULL, "
                    + "strict_mode VARCHAR(32) NOT NULL, pending_source_app_id VARCHAR(64) UNIQUE, "
                    + "base_fingerprint VARCHAR(64), candidate_status VARCHAR(32) NOT NULL, "
                    + "candidate_status_type VARCHAR(32) NOT NULL, requester VARCHAR(64) NOT NULL, "
                    + "request_reason VARCHAR(255) NOT NULL, review_status VARCHAR(32) NOT NULL, "
                    + "review_status_type VARCHAR(32) NOT NULL, reviewer VARCHAR(64), "
                    + "review_comment VARCHAR(255), created_at DATETIME NOT NULL, reviewed_at DATETIME, "
                    + "updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_control_policy_version ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, source_app_id VARCHAR(64) NOT NULL, "
                    + "version_no INT NOT NULL, source_app_name VARCHAR(128) NOT NULL, "
                    + "allowed_payment_methods VARCHAR(255) NOT NULL, allowed_channel_codes VARCHAR(255) NOT NULL, "
                    + "allowed_merchant_nos VARCHAR(255) NOT NULL, minute_submit_limit INT NOT NULL, "
                    + "interface_minute_submit_limit INT NOT NULL, token_auth_required VARCHAR(32) NOT NULL, "
                    + "access_token_value VARCHAR(128) NOT NULL, strict_mode VARCHAR(32) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, change_type VARCHAR(32) NOT NULL, "
                    + "source_version_no INT, change_reason VARCHAR(255) NOT NULL, created_at DATETIME NOT NULL, "
                    + "UNIQUE(source_app_id, version_no))");
            statement.execute("INSERT INTO t_payment_control_policy ("
                    + "source_app_id, source_app_name, allowed_payment_methods, allowed_channel_codes, "
                    + "allowed_merchant_nos, minute_submit_limit, interface_minute_submit_limit, "
                    + "token_auth_required, access_token_value, strict_mode, self_check_status, "
                    + "self_check_status_type, self_check_message, status, status_type, updated_at) VALUES ("
                    + "'housekeeping-app-web', '家政 App 收银台', '微信支付,支付宝', 'wx_h5,alipay_h5', "
                    + "'MCH_WX_10001,MCH_ALI_20001', 40, 20, '开启', 'token-current', '开启', "
                    + "'PASS', 'success', '自检通过', 'ENABLED', 'success', NOW())");
            session.commit();
        }
    }
}
