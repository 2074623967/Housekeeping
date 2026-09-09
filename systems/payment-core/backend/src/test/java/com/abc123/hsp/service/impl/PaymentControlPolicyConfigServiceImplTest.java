package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyUpsertRequestDTO;
import com.abc123.hsp.entity.PaymentControlPolicyEntity;
import com.abc123.hsp.entity.PaymentControlPolicyReviewEntity;
import com.abc123.hsp.entity.PaymentControlPolicyVersionEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/** 支付控制策略完整候选复核服务测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentControlPolicyConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldSubmitStatusCandidateWithoutChangingRuntime() {
        when(paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate("housekeeping-app-web"))
                .thenReturn(policy("DISABLED"));
        when(paymentConfigMapper.findControlPolicyReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).submitControlPolicyStatusReview(
                statusRequest("HOUSEKEEPING-APP-WEB", true));

        ArgumentCaptor<PaymentControlPolicyReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentControlPolicyReviewEntity.class);
        verify(paymentConfigMapper).insertControlPolicyReview(captor.capture());
        assertEquals("STATUS_CHANGE", captor.getValue().getOperationType());
        assertEquals("housekeeping-app-web", captor.getValue().getPendingSourceAppId());
        assertEquals("ENABLED", captor.getValue().getCandidateStatus());
        assertEquals(64, captor.getValue().getBaseFingerprint().length());
        verify(paymentConfigMapper, never()).updateControlPolicy(any());
    }

    @Test
    void shouldSubmitCreateAndUpdateWithTokenInheritance() {
        when(paymentConfigMapper.findControlPolicyReviews()).thenReturn(Collections.emptyList());
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        service.submitControlPolicyReview(reviewRequest(policyRequest("NEW-APP")));

        PaymentControlPolicyEntity current = policy("ENABLED");
        when(paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate("housekeeping-app-web"))
                .thenReturn(current);
        PaymentControlPolicyUpsertRequestDTO update = policyRequest("HOUSEKEEPING-APP-WEB");
        update.setSourceAppName("家政 App 收银台新版");
        update.setAccessTokenValue(" ");
        service.submitControlPolicyReview(reviewRequest(update));

        ArgumentCaptor<PaymentControlPolicyReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentControlPolicyReviewEntity.class);
        verify(paymentConfigMapper, times(2)).insertControlPolicyReview(captor.capture());
        PaymentControlPolicyReviewEntity submittedCreate = captor.getAllValues().get(0);
        assertEquals("CREATE", submittedCreate.getOperationType());
        assertEquals("new-app", submittedCreate.getSourceAppId());
        assertEquals("token-new", submittedCreate.getAccessTokenValue());
        PaymentControlPolicyReviewEntity submittedUpdate = captor.getAllValues().get(1);
        assertEquals("UPDATE", submittedUpdate.getOperationType());
        assertEquals("token-current", submittedUpdate.getAccessTokenValue());
    }

    @Test
    void shouldApproveUpdateAndInvalidatePreviousSelfCheck() {
        PaymentControlPolicyEntity current = policy("ENABLED");
        when(paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate("housekeeping-app-web"))
                .thenReturn(current);
        when(paymentConfigMapper.findControlPolicyReviews()).thenReturn(Collections.emptyList());
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentControlPolicyUpsertRequestDTO candidate = policyRequest("housekeeping-app-web");
        candidate.setSourceAppName("家政 App 收银台新版");
        service.submitControlPolicyReview(reviewRequest(candidate));
        ArgumentCaptor<PaymentControlPolicyReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(PaymentControlPolicyReviewEntity.class);
        verify(paymentConfigMapper).insertControlPolicyReview(reviewCaptor.capture());
        PaymentControlPolicyReviewEntity review = reviewCaptor.getValue();
        current.setSelfCheckStatus("FAIL");
        current.setSelfCheckStatusType("danger");
        current.setSelfCheckMessage("运行态自检结果已变化");
        when(paymentConfigMapper.findControlPolicyReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.updateControlPolicy(any(PaymentControlPolicyEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeControlPolicyReview(any(), any(), any(), any(), any())).thenReturn(1);

        service.approveControlPolicyReview(review.getRequestNo(), action("checker-b"));

        verify(paymentConfigMapper).updateControlPolicy(org.mockito.ArgumentMatchers.argThat(
                applied -> "WARN".equals(applied.getSelfCheckStatus())
                        && applied.getSelfCheckMessage().contains("待重新执行")
                        && "token-new".equals(applied.getAccessTokenValue())));
        verify(paymentConfigMapper).completeControlPolicyReview(
                review.getRequestNo(), "APPROVED", "success", "checker-b", "确认变更");
    }

    @Test
    void shouldTranslateCreateUniqueKeyRace() {
        PaymentControlPolicyReviewEntity review = review("CREATE", null);
        when(paymentConfigMapper.findControlPolicyReviewForUpdate("CPR-001")).thenReturn(review);
        doThrow(new DuplicateKeyException("uk_source_app_id"))
                .when(paymentConfigMapper).insertControlPolicy(any(PaymentControlPolicyEntity.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveControlPolicyReview("CPR-001", action("checker-b")));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(paymentConfigMapper, never()).completeControlPolicyReview(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectSameStatusExistingPendingAndPendingRace() {
        when(paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate("housekeeping-app-web"))
                .thenReturn(policy("ENABLED"));
        IllegalArgumentException sameStatus = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitControlPolicyStatusReview(statusRequest("HOUSEKEEPING-APP-WEB", true)));
        assertTrue(sameStatus.getMessage().contains("已启用"));

        when(paymentConfigMapper.findPendingControlPolicyReviewForUpdate("housekeeping-app-web"))
                .thenReturn(review("UPDATE", "baseline"));
        IllegalArgumentException pending = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitControlPolicyStatusReview(statusRequest("HOUSEKEEPING-APP-WEB", false)));
        assertTrue(pending.getMessage().contains("已有待复核"));

        when(paymentConfigMapper.findPendingControlPolicyReviewForUpdate("new-app")).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_control_policy_review_pending"))
                .when(paymentConfigMapper).insertControlPolicyReview(any(PaymentControlPolicyReviewEntity.class));
        IllegalArgumentException race = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitControlPolicyReview(reviewRequest(policyRequest("NEW-APP"))));
        assertTrue(race.getMessage().contains("已有待复核"));
        verify(paymentConfigMapper, never()).updateControlPolicy(any());
    }

    @Test
    void shouldRejectStaleAndProcessedReview() {
        PaymentControlPolicyReviewEntity staleReview = review("UPDATE", "stale-baseline");
        when(paymentConfigMapper.findControlPolicyReviewForUpdate("CPR-STALE")).thenReturn(staleReview);
        when(paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate("housekeeping-app-web"))
                .thenReturn(policy("ENABLED"));
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveControlPolicyReview("CPR-STALE", action("checker-b")));
        assertTrue(stale.getMessage().contains("已变化"));

        PaymentControlPolicyReviewEntity processedReview = review("UPDATE", "baseline");
        processedReview.setReviewStatus("APPROVED");
        when(paymentConfigMapper.findControlPolicyReviewForUpdate("CPR-PROCESSED"))
                .thenReturn(processedReview);
        IllegalArgumentException processed = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveControlPolicyReview("CPR-PROCESSED", action("checker-b")));
        assertTrue(processed.getMessage().contains("已处理"));
        verify(paymentConfigMapper, never()).updateControlPolicy(any());
    }

    @Test
    void shouldRejectReviewWhenVersionChangesWithoutConfigChange() {
        PaymentControlPolicyEntity current = policy("ENABLED");
        when(paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate("housekeeping-app-web"))
                .thenReturn(current);
        when(paymentConfigMapper.findLatestControlPolicyVersionNo("housekeeping-app-web"))
                .thenReturn(Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(2));
        when(paymentConfigMapper.findControlPolicyReviews()).thenReturn(Collections.emptyList());
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        service.submitControlPolicyReview(reviewRequest(policyRequest("housekeeping-app-web")));
        ArgumentCaptor<PaymentControlPolicyReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(PaymentControlPolicyReviewEntity.class);
        verify(paymentConfigMapper).insertControlPolicyReview(reviewCaptor.capture());
        PaymentControlPolicyReviewEntity review = reviewCaptor.getValue();
        when(paymentConfigMapper.findControlPolicyReviewForUpdate(review.getRequestNo())).thenReturn(review);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveControlPolicyReview(review.getRequestNo(), action("checker-b")));

        assertTrue(exception.getMessage().contains("已变化"));
        verify(paymentConfigMapper, never()).updateControlPolicy(any());
        verify(paymentConfigMapper, never()).insertControlPolicyVersion(any());
    }

    @Test
    void shouldRejectReviewWithoutChangingRuntime() {
        PaymentControlPolicyReviewEntity review = review("STATUS_CHANGE", "baseline");
        when(paymentConfigMapper.findControlPolicyReviewForUpdate("CPR-001")).thenReturn(review);
        when(paymentConfigMapper.completeControlPolicyReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findControlPolicyReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper)
                .rejectControlPolicyReview("CPR-001", action("checker-b"));

        verify(paymentConfigMapper).completeControlPolicyReview(
                "CPR-001", "REJECTED", "danger", "checker-b", "确认变更");
        verify(paymentConfigMapper, never()).insertControlPolicy(any());
        verify(paymentConfigMapper, never()).updateControlPolicy(any());
    }

    @Test
    void shouldRejectInvalidCandidateAndSameReviewer() {
        PaymentControlPolicyUpsertRequestDTO missingToken = policyRequest("new-app");
        missingToken.setAccessTokenValue("");
        IllegalArgumentException tokenError = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitControlPolicyReview(reviewRequest(missingToken)));
        assertTrue(tokenError.getMessage().contains("令牌不能为空"));

        PaymentControlPolicyUpsertRequestDTO invalidLimit = policyRequest("new-app");
        invalidLimit.setMinuteSubmitLimit(Integer.valueOf(0));
        IllegalArgumentException limitError = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitControlPolicyReview(reviewRequest(invalidLimit)));
        assertTrue(limitError.getMessage().contains("必须大于0"));

        when(paymentConfigMapper.findControlPolicyReviewForUpdate("CPR-001"))
                .thenReturn(review("CREATE", null));
        IllegalArgumentException sameReviewer = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveControlPolicyReview("CPR-001", action("maker-a")));
        assertTrue(sameReviewer.getMessage().contains("不能相同"));
    }

    @Test
    void shouldNotSerializeAccessTokenValue() throws Exception {
        PaymentControlPolicyDTO dto = new PaymentControlPolicyDTO();
        dto.setSourceAppId("housekeeping-app-web");
        dto.setAccessTokenValue("token-sensitive");
        dto.setAccessTokenConfigured(Boolean.TRUE);

        String json = new ObjectMapper().writeValueAsString(dto);

        assertFalse(json.contains("token-sensitive"));
        assertFalse(json.contains("accessTokenValue"));
        assertTrue(json.contains("\"accessTokenConfigured\":true"));
    }

    @Test
    void shouldSubmitAndApproveRollbackAsNewVersion() {
        PaymentControlPolicyEntity current = policy("ENABLED");
        PaymentControlPolicyVersionEntity target = version(current, 1, "token-old");
        when(paymentConfigMapper.findPendingControlPolicyReviewForUpdate("housekeeping-app-web"))
                .thenReturn(null);
        when(paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate("housekeeping-app-web"))
                .thenReturn(current);
        when(paymentConfigMapper.findLatestControlPolicyVersionNo("housekeeping-app-web"))
                .thenReturn(Integer.valueOf(2));
        when(paymentConfigMapper.findControlPolicyVersion("housekeeping-app-web", Integer.valueOf(1)))
                .thenReturn(target);
        when(paymentConfigMapper.findControlPolicyReviews()).thenReturn(Collections.emptyList());
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentControlPolicyRollbackRequestDTO rollback = new PaymentControlPolicyRollbackRequestDTO();
        rollback.setVersionNo(Integer.valueOf(1));
        rollback.setRequester("maker-a");
        rollback.setChangeReason("恢复稳定控制策略");

        service.submitControlPolicyRollbackReview("HOUSEKEEPING-APP-WEB", rollback);

        ArgumentCaptor<PaymentControlPolicyReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(PaymentControlPolicyReviewEntity.class);
        verify(paymentConfigMapper).insertControlPolicyReview(reviewCaptor.capture());
        PaymentControlPolicyReviewEntity review = reviewCaptor.getValue();
        assertEquals("ROLLBACK", review.getOperationType());
        assertEquals(Integer.valueOf(2), review.getBaseVersionNo());
        assertEquals(Integer.valueOf(1), review.getSourceVersionNo());
        assertEquals("token-old", review.getAccessTokenValue());

        when(paymentConfigMapper.findControlPolicyReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.updateControlPolicy(any(PaymentControlPolicyEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeControlPolicyReview(any(), any(), any(), any(), any())).thenReturn(1);

        service.approveControlPolicyReview(review.getRequestNo(), action("checker-b"));

        verify(paymentConfigMapper).updateControlPolicy(org.mockito.ArgumentMatchers.argThat(
                applied -> "token-old".equals(applied.getAccessTokenValue())
                        && "WARN".equals(applied.getSelfCheckStatus())));
        verify(paymentConfigMapper).insertControlPolicyVersion(org.mockito.ArgumentMatchers.argThat(
                version -> Integer.valueOf(3).equals(version.getVersionNo())
                        && "REVIEW_ROLLBACK".equals(version.getChangeType())
                        && Integer.valueOf(1).equals(version.getSourceVersionNo())));
    }

    private PaymentControlPolicyStatusReviewRequestDTO statusRequest(String sourceAppId, boolean enabled) {
        PaymentControlPolicyStatusReviewRequestDTO request = new PaymentControlPolicyStatusReviewRequestDTO();
        request.setSourceAppId(sourceAppId);
        request.setEnabled(Boolean.valueOf(enabled));
        request.setRequester("maker-a");
        request.setChangeReason("调整来源应用准入");
        return request;
    }

    private PaymentControlPolicyReviewSubmitRequestDTO reviewRequest(
            PaymentControlPolicyUpsertRequestDTO candidate) {
        PaymentControlPolicyReviewSubmitRequestDTO request = new PaymentControlPolicyReviewSubmitRequestDTO();
        request.setRequester("maker-a");
        request.setCandidate(candidate);
        return request;
    }

    private PaymentControlPolicyReviewActionRequestDTO action(String reviewer) {
        PaymentControlPolicyReviewActionRequestDTO request =
                new PaymentControlPolicyReviewActionRequestDTO();
        request.setReviewer(reviewer);
        request.setReviewComment("确认变更");
        return request;
    }

    private PaymentControlPolicyUpsertRequestDTO policyRequest(String sourceAppId) {
        PaymentControlPolicyUpsertRequestDTO request = new PaymentControlPolicyUpsertRequestDTO();
        request.setSourceAppId(sourceAppId);
        request.setSourceAppName("新来源应用");
        request.setAllowedPaymentMethods("微信支付,支付宝");
        request.setAllowedChannelCodes("wx_h5,alipay_h5");
        request.setAllowedMerchantNos("MCH_WX_10001,MCH_ALI_20001");
        request.setMinuteSubmitLimit(Integer.valueOf(40));
        request.setInterfaceMinuteSubmitLimit(Integer.valueOf(20));
        request.setTokenAuthRequired(Boolean.TRUE);
        request.setAccessTokenValue("token-new");
        request.setStrictMode(Boolean.TRUE);
        request.setEnabled(Boolean.TRUE);
        request.setChangeReason("新增或调整来源应用准入");
        return request;
    }

    private PaymentControlPolicyReviewEntity review(String operationType, String baseline) {
        PaymentControlPolicyEntity policy = policy("ENABLED");
        PaymentControlPolicyReviewEntity review = new PaymentControlPolicyReviewEntity();
        review.setRequestNo("CPR-001");
        review.setSourceAppId("housekeeping-app-web");
        review.setOperationType(operationType);
        review.setSourceAppName(policy.getSourceAppName());
        review.setAllowedPaymentMethods(policy.getAllowedPaymentMethods());
        review.setAllowedChannelCodes(policy.getAllowedChannelCodes());
        review.setAllowedMerchantNos(policy.getAllowedMerchantNos());
        review.setMinuteSubmitLimit(policy.getMinuteSubmitLimit());
        review.setInterfaceMinuteSubmitLimit(policy.getInterfaceMinuteSubmitLimit());
        review.setTokenAuthRequired(policy.getTokenAuthRequired());
        review.setAccessTokenValue(policy.getAccessTokenValue());
        review.setStrictMode(policy.getStrictMode());
        review.setBaseFingerprint(baseline);
        review.setCandidateStatus("ENABLED");
        review.setCandidateStatusType("success");
        review.setRequester("maker-a");
        review.setRequestReason("调整来源应用准入");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentControlPolicyEntity policy(String status) {
        PaymentControlPolicyEntity policy = new PaymentControlPolicyEntity();
        policy.setSourceAppId("housekeeping-app-web");
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
        policy.setStatus(status);
        policy.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        return policy;
    }

    private PaymentControlPolicyVersionEntity version(
            PaymentControlPolicyEntity policy, int versionNo, String accessToken) {
        PaymentControlPolicyVersionEntity version = new PaymentControlPolicyVersionEntity();
        version.setSourceAppId(policy.getSourceAppId());
        version.setVersionNo(Integer.valueOf(versionNo));
        version.setSourceAppName(policy.getSourceAppName());
        version.setAllowedPaymentMethods(policy.getAllowedPaymentMethods());
        version.setAllowedChannelCodes(policy.getAllowedChannelCodes());
        version.setAllowedMerchantNos(policy.getAllowedMerchantNos());
        version.setMinuteSubmitLimit(policy.getMinuteSubmitLimit());
        version.setInterfaceMinuteSubmitLimit(policy.getInterfaceMinuteSubmitLimit());
        version.setTokenAuthRequired(policy.getTokenAuthRequired());
        version.setAccessTokenValue(accessToken);
        version.setStrictMode(policy.getStrictMode());
        version.setStatus(policy.getStatus());
        version.setStatusType(policy.getStatusType());
        version.setChangeType("REVIEW_RELEASE");
        version.setChangeReason("历史版本");
        return version;
    }
}
