package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewDTO;
import com.abc123.hsp.entity.PaymentReturnCodeImportReviewEntity;
import com.abc123.hsp.entity.PaymentReturnCodeReviewEntity;
import java.io.InputStream;
import java.sql.ResultSet;
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

/**
 * 渠道返回码映射查询的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentConfigReturnCodeMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_return_code;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldOnlyResolveEnabledAndActiveMapping() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);

            PaymentChannelReturnCodeConfigDTO active =
                    mapper.findActiveReturnCodeMapping("wx_h5", "USERPAYING");

            assertEquals("PAYMENT-CHANNEL-1001", active.getStandardizedCode());
            assertEquals("v2026.07.1", active.getMappingVersion());
            assertNull(mapper.findActiveReturnCodeMapping("wx_h5", "DISABLED_CODE"));
            assertNull(mapper.findActiveReturnCodeMapping("wx_h5", "ARCHIVED_CODE"));
            assertNull(mapper.findActiveReturnCodeMapping("wx_h5", "UNKNOWN_CODE"));
        }
    }

    @Test
    void shouldLockUpsertAndRecordImportBatch() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            assertEquals(3, mapper.findReturnCodeMappingsForUpdate().size());
            PaymentChannelReturnCodeConfigDTO candidate = new PaymentChannelReturnCodeConfigDTO();
            candidate.setChannelCode("wx_h5");
            candidate.setChannelReturnCode("USERPAYING");
            candidate.setStandardizedCode("PAYMENT-CHANNEL-1099");
            candidate.setStandardizedMessage("等待用户完成支付");
            candidate.setHandlingSuggestion("等待回调并按需主动查单");
            candidate.setRetryable("是");
            candidate.setManualInterventionRequired("否");
            candidate.setMappingVersion("v2026.09.2");
            candidate.setStatus("ENABLED");
            candidate.setStatusType("success");
            candidate.setPriority(Integer.valueOf(9));

            assertEquals(2, mapper.upsertReturnCodeMapping(candidate));
            assertEquals(1, mapper.insertReturnCodeImportBatch(
                    "RCI-TEST-001", "ops-user", "同步渠道码表", repeat("a", 64),
                    repeat("b", 64), repeat("c", 64), 1, 0, 1, 0, 1, "APPLIED", "success"));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentChannelReturnCodeConfigDTO updated = session.getMapper(PaymentConfigMapper.class)
                    .findActiveReturnCodeMapping("wx_h5", "USERPAYING");
            assertEquals("PAYMENT-CHANNEL-1099", updated.getStandardizedCode());
            assertEquals(Integer.valueOf(9), updated.getPriority());
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT applied_count, batch_status FROM t_payment_return_code_import_batch "
                            + "WHERE batch_no = 'RCI-TEST-001'")) {
                resultSet.next();
                assertEquals(1, resultSet.getInt("applied_count"));
                assertEquals("APPLIED", resultSet.getString("batch_status"));
            }
        }
    }

    @Test
    void shouldNeverModifyArchivedMappingThroughUpsertConflict() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentChannelReturnCodeConfigDTO candidate = new PaymentChannelReturnCodeConfigDTO();
            candidate.setChannelCode("wx_h5");
            candidate.setChannelReturnCode("ARCHIVED_CODE");
            candidate.setStandardizedCode("PAYMENT-CHANNEL-9999");
            candidate.setStandardizedMessage("不应写入");
            candidate.setHandlingSuggestion("不应写入");
            candidate.setRetryable("否");
            candidate.setManualInterventionRequired("是");
            candidate.setMappingVersion("v9999");
            candidate.setStatus("DISABLED");
            candidate.setStatusType("danger");
            candidate.setPriority(Integer.valueOf(999));

            mapper.upsertReturnCodeMapping(candidate);
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT standardized_code, standardized_message, mapping_version, archive_status, "
                             + "status, priority FROM t_payment_channel_return_code_map "
                             + "WHERE channel_code = 'wx_h5' AND channel_return_code = 'ARCHIVED_CODE'")) {
            resultSet.next();
            assertEquals("PAYMENT-CHANNEL-1001", resultSet.getString("standardized_code"));
            assertEquals("用户支付中", resultSet.getString("standardized_message"));
            assertEquals("v2026.07.1", resultSet.getString("mapping_version"));
            assertEquals("ARCHIVED", resultSet.getString("archive_status"));
            assertEquals("ENABLED", resultSet.getString("status"));
            assertEquals(1, resultSet.getInt("priority"));
        }
    }

    @Test
    void shouldPersistLockCompleteAndReleaseReturnCodeImportReview() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentReturnCodeImportReviewEntity review = new PaymentReturnCodeImportReviewEntity();
            review.setRequestNo("RCR-TEST-001");
            review.setPendingImportKey("RETURN_CODE_IMPORT");
            review.setCsvContent("channelCode,channelReturnCode\nwx_h5,USERPAYING\n");
            review.setSourceSha256(repeat("a", 64));
            review.setBaselineFingerprint(repeat("b", 64));
            review.setTotalCount(Integer.valueOf(1));
            review.setCreateCount(Integer.valueOf(0));
            review.setUpdateCount(Integer.valueOf(1));
            review.setUnchangedCount(Integer.valueOf(0));
            review.setRequester("maker-a");
            review.setRequestReason("同步渠道码表");
            review.setReviewStatus("PENDING_REVIEW");
            review.setReviewStatusType("warn");

            assertEquals(1, mapper.insertReturnCodeImportReview(review));
            assertEquals("RCR-TEST-001", mapper.findPendingReturnCodeImportReviewForUpdate().getRequestNo());
            assertEquals("maker-a", mapper.findReturnCodeImportReviewForUpdate("RCR-TEST-001").getRequester());
            assertEquals(1, mapper.completeReturnCodeImportReview(
                    "RCR-TEST-001", "APPROVED", "success", "checker-b", "复核通过", "RCI-TEST-001"));
            assertNull(mapper.findPendingReturnCodeImportReviewForUpdate());

            review.setRequestNo("RCR-TEST-002");
            assertEquals(1, mapper.insertReturnCodeImportReview(review));
            assertEquals("RCR-TEST-002", mapper.findPendingReturnCodeImportReviewForUpdate().getRequestNo());
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentReturnCodeImportReviewDTO review = session.getMapper(PaymentConfigMapper.class)
                    .findReturnCodeImportReviews().stream()
                    .filter(item -> "RCR-TEST-001".equals(item.getRequestNo()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("APPROVED", review.getReviewStatus());
            assertEquals("checker-b", review.getReviewer());
            assertEquals("RCI-TEST-001", review.getAppliedBatchNo());
            assertEquals("SELF_DECLARED", review.getIdentityTrustLevel());
        }
    }

    @Test
    void shouldPersistLockCompleteAndReleaseReturnCodeStatusReview() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            assertEquals("USERPAYING", mapper.findReturnCodeMappingForUpdate(
                    "wx_h5", "USERPAYING").getChannelReturnCode());
            PaymentReturnCodeReviewEntity review = new PaymentReturnCodeReviewEntity();
            review.setRequestNo("RCS-TEST-001");
            review.setChannelCode("wx_h5");
            review.setChannelReturnCode("USERPAYING");
            review.setStandardizedCode("PAYMENT-CHANNEL-1001");
            review.setStandardizedMessage("用户支付中");
            review.setMappingVersion("v2026.07.1");
            review.setPendingMappingKey(repeat("d", 64));
            review.setBaseFingerprint(repeat("e", 64));
            review.setCandidateStatus("DISABLED");
            review.setCandidateStatusType("danger");
            review.setRequester("maker-a");
            review.setRequestReason("暂停错误码自动承接");
            review.setReviewStatus("PENDING_REVIEW");
            review.setReviewStatusType("warn");

            assertEquals(1, mapper.insertReturnCodeReview(review));
            assertEquals("RCS-TEST-001", mapper.findPendingReturnCodeReviewForUpdate(
                    repeat("d", 64)).getRequestNo());
            assertEquals("maker-a", mapper.findReturnCodeReviewForUpdate(
                    "RCS-TEST-001").getRequester());
            assertEquals(1, mapper.completeReturnCodeReview(
                    "RCS-TEST-001", "APPROVED", "success", "checker-b", "确认停用"));
            assertNull(mapper.findPendingReturnCodeReviewForUpdate(repeat("d", 64)));

            review.setRequestNo("RCS-TEST-002");
            assertEquals(1, mapper.insertReturnCodeReview(review));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentReturnCodeReviewDTO review = session.getMapper(PaymentConfigMapper.class)
                    .findReturnCodeReviews().stream()
                    .filter(item -> "RCS-TEST-001".equals(item.getRequestNo()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("APPROVED", review.getReviewStatus());
            assertEquals("PAYMENT-CHANNEL-1001", review.getStandardizedCode());
            assertEquals("checker-b", review.getReviewer());
            assertEquals("SELF_DECLARED", review.getIdentityTrustLevel());
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_return_code_map");
            statement.execute("DROP TABLE IF EXISTS t_payment_return_code_import_batch");
            statement.execute("DROP TABLE IF EXISTS t_payment_return_code_import_review");
            statement.execute("DROP TABLE IF EXISTS t_payment_return_code_review");
            statement.execute("CREATE TABLE t_payment_channel_return_code_map ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR(64), "
                    + "channel_return_code VARCHAR(64), standardized_code VARCHAR(64), "
                    + "standardized_message VARCHAR(256), handling_suggestion VARCHAR(256), "
                    + "retryable VARCHAR(16), manual_intervention_required VARCHAR(16), "
                    + "mapping_version VARCHAR(32), archive_status VARCHAR(32), archive_status_type VARCHAR(32), "
                    + "status VARCHAR(32), status_type VARCHAR(32), priority INT, updated_at DATETIME, "
                    + "UNIQUE(channel_code, channel_return_code))");
            statement.execute("CREATE TABLE t_payment_return_code_import_batch ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_no VARCHAR(64) UNIQUE, "
                    + "operator_name VARCHAR(128), change_reason VARCHAR(256), source_sha256 VARCHAR(64), "
                    + "baseline_fingerprint VARCHAR(64), result_fingerprint VARCHAR(64), total_count INT, "
                    + "create_count INT, update_count INT, unchanged_count INT, applied_count INT, "
                    + "batch_status VARCHAR(32), batch_status_type VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_return_code_import_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64) UNIQUE, "
                    + "pending_import_key VARCHAR(64) UNIQUE, csv_content CLOB, source_sha256 VARCHAR(64), "
                    + "baseline_fingerprint VARCHAR(64), total_count INT, create_count INT, update_count INT, "
                    + "unchanged_count INT, requester VARCHAR(64), request_reason VARCHAR(256), "
                    + "review_status VARCHAR(32), review_status_type VARCHAR(32), reviewer VARCHAR(64), "
                    + "review_comment VARCHAR(256), applied_batch_no VARCHAR(64), reviewed_at DATETIME, "
                    + "created_at DATETIME, updated_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_return_code_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64) UNIQUE, "
                    + "channel_code VARCHAR(64), channel_return_code VARCHAR(64), "
                    + "standardized_code VARCHAR(64), standardized_message VARCHAR(256), "
                    + "mapping_version VARCHAR(32), pending_mapping_key VARCHAR(64) UNIQUE, "
                    + "base_fingerprint VARCHAR(64), candidate_status VARCHAR(32), "
                    + "candidate_status_type VARCHAR(32), requester VARCHAR(64), request_reason VARCHAR(255), "
                    + "review_status VARCHAR(32), review_status_type VARCHAR(32), reviewer VARCHAR(64), "
                    + "review_comment VARCHAR(255), created_at DATETIME, reviewed_at DATETIME, "
                    + "updated_at DATETIME)");
            insert(statement, "USERPAYING", "ACTIVE", "ENABLED");
            insert(statement, "DISABLED_CODE", "ACTIVE", "DISABLED");
            insert(statement, "ARCHIVED_CODE", "ARCHIVED", "ENABLED");
            session.commit();
        }
    }

    private void insert(Statement statement, String returnCode, String archiveStatus, String status)
            throws Exception {
        statement.executeUpdate("INSERT INTO t_payment_channel_return_code_map "
                + "(channel_code, channel_return_code, standardized_code, standardized_message, "
                + "handling_suggestion, retryable, manual_intervention_required, mapping_version, "
                + "archive_status, archive_status_type, status, status_type, priority, updated_at) VALUES "
                + "('wx_h5', '" + returnCode + "', 'PAYMENT-CHANNEL-1001', '用户支付中', "
                + "'等待回调或主动查单', '是', '否', 'v2026.07.1', '" + archiveStatus
                + "', 'success', '" + status + "', 'success', 1, NOW())");
    }

    private String repeat(String value, int count) {
        return String.join("", java.util.Collections.nCopies(count, value));
    }
}
