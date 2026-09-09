package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.abc123.hsp.dto.PaymentProtocolVersionDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewDTO;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.entity.PaymentProtocolReviewEntity;
import com.abc123.hsp.entity.PaymentProtocolVersionEntity;
import java.io.InputStream;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
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
 * 支付协议版本 Mapper 的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentConfigProtocolVersionMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_protocol_version;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldPersistReadAndRestoreProtocolVersion() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentProtocolConfigEntity current = mapper.findProtocolByCodeForUpdate("PROTO_TEST_V1");
            assertNotNull(current);
            assertEquals(Integer.valueOf(1), mapper.findLatestProtocolVersionNo("PROTO_TEST_V1"));

            PaymentProtocolVersionEntity version = buildVersion(current, 2);
            version.setTemplateVersion("v1.1.0");
            version.setChangeReason("更新协议正文与模板版本");
            assertEquals(1, mapper.insertProtocolVersion(version));

            PaymentProtocolVersionEntity stored = mapper.findProtocolVersion("PROTO_TEST_V1", 2);
            assertEquals("v1.1.0", stored.getTemplateVersion());
            List<PaymentProtocolVersionDTO> versions = mapper.findProtocolVersions("PROTO_TEST_V1");
            assertEquals(2, versions.size());
            assertEquals(Integer.valueOf(2), versions.get(0).getVersionNo());
            assertEquals("更新协议正文与模板版本", versions.get(0).getChangeReason());

            current.setTemplateVersion(stored.getTemplateVersion());
            assertEquals(1, mapper.updateProtocol(current));
            assertEquals("v1.1.0", mapper.findProtocolByCode("PROTO_TEST_V1").getTemplateVersion());
            session.commit();
        }
    }

    @Test
    void shouldPersistLockListAndCompleteProtocolReview() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentProtocolConfigEntity current = mapper.findProtocolByCode("PROTO_TEST_V1");
            PaymentProtocolReviewEntity review = buildReview(current, "PRV-001");

            assertEquals(1, mapper.insertProtocolReview(review));
            assertEquals(1, mapper.countPendingProtocolReviews("PROTO_TEST_V1"));
            PaymentProtocolReviewEntity locked = mapper.findProtocolReviewForUpdate("PRV-001");
            assertEquals("v1.1.0", locked.getTemplateVersion());
            assertEquals("protocol-maker", locked.getRequester());

            List<PaymentProtocolReviewDTO> reviews = mapper.findProtocolReviews();
            assertEquals(1, reviews.size());
            assertEquals("SELF_DECLARED", reviews.get(0).getIdentityTrustLevel());
            assertEquals(1, mapper.completeProtocolReview(
                    "PRV-001", "APPROVED", "success", "protocol-checker", "已核对协议条款"));
            assertEquals(0, mapper.countPendingProtocolReviews("PROTO_TEST_V1"));

            PaymentProtocolReviewEntity nextReview = buildReview(current, "PRV-002");
            assertEquals(1, mapper.insertProtocolReview(nextReview));
            session.commit();
        }
    }

    private PaymentProtocolVersionEntity buildVersion(PaymentProtocolConfigEntity protocol, int versionNo) {
        PaymentProtocolVersionEntity version = new PaymentProtocolVersionEntity();
        version.setProtocolCode(protocol.getProtocolCode());
        version.setVersionNo(Integer.valueOf(versionNo));
        version.setProtocolName(protocol.getProtocolName());
        version.setProtocolType(protocol.getProtocolType());
        version.setProtocolTypeName(protocol.getProtocolTypeName());
        version.setTemplateCode(protocol.getTemplateCode());
        version.setTemplateName(protocol.getTemplateName());
        version.setTemplateVersion(protocol.getTemplateVersion());
        version.setSignMode(protocol.getSignMode());
        version.setSignElementSpec(protocol.getSignElementSpec());
        version.setESignatureProvider(protocol.getESignatureProvider());
        version.setSceneScope(protocol.getSceneScope());
        version.setChannelScope(protocol.getChannelScope());
        version.setMerchantAckRequired(protocol.getMerchantAckRequired());
        version.setRiskControlTag(protocol.getRiskControlTag());
        version.setProtocolBody(protocol.getProtocolBody());
        version.setStatus(protocol.getStatus());
        version.setStatusType(protocol.getStatusType());
        version.setPriority(protocol.getPriority());
        version.setChangeType("UPDATE");
        version.setChangeReason("协议变更");
        return version;
    }

    private PaymentProtocolReviewEntity buildReview(PaymentProtocolConfigEntity protocol, String requestNo) {
        PaymentProtocolReviewEntity review = new PaymentProtocolReviewEntity();
        review.setRequestNo(requestNo);
        review.setProtocolCode(protocol.getProtocolCode());
        review.setBaseVersionNo(Integer.valueOf(1));
        review.setOperationType("UPDATE");
        review.setProtocolName(protocol.getProtocolName());
        review.setProtocolType(protocol.getProtocolType());
        review.setProtocolTypeName(protocol.getProtocolTypeName());
        review.setTemplateCode(protocol.getTemplateCode());
        review.setTemplateName(protocol.getTemplateName());
        review.setTemplateVersion("v1.1.0");
        review.setSignMode(protocol.getSignMode());
        review.setSignElementSpec(protocol.getSignElementSpec());
        review.setESignatureProvider(protocol.getESignatureProvider());
        review.setSceneScope(protocol.getSceneScope());
        review.setChannelScope(protocol.getChannelScope());
        review.setMerchantAckRequired(protocol.getMerchantAckRequired());
        review.setRiskControlTag(protocol.getRiskControlTag());
        review.setProtocolBody(protocol.getProtocolBody());
        review.setStatus(protocol.getStatus());
        review.setStatusType(protocol.getStatusType());
        review.setPriority(protocol.getPriority());
        review.setRequester("protocol-maker");
        review.setRequestReason("更新协议模板");
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
            statement.execute("DROP TABLE IF EXISTS t_payment_protocol_version");
            statement.execute("DROP TABLE IF EXISTS t_payment_protocol_review");
            statement.execute("DROP TABLE IF EXISTS t_payment_protocol_config");
            statement.execute("CREATE TABLE t_payment_protocol_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, protocol_code VARCHAR(64) UNIQUE, "
                    + "protocol_name VARCHAR(128), protocol_type VARCHAR(64), protocol_type_name VARCHAR(64), "
                    + "template_code VARCHAR(64), template_name VARCHAR(128), template_version VARCHAR(32), "
                    + "sign_mode VARCHAR(64), sign_element_spec VARCHAR(255), e_signature_provider VARCHAR(64), "
                    + "scene_scope VARCHAR(128), channel_scope VARCHAR(128), merchant_ack_required VARCHAR(32), "
                    + "risk_control_tag VARCHAR(128), protocol_body CLOB, status VARCHAR(32), status_type VARCHAR(32), "
                    + "priority INT, updated_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_protocol_version ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, protocol_code VARCHAR(64), version_no INT, "
                    + "protocol_name VARCHAR(128), protocol_type VARCHAR(64), protocol_type_name VARCHAR(64), "
                    + "template_code VARCHAR(64), template_name VARCHAR(128), template_version VARCHAR(32), "
                    + "sign_mode VARCHAR(64), sign_element_spec VARCHAR(255), e_signature_provider VARCHAR(64), "
                    + "scene_scope VARCHAR(128), channel_scope VARCHAR(128), merchant_ack_required VARCHAR(32), "
                    + "risk_control_tag VARCHAR(128), protocol_body CLOB, status VARCHAR(32), status_type VARCHAR(32), "
                    + "priority INT, change_type VARCHAR(32), source_version_no INT, change_reason VARCHAR(255), "
                    + "created_at DATETIME, UNIQUE(protocol_code, version_no))");
            statement.execute("CREATE TABLE t_payment_protocol_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64) UNIQUE, protocol_code VARCHAR(64), "
                    + "pending_protocol_code VARCHAR(64) UNIQUE, base_version_no INT, source_version_no INT, "
                    + "operation_type VARCHAR(32), protocol_name VARCHAR(128), protocol_type VARCHAR(64), "
                    + "protocol_type_name VARCHAR(64), template_code VARCHAR(64), template_name VARCHAR(128), "
                    + "template_version VARCHAR(32), sign_mode VARCHAR(64), sign_element_spec VARCHAR(255), "
                    + "e_signature_provider VARCHAR(64), scene_scope VARCHAR(128), channel_scope VARCHAR(128), "
                    + "merchant_ack_required VARCHAR(32), risk_control_tag VARCHAR(128), protocol_body CLOB, "
                    + "status VARCHAR(32), status_type VARCHAR(32), priority INT, requester VARCHAR(64), "
                    + "request_reason VARCHAR(255), review_status VARCHAR(32), review_status_type VARCHAR(32), "
                    + "reviewer VARCHAR(64), review_comment VARCHAR(255), created_at DATETIME, reviewed_at DATETIME, "
                    + "updated_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_protocol_config "
                    + "(protocol_code, protocol_name, protocol_type, protocol_type_name, template_code, template_name, "
                    + "template_version, sign_mode, sign_element_spec, e_signature_provider, scene_scope, channel_scope, "
                    + "merchant_ack_required, risk_control_tag, protocol_body, status, status_type, priority, updated_at) VALUES "
                    + "('PROTO_TEST_V1', '测试协议', 'PAYMENT_SIGN', '支付签约协议', 'TPL_TEST_V1', '测试模板', "
                    + "'v1.0.0', '线上签约', '姓名/手机号', 'E-SIGN-CLOUD', '家政服务', 'wx_h5', "
                    + "'需要', '实名校验', '用户授权平台发起收款。', 'ENABLED', 'success', 1, NOW())");
            statement.executeUpdate("INSERT INTO t_payment_protocol_version "
                    + "(protocol_code, version_no, protocol_name, protocol_type, protocol_type_name, template_code, "
                    + "template_name, template_version, sign_mode, sign_element_spec, e_signature_provider, scene_scope, "
                    + "channel_scope, merchant_ack_required, risk_control_tag, protocol_body, status, status_type, priority, "
                    + "change_type, source_version_no, change_reason, created_at) VALUES "
                    + "('PROTO_TEST_V1', 1, '测试协议', 'PAYMENT_SIGN', '支付签约协议', 'TPL_TEST_V1', '测试模板', "
                    + "'v1.0.0', '线上签约', '姓名/手机号', 'E-SIGN-CLOUD', '家政服务', 'wx_h5', '需要', "
                    + "'实名校验', '用户授权平台发起收款。', 'ENABLED', 'success', 1, 'BASELINE', NULL, "
                    + "'初始化协议基线', NOW())");
            session.commit();
        }
    }

    /**
     * H2 测试环境中的 MySQL DATE_FORMAT 等价实现。
     */
    public static String dateFormat(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
