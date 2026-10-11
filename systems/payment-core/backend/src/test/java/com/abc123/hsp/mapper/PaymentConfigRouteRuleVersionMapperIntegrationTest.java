package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.hsp.dto.PaymentRouteRuleRuntimeDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.entity.PaymentRouteRuleConfigEntity;
import com.abc123.hsp.entity.PaymentRouteRuleReviewEntity;
import com.abc123.hsp.entity.PaymentRouteRuleVersionEntity;
import com.abc123.hsp.service.impl.PaymentConfigServiceImpl;
import java.io.InputStream;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 支付路由规则版本 Mapper 的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentConfigRouteRuleVersionMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_route_rule_version;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldPersistReadAndRestoreRouteRuleVersion() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentRouteRuleConfigEntity current = mapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP");
            assertNotNull(current);
            assertEquals(Integer.valueOf(30), current.getRolloutPercentage());
            assertEquals("home-app-v1", current.getRolloutSalt());
            assertEquals(Integer.valueOf(1), mapper.findLatestRouteRuleVersionNo("RULE_HOME_APP"));
            List<PaymentRouteRuleRuntimeDTO> runtimeRules = mapper.findEnabledRouteRulesForRouting();
            assertEquals(1, runtimeRules.size());
            assertEquals(Integer.valueOf(30), runtimeRules.get(0).getRolloutPercentage());
            assertEquals("home-app-v1", runtimeRules.get(0).getRolloutSalt());

            PaymentRouteRuleVersionEntity version = buildVersion(current, 2, "UPDATE");
            version.setMatchExpression("terminal=APP AND amount<=500");
            version.setChangeReason("收窄 App 小额规则");
            assertEquals(1, mapper.insertRouteRuleVersion(version));

            PaymentRouteRuleVersionEntity stored = mapper.findRouteRuleVersion("RULE_HOME_APP", 2);
            assertEquals("terminal=APP AND amount<=500", stored.getMatchExpression());
            assertEquals(Integer.valueOf(30), stored.getRolloutPercentage());
            assertEquals("home-app-v1", stored.getRolloutSalt());
            List<PaymentRouteRuleVersionDTO> versions = mapper.findRouteRuleVersions("RULE_HOME_APP");
            assertEquals(2, versions.size());
            assertEquals(Integer.valueOf(2), versions.get(0).getVersionNo());
            assertEquals("收窄 App 小额规则", versions.get(0).getChangeReason());

            current.setMatchExpression(stored.getMatchExpression());
            current.setPriority(Integer.valueOf(5));
            assertEquals(1, mapper.updateRouteRule(current));
            assertEquals("terminal=APP AND amount<=500",
                    mapper.findRouteRuleByCode("RULE_HOME_APP").getMatchExpression());
            session.commit();
        }
    }

    @Test
    void shouldNotRouteThroughAliasedEnabledStatuses() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            assertEquals(2, mapper.findEnabledChannelsForRouting().size());
            assertEquals(1, mapper.findEnabledRouteRulesForRouting().size());

            statement.executeUpdate("UPDATE t_payment_channel_config SET status='enabled' "
                    + "WHERE channel_code='wx_h5'");
            session.clearCache();
            assertEquals(1, mapper.findEnabledChannelsForRouting().size());
            assertEquals("alipay_h5", mapper.findEnabledChannelsForRouting().get(0).getChannelCode());
            statement.executeUpdate("UPDATE t_payment_channel_config SET status='ENABLED ' "
                    + "WHERE channel_code='wx_h5'");
            session.clearCache();
            assertEquals(1, mapper.findEnabledChannelsForRouting().size());
            statement.executeUpdate("UPDATE t_payment_channel_config SET status='ENABLED' "
                    + "WHERE channel_code='wx_h5'");
            session.clearCache();
            assertEquals(2, mapper.findEnabledChannelsForRouting().size());

            statement.executeUpdate("UPDATE t_payment_route_rule_config SET status='enabled' "
                    + "WHERE rule_code='RULE_HOME_APP'");
            session.clearCache();
            assertEquals(0, mapper.findEnabledRouteRulesForRouting().size());
            statement.executeUpdate("UPDATE t_payment_route_rule_config SET status='ENABLED ' "
                    + "WHERE rule_code='RULE_HOME_APP'");
            session.clearCache();
            assertEquals(0, mapper.findEnabledRouteRulesForRouting().size());
            statement.executeUpdate("UPDATE t_payment_route_rule_config SET status='ENABLED' "
                    + "WHERE rule_code='RULE_HOME_APP'");
            session.clearCache();
            assertEquals(1, mapper.findEnabledRouteRulesForRouting().size());
        }
    }

    @Test
    void shouldKeepRouteRuleConfigurationAndVersionOutOfCaseAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            assertNull(mapper.findRouteRuleByCode("rule_home_app"));
            assertNull(mapper.findRouteRuleByCodeForUpdate("rule_home_app"));
            assertEquals(0, mapper.findRouteRuleVersions("rule_home_app").size());
            assertNull(mapper.findRouteRuleVersion("rule_home_app", 1));
            assertEquals(Integer.valueOf(0), mapper.findLatestRouteRuleVersionNo("rule_home_app"));

            PaymentRouteRuleConfigEntity rule = mapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP");
            rule.setRuleCode("rule_home_app");
            rule.setTargetChannelCode("alias_channel");
            assertEquals(0, mapper.updateRouteRule(rule));
            session.clearCache();
            assertEquals("wx_h5", mapper.findRouteRuleByCode("RULE_HOME_APP").getTargetChannelCode());
            assertEquals("BASELINE", mapper.findRouteRuleVersion("RULE_HOME_APP", 1).getChangeType());
        }
    }

    @Test
    void shouldPersistLockAndCompleteRouteRuleReview() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            PaymentRouteRuleReviewEntity review = new PaymentRouteRuleReviewEntity();
            review.setRequestNo("RRV-001");
            review.setRuleCode("RULE_HOME_APP");
            review.setBaseVersionNo(Integer.valueOf(1));
            review.setSourceVersionNo(Integer.valueOf(1));
            review.setOperationType("UPDATE");
            review.setRuleName("家政 App 小额路由");
            review.setMatchScene("HOME_CLEAN/APP");
            review.setMatchExpression("terminal=APP AND amount<=500");
            review.setTargetChannelCode("wx_h5");
            review.setFallbackChannelCode("alipay_h5");
            review.setRolloutPercentage(Integer.valueOf(30));
            review.setRolloutSalt("home-app-v2");
            review.setStatus("ENABLED");
            review.setStatusType("success");
            review.setPriority(Integer.valueOf(5));
            review.setRequester("maker-a");
            review.setRequestReason("收窄 App 小额规则");
            review.setReviewStatus("PENDING_REVIEW");
            review.setReviewStatusType("warn");

            assertEquals(1, mapper.insertRouteRuleReview(review));
            assertEquals(0, mapper.countPendingRouteRuleReviews("rule_home_app"));
            assertNull(mapper.findRouteRuleReviewForUpdate("rrv-001"));
            assertEquals(0, mapper.completeRouteRuleReview(
                    "rrv-001", "APPROVED", "success", "reviewer-b", "别名复核",
                    Boolean.TRUE, Boolean.TRUE, "[\"RULE_A_APP\",\"RULE_Z_APP\"]"));
            assertEquals(1, mapper.countPendingRouteRuleReviews("RULE_HOME_APP"));
            PaymentRouteRuleReviewEntity duplicatePending = buildReview("RRV-002");
            assertThrows(PersistenceException.class, () -> mapper.insertRouteRuleReview(duplicatePending));
            PaymentRouteRuleReviewEntity storedReview = mapper.findRouteRuleReviewForUpdate("RRV-001");
            assertNotNull(storedReview);
            assertEquals(Integer.valueOf(1), storedReview.getSourceVersionNo());
            assertEquals(Integer.valueOf(30), storedReview.getRolloutPercentage());
            assertEquals("home-app-v2", storedReview.getRolloutSalt());
            assertEquals(1, mapper.completeRouteRuleReview(
                    "RRV-001", "APPROVED", "success", "reviewer-b", "复核通过",
                    Boolean.TRUE, Boolean.TRUE, "[\"RULE_A_APP\",\"RULE_Z_APP\"]"));
            assertEquals(0, mapper.countPendingRouteRuleReviews("RULE_HOME_APP"));
            assertEquals(1, mapper.insertRouteRuleReview(duplicatePending));
            assertEquals(1, mapper.countPendingRouteRuleReviews("RULE_HOME_APP"));
            List<PaymentRouteRuleReviewDTO> reviews = mapper.findRouteRuleReviews();
            assertEquals(2, reviews.size());
            PaymentRouteRuleReviewDTO completed = reviews.stream()
                    .filter(item -> "RRV-001".equals(item.getRequestNo()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("APPROVED", completed.getReviewStatus());
            assertEquals("SELF_DECLARED", completed.getIdentityTrustLevel());
            assertEquals(Integer.valueOf(1), completed.getSourceVersionNo());
            assertEquals(Integer.valueOf(30), completed.getRolloutPercentage());
            assertEquals("home-app-v2", completed.getRolloutSalt());
            assertEquals(Boolean.TRUE, completed.getPriorityCollisionDetected());
            assertEquals(Boolean.TRUE, completed.getPriorityCollisionAcknowledged());
            assertEquals("[\"RULE_A_APP\",\"RULE_Z_APP\"]", completed.getPriorityCollisionRuleCodesJson());
            session.commit();
        }
    }

    @Test
    void shouldRejectCrossMethodFallbackReadFromChannelConfigurationBeforeReviewInsert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            assertEquals("微信支付", mapper.findChannelForRouting("wx_h5").getPaymentMethod());
            assertEquals("支付宝", mapper.findChannelForRouting("alipay_h5").getPaymentMethod());

            PaymentRouteRuleUpsertRequestDTO candidate = new PaymentRouteRuleUpsertRequestDTO();
            candidate.setRuleCode("RULE_HOME_APP");
            candidate.setRuleName("家政 App 小额路由");
            candidate.setMatchScene("HOME_CLEAN/APP");
            candidate.setMatchExpression("terminal IN (H5, APP)");
            candidate.setTargetChannelCode("wx_h5");
            candidate.setFallbackChannelCode("alipay_h5");
            candidate.setRolloutPercentage(Integer.valueOf(30));
            candidate.setRolloutSalt("home-app-v1");
            candidate.setPriority(Integer.valueOf(10));
            candidate.setEnabled(Boolean.TRUE);
            candidate.setChangeReason("复核存量兜底");
            PaymentRouteRuleReviewSubmitRequestDTO request = new PaymentRouteRuleReviewSubmitRequestDTO();
            request.setRequester("maker-a");
            request.setCandidate(candidate);

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new PaymentConfigServiceImpl(mapper).submitRouteRuleReview(request));
            assertEquals("目标渠道与兜底渠道支付方式必须一致", error.getMessage());
            assertEquals(0, mapper.countPendingRouteRuleReviews("RULE_HOME_APP"));

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_channel_config SET payment_method = '微信支付' "
                        + "WHERE channel_code = 'alipay_h5'");
            }
            session.clearCache();
            new PaymentConfigServiceImpl(mapper).submitRouteRuleReview(request);
            assertEquals(1, mapper.countPendingRouteRuleReviews("RULE_HOME_APP"));
            assertEquals("alipay_h5", mapper.findRouteRuleByCode("RULE_HOME_APP").getFallbackChannelCode());
        }
    }

    @Test
    void shouldSerializeConcurrentApprovalsWithInitiallyMissingCoordinator() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (SqlSession firstSession = sqlSessionFactory.openSession()) {
            PaymentConfigMapper firstMapper = firstSession.getMapper(PaymentConfigMapper.class);
            assertEquals(1, firstMapper.initRouteRuleConflictLock());
            Long firstLockId = firstMapper.lockRouteRuleConflictSet();
            assertEquals(Long.valueOf(1L), firstLockId);

            Future<Long> second = executor.submit(() -> lockRuleSetInSecondTransaction(secondStarted));
            org.junit.jupiter.api.Assertions.assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));

            firstSession.commit();
            assertEquals(firstLockId, second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Long lockRuleSetInSecondTransaction(CountDownLatch started) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);
            started.countDown();
            mapper.initRouteRuleConflictLock();
            Long lockId = mapper.lockRouteRuleConflictSet();
            session.commit();
            return lockId;
        }
    }

    private PaymentRouteRuleReviewEntity buildReview(String requestNo) {
        PaymentRouteRuleReviewEntity review = new PaymentRouteRuleReviewEntity();
        review.setRequestNo(requestNo);
        review.setRuleCode("RULE_HOME_APP");
        review.setBaseVersionNo(Integer.valueOf(1));
        review.setOperationType("UPDATE");
        review.setRuleName("家政 App 小额路由");
        review.setMatchScene("HOME_CLEAN/APP");
        review.setMatchExpression("terminal=APP AND amount<=500");
        review.setTargetChannelCode("wx_h5");
        review.setFallbackChannelCode("alipay_h5");
        review.setRolloutPercentage(Integer.valueOf(30));
        review.setRolloutSalt("home-app-v2");
        review.setStatus("ENABLED");
        review.setStatusType("success");
        review.setPriority(Integer.valueOf(5));
        review.setRequester("maker-a");
        review.setRequestReason("收窄 App 小额规则");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentRouteRuleVersionEntity buildVersion(PaymentRouteRuleConfigEntity rule,
                                                       int versionNo,
                                                       String changeType) {
        PaymentRouteRuleVersionEntity version = new PaymentRouteRuleVersionEntity();
        version.setRuleCode(rule.getRuleCode());
        version.setVersionNo(Integer.valueOf(versionNo));
        version.setRuleName(rule.getRuleName());
        version.setMatchScene(rule.getMatchScene());
        version.setMatchExpression(rule.getMatchExpression());
        version.setTargetChannelCode(rule.getTargetChannelCode());
        version.setFallbackChannelCode(rule.getFallbackChannelCode());
        version.setRolloutPercentage(rule.getRolloutPercentage());
        version.setRolloutSalt(rule.getRolloutSalt());
        version.setStatus(rule.getStatus());
        version.setStatusType(rule.getStatusType());
        version.setPriority(rule.getPriority());
        version.setChangeType(changeType);
        version.setChangeReason("版本变更");
        return version;
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigRouteRuleVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_route_rule_version");
            statement.execute("DROP TABLE IF EXISTS t_payment_route_rule_review");
            statement.execute("DROP TABLE IF EXISTS t_payment_route_rule_config");
            statement.execute("DROP TABLE IF EXISTS t_payment_route_rule_conflict_lock");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_config");
            statement.execute("CREATE TABLE t_payment_channel_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR_IGNORECASE(64) UNIQUE, "
                    + "payment_method VARCHAR(64) NOT NULL, scene_scope VARCHAR(128), "
                    + "daily_limit DECIMAL(18,2), priority INT, route_weight INT, "
                    + "status VARCHAR_IGNORECASE(32))");
            statement.executeUpdate("INSERT INTO t_payment_channel_config "
                    + "(channel_code, payment_method, scene_scope, daily_limit, priority, route_weight, status) VALUES "
                    + "('wx_h5', '微信支付', 'H5/APP', 500000.00, 1, 100, 'ENABLED'), "
                    + "('alipay_h5', '支付宝', 'H5/APP', 500000.00, 2, 100, 'ENABLED')");
            statement.execute("CREATE TABLE t_payment_route_rule_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, rule_code VARCHAR_IGNORECASE(64) UNIQUE, "
                    + "rule_name VARCHAR(128), match_scene VARCHAR(128), match_expression VARCHAR(512), "
                    + "target_channel_code VARCHAR(64), fallback_channel_code VARCHAR(64), "
                    + "rollout_percentage INT, rollout_salt VARCHAR(64), "
                    + "status VARCHAR_IGNORECASE(32), status_type VARCHAR(32), priority INT, updated_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_route_rule_conflict_lock ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, lock_name VARCHAR(64) UNIQUE, "
                    + "created_at DATETIME, updated_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_route_rule_version ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, rule_code VARCHAR_IGNORECASE(64), version_no INT, "
                    + "rule_name VARCHAR(128), match_scene VARCHAR(128), match_expression VARCHAR(512), "
                    + "target_channel_code VARCHAR(64), fallback_channel_code VARCHAR(64), "
                    + "rollout_percentage INT, rollout_salt VARCHAR(64), "
                    + "status VARCHAR(32), status_type VARCHAR(32), priority INT, change_type VARCHAR(32), "
                    + "source_version_no INT, change_reason VARCHAR(255), created_at DATETIME, "
                    + "UNIQUE(rule_code, version_no))");
            statement.execute("CREATE TABLE t_payment_route_rule_review ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR_IGNORECASE(64) UNIQUE, "
                    + "rule_code VARCHAR_IGNORECASE(64), pending_rule_code VARCHAR(64) UNIQUE, "
                    + "base_version_no INT, source_version_no INT, operation_type VARCHAR(32), "
                    + "rule_name VARCHAR(128), match_scene VARCHAR(128), match_expression VARCHAR(512), "
                    + "target_channel_code VARCHAR(64), fallback_channel_code VARCHAR(64), "
                    + "rollout_percentage INT, rollout_salt VARCHAR(64), "
                    + "status VARCHAR(32), status_type VARCHAR(32), priority INT, requester VARCHAR(64), "
                    + "request_reason VARCHAR(255), review_status VARCHAR(32), review_status_type VARCHAR(32), "
                    + "reviewer VARCHAR(64), review_comment VARCHAR(255), "
                    + "priority_collision_detected BOOLEAN, priority_collision_acknowledged BOOLEAN, "
                    + "priority_collision_rule_codes_json TEXT, created_at DATETIME, "
                    + "reviewed_at DATETIME, updated_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_route_rule_config "
                    + "(rule_code, rule_name, match_scene, match_expression, target_channel_code, "
                    + "fallback_channel_code, rollout_percentage, rollout_salt, status, status_type, priority, updated_at) VALUES "
                    + "('RULE_HOME_APP', '家政 App 小额路由', 'HOME_CLEAN/APP', 'terminal IN (H5, APP)', "
                    + "'wx_h5', 'alipay_h5', 30, 'home-app-v1', 'ENABLED', 'success', 10, NOW())");
            statement.executeUpdate("INSERT INTO t_payment_route_rule_version "
                    + "(rule_code, version_no, rule_name, match_scene, match_expression, target_channel_code, "
                    + "fallback_channel_code, rollout_percentage, rollout_salt, status, status_type, priority, change_type, source_version_no, "
                    + "change_reason, created_at) VALUES "
                    + "('RULE_HOME_APP', 1, '家政 App 小额路由', 'HOME_CLEAN/APP', "
                    + "'terminal IN (H5, APP)', 'wx_h5', 'alipay_h5', 30, 'home-app-v1', 'ENABLED', 'success', 10, "
                    + "'BASELINE', NULL, '初始化规则基线', NOW())");
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
