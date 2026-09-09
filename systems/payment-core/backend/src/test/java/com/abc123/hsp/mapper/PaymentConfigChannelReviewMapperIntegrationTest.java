package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentChannelReviewDTO;
import com.abc123.hsp.entity.PaymentChannelConfigEntity;
import com.abc123.hsp.entity.PaymentChannelReviewEntity;
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

/** 支付渠道状态复核 Mapper 的 MySQL 兼容 SQL 集成测试。 */
class PaymentConfigChannelReviewMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_channel_review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldLockPersistCompleteReleaseAndListChannelReview() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentChannelConfigEntity channel = mapper.findChannelByCodeForUpdate("wx_h5");
            assertEquals("MCH-WX-001", channel.getMerchantNo());
            assertEquals("secret", channel.getCallbackSecret());

            assertEquals(1, mapper.insertChannelReview(review("CSR-TEST-001")));
            assertEquals("CSR-TEST-001",
                    mapper.findPendingChannelReviewForUpdate("wx_h5").getRequestNo());
            assertEquals("DISABLED",
                    mapper.findChannelReviewForUpdate("CSR-TEST-001").getCandidateStatus());
            assertEquals(1, mapper.completeChannelReview(
                    "CSR-TEST-001", "APPROVED", "success", "checker-b", "确认停用"));
            assertNull(mapper.findPendingChannelReviewForUpdate("wx_h5"));
            assertEquals(1, mapper.insertChannelReview(review("CSR-TEST-002")));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            java.util.List<PaymentChannelReviewDTO> reviews =
                    session.getMapper(PaymentConfigMapper.class).findChannelReviews();
            PaymentChannelReviewDTO completed = reviews.stream()
                    .filter(item -> "CSR-TEST-001".equals(item.getRequestNo()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("APPROVED", completed.getReviewStatus());
            assertEquals("SELF_DECLARED", completed.getIdentityTrustLevel());
            assertEquals(2, reviews.size());
        }
    }

    private PaymentChannelReviewEntity review(String requestNo) {
        PaymentChannelReviewEntity review = new PaymentChannelReviewEntity();
        review.setRequestNo(requestNo);
        review.setChannelCode("wx_h5");
        review.setChannelName("微信H5支付");
        review.setPaymentMethod("微信支付");
        review.setPendingChannelCode("wx_h5");
        review.setBaseFingerprint(repeat("a", 64));
        review.setCandidateStatus("DISABLED");
        review.setCandidateStatusType("danger");
        review.setRequester("maker-a");
        review.setRequestReason("暂停渠道承接");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_review");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_config");
            statement.execute("CREATE TABLE t_payment_channel_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "channel_name VARCHAR(128) NOT NULL, payment_method VARCHAR(64) NOT NULL, "
                    + "merchant_no VARCHAR(128) NOT NULL, merchant_app_id VARCHAR(128) NOT NULL, "
                    + "callback_secret VARCHAR(256) NOT NULL, callback_sign_algorithm VARCHAR(32) NOT NULL, "
                    + "callback_public_key CLOB, callback_notify_url VARCHAR(256) NOT NULL, "
                    + "certificate_profile VARCHAR(128) NOT NULL, notify_sign_window_sec INT NOT NULL, "
                    + "refund_window_days INT NOT NULL, risk_control_tag VARCHAR(128) NOT NULL, "
                    + "scene_scope VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, "
                    + "status_type VARCHAR(32) NOT NULL, daily_limit DECIMAL(18,2) NOT NULL, "
                    + "priority INT NOT NULL, route_weight INT NOT NULL, updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_channel_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "channel_code VARCHAR(64) NOT NULL, channel_name VARCHAR(128) NOT NULL, "
                    + "payment_method VARCHAR(64) NOT NULL, pending_channel_code VARCHAR(64) UNIQUE, "
                    + "base_fingerprint VARCHAR(64) NOT NULL, candidate_status VARCHAR(32) NOT NULL, "
                    + "candidate_status_type VARCHAR(32) NOT NULL, requester VARCHAR(64) NOT NULL, "
                    + "request_reason VARCHAR(255) NOT NULL, review_status VARCHAR(32) NOT NULL, "
                    + "review_status_type VARCHAR(32) NOT NULL, reviewer VARCHAR(64), "
                    + "review_comment VARCHAR(255), created_at DATETIME NOT NULL, reviewed_at DATETIME, "
                    + "updated_at DATETIME NOT NULL)");
            statement.execute("INSERT INTO t_payment_channel_config ("
                    + "channel_code, channel_name, payment_method, merchant_no, merchant_app_id, "
                    + "callback_secret, callback_sign_algorithm, callback_public_key, callback_notify_url, "
                    + "certificate_profile, notify_sign_window_sec, refund_window_days, risk_control_tag, "
                    + "scene_scope, status, status_type, daily_limit, priority, route_weight, updated_at) VALUES ("
                    + "'wx_h5', '微信H5支付', '微信支付', 'MCH-WX-001', 'APP-WX-001', "
                    + "'secret', 'HMAC-SHA256', 'public-key', 'https://example.test/notify', "
                    + "'CERT-WX', 300, 180, '实名校验', '家政服务', 'ENABLED', 'success', "
                    + "500000.00, 1, 100, NOW())");
            session.commit();
        }
    }

    private String repeat(String value, int count) {
        return String.join("", java.util.Collections.nCopies(count, value));
    }
}
