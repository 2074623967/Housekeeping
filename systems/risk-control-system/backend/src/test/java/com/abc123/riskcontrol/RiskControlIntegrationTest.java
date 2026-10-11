package com.abc123.riskcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.abc123.riskcontrol.common.BusinessException;
import com.abc123.riskcontrol.dao.RiskControlDao;
import com.abc123.riskcontrol.dto.RiskDecisionRequestDTO;
import com.abc123.riskcontrol.dto.RiskDecisionResultDTO;
import com.abc123.riskcontrol.dto.RiskOpsConfigSnapshotDTO;
import com.abc123.riskcontrol.dto.RiskOpsSystemControlDTO;
import com.abc123.riskcontrol.dto.RiskReviewActionRequestDTO;
import com.abc123.riskcontrol.dto.ToggleRequestDTO;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.abc123.riskcontrol.service.RiskControlService;
import com.abc123.riskcontrol.service.RiskOpsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证风控基线链路。
 */
@SpringBootTest
class RiskControlIntegrationTest {

    @Autowired
    private RiskControlService service;

    @Autowired
    private RiskControlDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private RiskOpsConfigService riskOpsConfigService;

    @Test
    void shouldLoadRiskSummaryAndLists() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        assertEquals(4, service.summary().getMetrics().size());
        assertEquals(3, service.policies().getRecords().size());
        org.junit.jupiter.api.Assertions.assertTrue(service.reviewOrders().getRecords().size() >= 3);
    }

    @Test
    void shouldNotApproveLegacyReviewWithoutRequestContext() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        RiskReviewActionRequestDTO request = new RiskReviewActionRequestDTO();
        request.setReviewNo("REVIEW-1001");
        request.setAction("APPROVE");
        BusinessException exception = assertThrows(BusinessException.class, () -> service.reviewAction(request));
        assertEquals("历史复核单缺少当前版本原支付请求上下文，不能批准放行", exception.getMessage());
    }

    @Test
    void shouldFailClosedForLegacyDigestAndStillAllowManualRejection() {
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-LEGACY-DIGEST-001");
        request.setSourceSystem("payment-core");
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setAmount(new BigDecimal("6888.00"));

        RiskDecisionResultDTO reviewing = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", reviewing.getDecision());
        String fingerprint = dao.findReviewOrderByReviewNo(reviewing.getReviewNo()).getRequestFingerprint();
        assertTrue(fingerprint.matches("v2:[A-Za-z0-9_-]{43}"));
        assertEquals(46, fingerprint.length());

        jdbcTemplate.update("UPDATE t_risk_review_order SET request_fingerprint = ? WHERE review_no = ?",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", reviewing.getReviewNo());
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));

        RiskReviewActionRequestDTO action = new RiskReviewActionRequestDTO();
        action.setReviewNo(reviewing.getReviewNo());
        action.setAction("APPROVE");
        BusinessException exception = assertThrows(BusinessException.class, () -> service.reviewAction(action));
        assertEquals("历史复核单缺少当前版本原支付请求上下文，不能批准放行", exception.getMessage());
        assertEquals("PENDING", dao.findReviewOrderByReviewNo(reviewing.getReviewNo()).getStatus());

        action.setAction("REJECT");
        service.reviewAction(action);
        assertEquals("REJECTED", dao.findReviewOrderByReviewNo(reviewing.getReviewNo()).getStatus());

        jdbcTemplate.update("UPDATE t_risk_review_order SET status = 'APPROVED' WHERE review_no = ?",
                reviewing.getReviewNo());
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
    }

    @Test
    void shouldCreateOnlyOneReviewForConcurrentSameBusinessNo() throws Exception {
        CountDownLatch bothReadWithoutReview = new CountDownLatch(2);
        when(riskOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"))
                .thenAnswer(invocation -> {
                    bothReadWithoutReview.countDown();
                    if (!bothReadWithoutReview.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent risk requests did not reach the decision barrier");
                    }
                    return null;
                });
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-CONCURRENT-REVIEW-001");
        request.setSourceSystem("payment-core");
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setClientDeviceId("CONCURRENT-DEVICE-001");
        request.setPayerPhone("13900001111");
        request.setAmount(new BigDecimal("6888.00"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RiskDecisionResultDTO> first = executor.submit(() -> service.evaluatePaymentDecision(request));
            Future<RiskDecisionResultDTO> second = executor.submit(() -> service.evaluatePaymentDecision(request));
            RiskDecisionResultDTO firstResult = first.get(15, TimeUnit.SECONDS);
            RiskDecisionResultDTO secondResult = second.get(15, TimeUnit.SECONDS);
            assertEquals("REVIEW", firstResult.getDecision());
            assertEquals("REVIEW", secondResult.getDecision());
            assertEquals(firstResult.getReviewNo(), secondResult.getReviewNo());
            assertEquals(firstResult.getEventNo(), secondResult.getEventNo());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_risk_review_order WHERE business_no = ?",
                    Integer.class, request.getBusinessNo()));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_risk_intercept_event "
                            + "WHERE payment_order_id = ? AND hit_policy = 'LIMIT_USER_DAY' "
                            + "AND decision_result = '转人工复核'",
                    Integer.class, request.getBusinessNo()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional
    void shouldNotReuseRiskEventAcrossSourceSystems() {
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-EVENT-SOURCE-001");
        request.setSourceSystem("payment-core");
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setClientDeviceId("RISK-DEVICE-SOURCE-001");
        request.setPayerPhone("13900001111");
        request.setAmount(new BigDecimal("168.00"));

        RiskDecisionResultDTO first = service.evaluatePaymentDecision(request);
        assertEquals("INTERCEPT", first.getDecision());
        request.setSourceSystem("other-system");
        RiskDecisionResultDTO second = service.evaluatePaymentDecision(request);
        assertEquals("INTERCEPT", second.getDecision());
        assertTrue(!first.getEventNo().equals(second.getEventNo()));
        assertEquals(second.getEventNo(), service.evaluatePaymentDecision(request).getEventNo());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_risk_intercept_event WHERE payment_order_id = ? "
                        + "AND hit_policy = 'POLICY_DEVICE_RISK' AND decision_result = '已拦截'",
                Integer.class, request.getBusinessNo()));
    }

    @Test
    void shouldNotReuseLegacyApprovedOrPendingReviewWithoutRequestContext() {
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setAmount(new BigDecimal("168.00"));
        request.setBusinessNo("PAY202608030099");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
        request.setBusinessNo("PAY202608040002");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
    }

    @Test
    void shouldExportReviewOrdersSnapshot() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        String csv = new String(service.exportReviewOrders(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("复核单号,业务单号,风险标签"));
        assertTrue(csv.contains("REVIEW-1001"));
    }

    @Test
    void shouldRejectRepeatedReviewActionOnSameReviewOrder() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-REVIEW-GUARD-001");
        request.setSourceSystem("payment-core");
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13800008888");
        request.setAmount(new BigDecimal("188.00"));

        RiskDecisionResultDTO reviewing = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", reviewing.getDecision());

        request.setChannelCode("alipay_h5");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
        request.setChannelCode("wx_h5");

        RiskReviewActionRequestDTO approveRequest = new RiskReviewActionRequestDTO();
        approveRequest.setReviewNo(reviewing.getReviewNo());
        approveRequest.setAction("APPROVE");
        service.reviewAction(approveRequest);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.reviewAction(approveRequest));
        assertEquals("仅待审核复核单允许执行审核动作", exception.getMessage());
    }

    @Test
    void shouldRejectEmptyToggleCode() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        ToggleRequestDTO request = new ToggleRequestDTO();
        request.setEnabled(true);
        assertThrows(BusinessException.class, () -> service.togglePolicy(request));
    }

    @Test
    void shouldCreateReviewDecisionThenPassAfterApproval() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-DECISION-001");
        request.setSourceSystem("payment-core");
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13800008888");
        request.setAmount(new BigDecimal("168.00"));

        RiskDecisionResultDTO reviewing = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", reviewing.getDecision());

        request.setSourceAppId("housekeeping-h5-web");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
        request.setSourceAppId("housekeeping-app-web");

        RiskReviewActionRequestDTO approveRequest = new RiskReviewActionRequestDTO();
        approveRequest.setReviewNo(reviewing.getReviewNo());
        approveRequest.setAction("APPROVE");
        service.reviewAction(approveRequest);

        RiskDecisionResultDTO passed = service.evaluatePaymentDecision(request);
        assertEquals("PASS", passed.getDecision());
        assertEquals(reviewing.getReviewNo(), passed.getReviewNo());

        request.setSourceAppId("housekeeping-h5-web");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
        request.setSourceAppId("housekeeping-app-web");

        request.setAmount(new BigDecimal("168.0"));
        assertEquals("PASS", service.evaluatePaymentDecision(request).getDecision());

        request.setAmount(new BigDecimal("169.00"));
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
        request.setAmount(new BigDecimal("168.00"));
        request.setPaymentMethod("支付宝");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
        request.setPaymentMethod("微信支付");
        request.setChannelCode("alipay_h5");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_OTHER");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
    }

    @Test
    @Transactional
    void shouldApplyNewHardBlocklistEvenAfterSoftReviewWasApproved() {
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-HARD-AFTER-REVIEW-001");
        request.setSourceSystem("payment-core");
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setClientDeviceId("DEVICE-HARD-NEW-001");
        request.setPayerPhone("13800008888");
        request.setAmount(new BigDecimal("168.00"));

        RiskDecisionResultDTO reviewing = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", reviewing.getDecision());
        jdbcTemplate.update("INSERT INTO t_risk_blocklist "
                        + "(block_code, subject_value, subject_type, reason, action_type, status, status_type, updated_at) "
                        + "VALUES (?, ?, 'DEVICE', '新启用的硬拦截', 'INTERCEPT', 'ENABLED', 'danger', CURRENT_TIMESTAMP)",
                "BLOCK_NEW_HARD_AFTER_REVIEW", request.getClientDeviceId());

        RiskDecisionResultDTO pendingBlocked = service.evaluatePaymentDecision(request);
        assertEquals("INTERCEPT", pendingBlocked.getDecision());
        assertEquals("BLOCK_NEW_HARD_AFTER_REVIEW", pendingBlocked.getHitCode());
        assertTrue(pendingBlocked.getEventNo().startsWith("RISK-EVT-"));

        RiskReviewActionRequestDTO action = new RiskReviewActionRequestDTO();
        action.setReviewNo(reviewing.getReviewNo());
        action.setAction("APPROVE");
        service.reviewAction(action);
        RiskDecisionResultDTO approvedBlocked = service.evaluatePaymentDecision(request);
        assertEquals("INTERCEPT", approvedBlocked.getDecision());
        assertEquals(pendingBlocked.getEventNo(), approvedBlocked.getEventNo());
        assertEquals("BLOCK_NEW_HARD_AFTER_REVIEW", approvedBlocked.getHitCode());

        request.setBusinessNo("PAY-RISK-HARD-FIRST-001");
        RiskDecisionResultDTO firstBlocked = service.evaluatePaymentDecision(request);
        assertEquals("INTERCEPT", firstBlocked.getDecision());
        assertEquals("BLOCK_NEW_HARD_AFTER_REVIEW", firstBlocked.getHitCode());
        assertNull(dao.findLatestReviewOrderByBusinessNo(request.getBusinessNo()));
    }

    @Test
    void shouldReviewWhenOpsConfigBigAmountControlExceeded() {
        RiskOpsSystemControlDTO control = new RiskOpsSystemControlDTO();
        control.setControlCode("CONTROL_BIG_AMOUNT_REVIEW");
        control.setControlName("大额支付人工复核");
        control.setControlValue("5000");
        control.setRiskLevel("高");
        RiskOpsConfigSnapshotDTO snapshot = new RiskOpsConfigSnapshotDTO();
        snapshot.setBusinessCode("HOME_CLEAN");
        snapshot.setPayType("PAY_CONSUME");
        snapshot.setTerminalType("APP");
        snapshot.setEnabledSystemControls(Collections.singletonList(control));
        when(riskOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP")).thenReturn(snapshot);

        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-OPS-001");
        request.setSourceSystem("payment-core");
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13900001111");
        request.setAmount(new BigDecimal("6888.00"));

        RiskDecisionResultDTO result = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", result.getDecision());
        assertEquals("大额支付人工复核", result.getRiskTag());
    }
}
