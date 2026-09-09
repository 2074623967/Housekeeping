package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewDTO;
import com.abc123.hsp.dto.PaymentGatewayStatusReviewRequestDTO;
import com.abc123.hsp.entity.PaymentGatewayConfigEntity;
import com.abc123.hsp.entity.PaymentGatewayReviewEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付网关接入配置服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentGatewayConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldSubmitGatewayStatusCandidateWithoutChangingRuntime() {
        PaymentGatewayConfigEntity gateway = gateway("DISABLED");
        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_BANK_OFFLINE")).thenReturn(gateway);
        when(paymentConfigMapper.findGatewayReviews())
                .thenReturn(Collections.singletonList(new PaymentGatewayReviewDTO()));

        new PaymentConfigServiceImpl(paymentConfigMapper).submitGatewayStatusReview(
                submitRequest("gateway_bank_offline", true));

        ArgumentCaptor<PaymentGatewayReviewEntity> captor = ArgumentCaptor.forClass(PaymentGatewayReviewEntity.class);
        verify(paymentConfigMapper).insertGatewayReview(captor.capture());
        assertEquals("GATEWAY_BANK_OFFLINE", captor.getValue().getPendingGatewayCode());
        assertEquals("ENABLED", captor.getValue().getCandidateStatus());
        assertEquals(64, captor.getValue().getBaseFingerprint().length());
        verify(paymentConfigMapper, never()).updateGatewayStatus(any(), any(), any());
    }

    @Test
    void shouldApproveGatewayStatusCandidateWhenBaselineMatches() {
        PaymentGatewayConfigEntity gateway = gateway("DISABLED");
        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_BANK_OFFLINE")).thenReturn(gateway);
        when(paymentConfigMapper.findGatewayReviews()).thenReturn(Collections.emptyList());
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        service.submitGatewayStatusReview(submitRequest("GATEWAY_BANK_OFFLINE", true));
        ArgumentCaptor<PaymentGatewayReviewEntity> captor = ArgumentCaptor.forClass(PaymentGatewayReviewEntity.class);
        verify(paymentConfigMapper).insertGatewayReview(captor.capture());
        PaymentGatewayReviewEntity review = captor.getValue();
        when(paymentConfigMapper.findGatewayReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.updateGatewayStatus("GATEWAY_BANK_OFFLINE", "ENABLED", "success")).thenReturn(1);
        when(paymentConfigMapper.completeGatewayReview(any(), any(), any(), any(), any())).thenReturn(1);

        service.approveGatewayReview(review.getRequestNo(), action("checker-b", "确认启用"));

        verify(paymentConfigMapper).updateGatewayStatus("GATEWAY_BANK_OFFLINE", "ENABLED", "success");
        verify(paymentConfigMapper).completeGatewayReview(
                review.getRequestNo(), "APPROVED", "success", "checker-b", "确认启用");
    }

    @Test
    void shouldRejectSameReviewerAndStaleGatewayBaseline() {
        PaymentGatewayReviewEntity sameReviewer = review("fingerprint");
        when(paymentConfigMapper.findGatewayReviewForUpdate("GWR-001")).thenReturn(sameReviewer);
        IllegalArgumentException sameIdentity = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveGatewayReview(
                        "GWR-001", action("maker-a", "同人复核")));
        assertTrue(sameIdentity.getMessage().contains("不能相同"));

        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_BANK_OFFLINE"))
                .thenReturn(gateway("DISABLED"));
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveGatewayReview(
                        "GWR-001", action("checker-b", "确认启用")));
        assertTrue(stale.getMessage().contains("已变化"));
        verify(paymentConfigMapper, never()).updateGatewayStatus(any(), any(), any());
    }

    @Test
    void shouldRejectSameStatusAndExistingPendingReview() {
        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_BANK_OFFLINE"))
                .thenReturn(gateway("ENABLED"));
        IllegalArgumentException sameStatus = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).submitGatewayStatusReview(
                        submitRequest("GATEWAY_BANK_OFFLINE", true)));
        assertTrue(sameStatus.getMessage().contains("已启用"));

        when(paymentConfigMapper.findPendingGatewayReviewForUpdate("GATEWAY_BANK_OFFLINE"))
                .thenReturn(review("fingerprint"));
        IllegalArgumentException pending = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).submitGatewayStatusReview(
                        submitRequest("GATEWAY_BANK_OFFLINE", false)));
        assertTrue(pending.getMessage().contains("已有待复核"));
        verify(paymentConfigMapper, never()).insertGatewayReview(any());
    }

    @Test
    void shouldTranslatePendingReviewUniqueKeyConflict() {
        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_BANK_OFFLINE"))
                .thenReturn(gateway("DISABLED"));
        doThrow(new DuplicateKeyException("duplicate pending gateway"))
                .when(paymentConfigMapper).insertGatewayReview(any());

        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).submitGatewayStatusReview(
                        submitRequest("GATEWAY_BANK_OFFLINE", true)));

        assertTrue(conflict.getMessage().contains("已有待复核"));
        verify(paymentConfigMapper, never()).updateGatewayStatus(any(), any(), any());
    }

    @Test
    void shouldRejectGatewayReviewWithoutChangingRuntime() {
        PaymentGatewayReviewEntity review = review("fingerprint");
        when(paymentConfigMapper.findGatewayReviewForUpdate("GWR-001")).thenReturn(review);
        when(paymentConfigMapper.completeGatewayReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findGatewayReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).rejectGatewayReview(
                "GWR-001", action("checker-b", "暂不启用"));

        verify(paymentConfigMapper).completeGatewayReview(
                "GWR-001", "REJECTED", "danger", "checker-b", "暂不启用");
        verify(paymentConfigMapper, never()).updateGatewayStatus(any(), any(), any());
    }

    @Test
    void shouldExposeGatewayFormalizationFieldsInOverview() {
        PaymentGatewayConfigDTO gateway = new PaymentGatewayConfigDTO();
        gateway.setGatewayCode("GATEWAY_WX_ACQ");
        gateway.setEnvironmentScope("生产+预发+沙箱");
        gateway.setCertificateAlias("wx-mch-cert-v2026.07");
        gateway.setCertificateStatus("VALID");
        gateway.setCertificateStatusType("success");
        gateway.setReleaseStage("灰度 30%");
        gateway.setGrayStrategy("按商户分组灰度至核心城市订单");
        gateway.setCallbackWhitelist("101.1.0.0/16,101.2.0.0/16");
        gateway.setAdapterRegistry("submit=wechat-h5,query=wechat-query,refund=wechat-refund");
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Arrays.asList(gateway));

        PaymentGatewayConfigDTO actualGateway = new PaymentConfigServiceImpl(paymentConfigMapper)
                .overview()
                .getGateways()
                .get(0);

        org.junit.jupiter.api.Assertions.assertEquals("生产+预发+沙箱", actualGateway.getEnvironmentScope());
        org.junit.jupiter.api.Assertions.assertEquals("wx-mch-cert-v2026.07", actualGateway.getCertificateAlias());
        org.junit.jupiter.api.Assertions.assertEquals("VALID", actualGateway.getCertificateStatus());
        org.junit.jupiter.api.Assertions.assertEquals("灰度 30%", actualGateway.getReleaseStage());
        org.junit.jupiter.api.Assertions.assertTrue(actualGateway.getGrayStrategy().contains("核心城市"));
        org.junit.jupiter.api.Assertions.assertTrue(actualGateway.getCallbackWhitelist().contains("101.1.0.0/16"));
        org.junit.jupiter.api.Assertions.assertTrue(actualGateway.getAdapterRegistry().contains("refund=wechat-refund"));
    }

    private PaymentGatewayStatusReviewRequestDTO submitRequest(String gatewayCode, boolean enabled) {
        PaymentGatewayStatusReviewRequestDTO request = new PaymentGatewayStatusReviewRequestDTO();
        request.setGatewayCode(gatewayCode);
        request.setEnabled(Boolean.valueOf(enabled));
        request.setRequester("maker-a");
        request.setChangeReason("恢复网关承接");
        return request;
    }

    private PaymentGatewayReviewActionRequestDTO action(String reviewer, String comment) {
        PaymentGatewayReviewActionRequestDTO request = new PaymentGatewayReviewActionRequestDTO();
        request.setReviewer(reviewer);
        request.setReviewComment(comment);
        return request;
    }

    private PaymentGatewayReviewEntity review(String baseline) {
        PaymentGatewayReviewEntity review = new PaymentGatewayReviewEntity();
        review.setRequestNo("GWR-001");
        review.setGatewayCode("GATEWAY_BANK_OFFLINE");
        review.setGatewayName("线下银行网关");
        review.setAccessMode("直连");
        review.setBaseFingerprint(baseline);
        review.setCandidateStatus("ENABLED");
        review.setCandidateStatusType("success");
        review.setRequester("maker-a");
        review.setRequestReason("恢复网关承接");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentGatewayConfigEntity gateway(String status) {
        PaymentGatewayConfigEntity gateway = new PaymentGatewayConfigEntity();
        gateway.setGatewayCode("GATEWAY_BANK_OFFLINE");
        gateway.setGatewayName("线下银行网关");
        gateway.setAccessMode("直连");
        gateway.setChannelScope("bank_offline");
        gateway.setEnvironmentScope("生产+预发");
        gateway.setApiBaseUrl("https://gateway.example.test");
        gateway.setProtocolType("JSON");
        gateway.setSignAlgorithm("RSA2");
        gateway.setCertificateAlias("bank-cert-v1");
        gateway.setCertificateStatus("VALID");
        gateway.setCertificateStatusType("success");
        gateway.setReleaseStage("全量");
        gateway.setGrayStrategy("全部商户");
        gateway.setCallbackWhitelist("10.0.0.0/8");
        gateway.setAdapterRegistry("submit=bank-submit,query=bank-query");
        gateway.setTimeoutMs(Integer.valueOf(3000));
        gateway.setRetryPolicy("查询重试2次");
        gateway.setStatus(status);
        gateway.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        gateway.setPriority(Integer.valueOf(1));
        return gateway;
    }
}
