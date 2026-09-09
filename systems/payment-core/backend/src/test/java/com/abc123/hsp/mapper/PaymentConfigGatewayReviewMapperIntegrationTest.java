package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentGatewayReviewDTO;
import com.abc123.hsp.entity.PaymentGatewayConfigEntity;
import com.abc123.hsp.entity.PaymentGatewayReviewEntity;
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

/** 支付网关状态复核 Mapper 的 MySQL 兼容 SQL 集成测试。 */
class PaymentConfigGatewayReviewMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_gateway_review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldLockPersistCompleteReleaseAndListGatewayReview() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentGatewayConfigEntity gateway = mapper.findGatewayByCodeForUpdate("GATEWAY_WX_ACQ");
            assertEquals("wx_h5", gateway.getChannelScope());
            assertEquals(Integer.valueOf(3000), gateway.getTimeoutMs());
            assertEquals(1, mapper.insertGatewayReview(review("GWR-TEST-001")));
            assertEquals("GWR-TEST-001",
                    mapper.findPendingGatewayReviewForUpdate("GATEWAY_WX_ACQ").getRequestNo());
            assertEquals("DISABLED",
                    mapper.findGatewayReviewForUpdate("GWR-TEST-001").getCandidateStatus());
            gateway.setStatus("DISABLED");
            gateway.setStatusType("danger");
            assertEquals(1, mapper.updateGateway(gateway));
            PaymentGatewayConfigEntity createdGateway = gateway("GATEWAY_NEW_ACQ");
            assertEquals(1, mapper.insertGateway(createdGateway));
            assertEquals("新收单网关",
                    mapper.findGatewayByCodeForUpdate("GATEWAY_NEW_ACQ").getGatewayName());
            assertEquals(1, mapper.completeGatewayReview(
                    "GWR-TEST-001", "APPROVED", "success", "checker-b", "确认停用"));
            assertNull(mapper.findPendingGatewayReviewForUpdate("GATEWAY_WX_ACQ"));
            assertEquals(1, mapper.insertGatewayReview(review("GWR-TEST-002")));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            java.util.List<PaymentGatewayReviewDTO> reviews =
                    session.getMapper(PaymentConfigMapper.class).findGatewayReviews();
            PaymentGatewayReviewDTO completed = reviews.stream()
                    .filter(item -> "GWR-TEST-001".equals(item.getRequestNo()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("APPROVED", completed.getReviewStatus());
            assertEquals("STATUS_CHANGE", completed.getOperationType());
            assertEquals("https://gateway.example.test", completed.getApiBaseUrl());
            assertEquals("SELF_DECLARED", completed.getIdentityTrustLevel());
            assertEquals(2, reviews.size());
        }
    }

    private PaymentGatewayReviewEntity review(String requestNo) {
        PaymentGatewayReviewEntity review = new PaymentGatewayReviewEntity();
        review.setRequestNo(requestNo);
        review.setGatewayCode("GATEWAY_WX_ACQ");
        review.setOperationType("STATUS_CHANGE");
        review.setGatewayName("微信收单网关");
        review.setAccessMode("直连");
        review.setChannelScope("wx_h5");
        review.setEnvironmentScope("生产+预发");
        review.setApiBaseUrl("https://gateway.example.test");
        review.setProtocolType("JSON");
        review.setSignAlgorithm("RSA2");
        review.setCertificateAlias("wx-cert-v1");
        review.setCertificateStatus("VALID");
        review.setCertificateStatusType("success");
        review.setReleaseStage("全量");
        review.setGrayStrategy("全部商户");
        review.setCallbackWhitelist("10.0.0.0/8");
        review.setAdapterRegistry("submit=wx,query=wx");
        review.setTimeoutMs(Integer.valueOf(3000));
        review.setRetryPolicy("查询重试2次");
        review.setPriority(Integer.valueOf(1));
        review.setPendingGatewayCode("GATEWAY_WX_ACQ");
        review.setBaseFingerprint(String.join("", java.util.Collections.nCopies(64, "a")));
        review.setCandidateStatus("DISABLED");
        review.setCandidateStatusType("danger");
        review.setRequester("maker-a");
        review.setRequestReason("暂停网关承接");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentGatewayConfigEntity gateway(String gatewayCode) {
        PaymentGatewayConfigEntity gateway = new PaymentGatewayConfigEntity();
        gateway.setGatewayCode(gatewayCode);
        gateway.setGatewayName("新收单网关");
        gateway.setAccessMode("直连");
        gateway.setChannelScope("new_h5");
        gateway.setEnvironmentScope("预发+沙箱");
        gateway.setApiBaseUrl("https://gateway-new.example.test");
        gateway.setProtocolType("HTTP+JSON");
        gateway.setSignAlgorithm("HMAC-SHA256");
        gateway.setCertificateAlias("new-cert-v1");
        gateway.setCertificateStatus("VALID");
        gateway.setCertificateStatusType("success");
        gateway.setReleaseStage("灰度");
        gateway.setGrayStrategy("仅测试商户");
        gateway.setCallbackWhitelist("10.1.0.0/16");
        gateway.setAdapterRegistry("submit=new,query=new-query");
        gateway.setTimeoutMs(Integer.valueOf(2500));
        gateway.setRetryPolicy("失败重试1次");
        gateway.setStatus("DISABLED");
        gateway.setStatusType("danger");
        gateway.setPriority(Integer.valueOf(9));
        return gateway;
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_gateway_review");
            statement.execute("DROP TABLE IF EXISTS t_payment_gateway_config");
            statement.execute("CREATE TABLE t_payment_gateway_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, gateway_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "gateway_name VARCHAR(128) NOT NULL, access_mode VARCHAR(64) NOT NULL, "
                    + "channel_scope VARCHAR(256) NOT NULL, environment_scope VARCHAR(128) NOT NULL, "
                    + "api_base_url VARCHAR(256) NOT NULL, protocol_type VARCHAR(64) NOT NULL, "
                    + "sign_algorithm VARCHAR(64) NOT NULL, certificate_alias VARCHAR(128) NOT NULL, "
                    + "certificate_status VARCHAR(32) NOT NULL, certificate_status_type VARCHAR(32) NOT NULL, "
                    + "release_stage VARCHAR(64) NOT NULL, gray_strategy VARCHAR(256) NOT NULL, "
                    + "callback_whitelist VARCHAR(256) NOT NULL, adapter_registry VARCHAR(256) NOT NULL, "
                    + "timeout_ms INT NOT NULL, retry_policy VARCHAR(128) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, "
                    + "priority INT NOT NULL, updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_gateway_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "gateway_code VARCHAR(64) NOT NULL, operation_type VARCHAR(32) NOT NULL, "
                    + "gateway_name VARCHAR(128) NOT NULL, access_mode VARCHAR(64) NOT NULL, "
                    + "channel_scope VARCHAR(256) NOT NULL, environment_scope VARCHAR(128) NOT NULL, "
                    + "api_base_url VARCHAR(256) NOT NULL, protocol_type VARCHAR(64) NOT NULL, "
                    + "sign_algorithm VARCHAR(64) NOT NULL, certificate_alias VARCHAR(128) NOT NULL, "
                    + "certificate_status VARCHAR(32) NOT NULL, certificate_status_type VARCHAR(32) NOT NULL, "
                    + "release_stage VARCHAR(64) NOT NULL, gray_strategy VARCHAR(256) NOT NULL, "
                    + "callback_whitelist VARCHAR(256) NOT NULL, adapter_registry VARCHAR(256) NOT NULL, "
                    + "timeout_ms INT NOT NULL, retry_policy VARCHAR(128) NOT NULL, priority INT NOT NULL, "
                    + "pending_gateway_code VARCHAR(64) UNIQUE, base_fingerprint VARCHAR(64), "
                    + "candidate_status VARCHAR(32) NOT NULL, "
                    + "candidate_status_type VARCHAR(32) NOT NULL, requester VARCHAR(64) NOT NULL, "
                    + "request_reason VARCHAR(255) NOT NULL, review_status VARCHAR(32) NOT NULL, "
                    + "review_status_type VARCHAR(32) NOT NULL, reviewer VARCHAR(64), "
                    + "review_comment VARCHAR(255), created_at DATETIME NOT NULL, reviewed_at DATETIME, "
                    + "updated_at DATETIME NOT NULL)");
            statement.execute("INSERT INTO t_payment_gateway_config ("
                    + "gateway_code, gateway_name, access_mode, channel_scope, environment_scope, "
                    + "api_base_url, protocol_type, sign_algorithm, certificate_alias, certificate_status, "
                    + "certificate_status_type, release_stage, gray_strategy, callback_whitelist, "
                    + "adapter_registry, timeout_ms, retry_policy, status, status_type, priority, updated_at) VALUES ("
                    + "'GATEWAY_WX_ACQ', '微信收单网关', '直连', 'wx_h5', '生产+预发', "
                    + "'https://gateway.example.test', 'JSON', 'RSA2', 'wx-cert-v1', 'VALID', "
                    + "'success', '全量', '全部商户', '10.0.0.0/8', 'submit=wx,query=wx', "
                    + "3000, '查询重试2次', 'ENABLED', 'success', 1, NOW())");
            session.commit();
        }
    }
}
