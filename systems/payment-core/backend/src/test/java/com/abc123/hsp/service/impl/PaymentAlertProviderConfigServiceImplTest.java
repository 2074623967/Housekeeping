package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentAlertProviderReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentAlertProviderReviewDTO;
import com.abc123.hsp.dto.PaymentAlertProviderStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentAlertProviderUpsertRequestDTO;
import com.abc123.hsp.entity.PaymentAlertProviderConfigEntity;
import com.abc123.hsp.entity.PaymentAlertProviderReviewEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/** 异常告警通知供应商完整候选复核服务测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentAlertProviderConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @BeforeEach
    void defaultAlertProviderReviewInsertSucceeds() {
        lenient().when(paymentConfigMapper.insertAlertProviderReview(any())).thenReturn(1);
    }

    @Test
    void shouldSubmitCreateUpdateAndStatusCandidatesWithoutChangingRuntime() {
        when(paymentConfigMapper.findAlertProviderReviews())
                .thenReturn(Collections.singletonList(new PaymentAlertProviderReviewDTO()));
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        service.createAlertProvider(providerRequest("alert_sms_backup"));

        PaymentAlertProviderConfigEntity current = provider("ENABLED");
        when(paymentConfigMapper.findAlertProviderByCodeForUpdate("ALERT_IM_WECOM")).thenReturn(current);
        PaymentAlertProviderUpsertRequestDTO update = providerRequest("ignored_code");
        update.setProviderName("企业消息主通道");
        service.updateAlertProvider("alert_im_wecom", update);
        service.toggleAlertProvider(statusRequest("alert_im_wecom", false));

        ArgumentCaptor<PaymentAlertProviderReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentAlertProviderReviewEntity.class);
        verify(paymentConfigMapper, org.mockito.Mockito.times(3))
                .insertAlertProviderReview(captor.capture());
        assertEquals("CREATE", captor.getAllValues().get(0).getOperationType());
        assertEquals("ALERT_SMS_BACKUP", captor.getAllValues().get(0).getPendingProviderCode());
        assertEquals(null, captor.getAllValues().get(0).getBaseFingerprint());
        assertEquals("UPDATE", captor.getAllValues().get(1).getOperationType());
        assertEquals("企业消息主通道", captor.getAllValues().get(1).getProviderName());
        assertEquals(64, captor.getAllValues().get(1).getBaseFingerprint().length());
        assertEquals("STATUS_CHANGE", captor.getAllValues().get(2).getOperationType());
        assertEquals("DISABLED", captor.getAllValues().get(2).getCandidateStatus());
        verify(paymentConfigMapper, never()).insertAlertProvider(any());
        verify(paymentConfigMapper, never()).updateAlertProvider(any());
    }

    @Test
    void shouldStopStatusReviewWhenDatabaseAffectsNoRow() {
        when(paymentConfigMapper.findAlertProviderByCodeForUpdate("ALERT_IM_WECOM"))
                .thenReturn(provider("ENABLED"));
        when(paymentConfigMapper.insertAlertProviderReview(any())).thenReturn(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .toggleAlertProvider(statusRequest("alert_im_wecom", false)));

        assertTrue(exception.getMessage().contains("未实际生效"));
        verify(paymentConfigMapper, never()).updateAlertProvider(any());
    }

    @Test
    void shouldApproveUpdateOnlyWhenCompleteBaselineMatches() {
        PaymentAlertProviderConfigEntity current = provider("ENABLED");
        when(paymentConfigMapper.findAlertProviderByCodeForUpdate("ALERT_IM_WECOM")).thenReturn(current);
        when(paymentConfigMapper.findAlertProviderReviews()).thenReturn(Collections.emptyList());
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentAlertProviderUpsertRequestDTO candidate = providerRequest("ALERT_IM_WECOM");
        candidate.setEndpointAlias("wecom-alert-v2");
        candidate.setRoutePriority(Integer.valueOf(5));

        service.updateAlertProvider("ALERT_IM_WECOM", candidate);

        ArgumentCaptor<PaymentAlertProviderReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentAlertProviderReviewEntity.class);
        verify(paymentConfigMapper).insertAlertProviderReview(captor.capture());
        PaymentAlertProviderReviewEntity review = captor.getValue();
        when(paymentConfigMapper.findAlertProviderReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.updateAlertProvider(any())).thenReturn(1);
        when(paymentConfigMapper.completeAlertProviderReview(any(), any(), any(), any(), any())).thenReturn(1);

        service.approveAlertProviderReview(review.getRequestNo(), action("checker-b", "确认调整"));

        verify(paymentConfigMapper).updateAlertProvider(org.mockito.ArgumentMatchers.argThat(
                applied -> "wecom-alert-v2".equals(applied.getEndpointAlias())
                        && Integer.valueOf(5).equals(applied.getRoutePriority())));
        verify(paymentConfigMapper).completeAlertProviderReview(
                review.getRequestNo(), "APPROVED", "success", "checker-b", "确认调整");
    }

    @Test
    void shouldApproveCreateOnlyWhileProviderCodeRemainsUnused() {
        PaymentAlertProviderReviewEntity review = review("CREATE", null);
        review.setProviderCode("ALERT_SMS_BACKUP");
        when(paymentConfigMapper.findAlertProviderReviewForUpdate("APR-001")).thenReturn(review);
        when(paymentConfigMapper.insertAlertProvider(any())).thenReturn(1);
        when(paymentConfigMapper.completeAlertProviderReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findAlertProviderReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveAlertProviderReview(
                "APR-001", action("checker-b", "确认新增"));

        verify(paymentConfigMapper).insertAlertProvider(org.mockito.ArgumentMatchers.argThat(
                applied -> "ALERT_SMS_BACKUP".equals(applied.getProviderCode())
                        && "ENABLED".equals(applied.getStatus())));

        when(paymentConfigMapper.findAlertProviderByCodeForUpdate("ALERT_SMS_BACKUP"))
                .thenReturn(provider("ENABLED"));
        IllegalArgumentException occupied = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveAlertProviderReview(
                        "APR-001", action("checker-b", "再次新增")));
        assertTrue(occupied.getMessage().contains("已存在"));
    }

    @Test
    void shouldRejectSameStatusPendingRaceAndInvalidCandidate() {
        when(paymentConfigMapper.findAlertProviderByCodeForUpdate("ALERT_IM_WECOM"))
                .thenReturn(provider("ENABLED"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .toggleAlertProvider(statusRequest("ALERT_IM_WECOM", true)))
                .getMessage().contains("已启用"));

        when(paymentConfigMapper.findPendingAlertProviderReviewForUpdate("ALERT_IM_WECOM"))
                .thenReturn(review("UPDATE", "baseline"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .updateAlertProvider("ALERT_IM_WECOM", providerRequest("ALERT_IM_WECOM")))
                .getMessage().contains("已有待复核"));

        when(paymentConfigMapper.findPendingAlertProviderReviewForUpdate("ALERT_SMS_BACKUP"))
                .thenReturn(null);
        doThrow(new DuplicateKeyException("uk_alert_provider_review_pending"))
                .when(paymentConfigMapper).insertAlertProviderReview(any());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .createAlertProvider(providerRequest("ALERT_SMS_BACKUP")))
                .getMessage().contains("已有待复核"));

        PaymentAlertProviderUpsertRequestDTO invalidChannel = providerRequest("ALERT_EMAIL_BACKUP");
        invalidChannel.setChannelCode("WEBHOOK");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .createAlertProvider(invalidChannel)).getMessage().contains("通知通道"));
        PaymentAlertProviderUpsertRequestDTO invalidPriority = providerRequest("ALERT_EMAIL_BACKUP");
        invalidPriority.setRoutePriority(Integer.valueOf(-1));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .createAlertProvider(invalidPriority)).getMessage().contains("优先级"));
    }

    @Test
    void shouldRejectSameReviewerStaleBaselineAndCandidateWithoutRuntimeWrite() {
        PaymentAlertProviderReviewEntity staleReview = review("UPDATE", "stale-baseline");
        when(paymentConfigMapper.findAlertProviderReviewForUpdate("APR-001")).thenReturn(staleReview);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveAlertProviderReview(
                        "APR-001", action("maker-a", "同人复核"))).getMessage().contains("不能相同"));

        when(paymentConfigMapper.findAlertProviderByCodeForUpdate("ALERT_IM_WECOM"))
                .thenReturn(provider("ENABLED"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveAlertProviderReview(
                        "APR-001", action("checker-b", "确认修改"))).getMessage().contains("已变化"));

        PaymentAlertProviderReviewEntity rejected = review("STATUS_CHANGE", "baseline");
        rejected.setRequestNo("APR-REJECT");
        when(paymentConfigMapper.findAlertProviderReviewForUpdate("APR-REJECT")).thenReturn(rejected);
        when(paymentConfigMapper.completeAlertProviderReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findAlertProviderReviews()).thenReturn(Collections.emptyList());
        new PaymentConfigServiceImpl(paymentConfigMapper).rejectAlertProviderReview(
                "APR-REJECT", action("checker-b", "暂不调整"));
        verify(paymentConfigMapper).completeAlertProviderReview(
                "APR-REJECT", "REJECTED", "danger", "checker-b", "暂不调整");
        verify(paymentConfigMapper, never()).updateAlertProvider(any());
    }

    private PaymentAlertProviderStatusReviewRequestDTO statusRequest(String code, boolean enabled) {
        PaymentAlertProviderStatusReviewRequestDTO request = new PaymentAlertProviderStatusReviewRequestDTO();
        request.setProviderCode(code);
        request.setEnabled(Boolean.valueOf(enabled));
        request.setRequester("maker-a");
        request.setChangeReason("调整供应商状态");
        return request;
    }

    private PaymentAlertProviderReviewActionRequestDTO action(String reviewer, String comment) {
        PaymentAlertProviderReviewActionRequestDTO request = new PaymentAlertProviderReviewActionRequestDTO();
        request.setReviewer(reviewer);
        request.setReviewComment(comment);
        return request;
    }

    private PaymentAlertProviderUpsertRequestDTO providerRequest(String providerCode) {
        PaymentAlertProviderConfigEntity provider = provider("ENABLED");
        PaymentAlertProviderUpsertRequestDTO request = new PaymentAlertProviderUpsertRequestDTO();
        request.setProviderCode(providerCode);
        request.setProviderName(provider.getProviderName());
        request.setChannelCode(provider.getChannelCode());
        request.setEndpointAlias(provider.getEndpointAlias());
        request.setTemplateCode(provider.getTemplateCode());
        request.setTemplateBody(provider.getTemplateBody());
        request.setRouteRule(provider.getRouteRule());
        request.setRoutePriority(provider.getRoutePriority());
        request.setRetryPolicy(provider.getRetryPolicy());
        request.setRateLimitPolicy(provider.getRateLimitPolicy());
        request.setEnabled(Boolean.TRUE);
        request.setRequester("maker-a");
        request.setChangeReason("调整告警通知供应商");
        return request;
    }

    private PaymentAlertProviderReviewEntity review(String operationType, String baseline) {
        PaymentAlertProviderConfigEntity provider = provider("ENABLED");
        PaymentAlertProviderReviewEntity review = new PaymentAlertProviderReviewEntity();
        review.setRequestNo("APR-001");
        review.setProviderCode(provider.getProviderCode());
        review.setOperationType(operationType);
        review.setProviderName(provider.getProviderName());
        review.setChannelCode(provider.getChannelCode());
        review.setEndpointAlias(provider.getEndpointAlias());
        review.setTemplateCode(provider.getTemplateCode());
        review.setTemplateBody(provider.getTemplateBody());
        review.setRouteRule(provider.getRouteRule());
        review.setRoutePriority(provider.getRoutePriority());
        review.setRetryPolicy(provider.getRetryPolicy());
        review.setRateLimitPolicy(provider.getRateLimitPolicy());
        review.setBaseFingerprint(baseline);
        review.setCandidateStatus(provider.getStatus());
        review.setCandidateStatusType(provider.getStatusType());
        review.setRequester("maker-a");
        review.setRequestReason("调整告警通知供应商");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentAlertProviderConfigEntity provider(String status) {
        PaymentAlertProviderConfigEntity provider = new PaymentAlertProviderConfigEntity();
        provider.setProviderCode("ALERT_IM_WECOM");
        provider.setProviderName("企业消息");
        provider.setChannelCode("IM");
        provider.setEndpointAlias("wecom-alert");
        provider.setTemplateCode("PAYMENT_ALERT");
        provider.setTemplateBody("异常：${issueNo}");
        provider.setRouteRule("P1/P2");
        provider.setRoutePriority(Integer.valueOf(10));
        provider.setRetryPolicy("最多3次");
        provider.setRateLimitPolicy("每分钟100条");
        provider.setStatus(status);
        provider.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        return provider;
    }
}
