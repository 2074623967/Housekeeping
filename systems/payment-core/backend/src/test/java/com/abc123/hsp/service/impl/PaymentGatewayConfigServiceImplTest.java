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
import com.abc123.hsp.dto.PaymentGatewayReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayVersionDTO;
import com.abc123.hsp.entity.PaymentGatewayConfigEntity;
import com.abc123.hsp.entity.PaymentGatewayReviewEntity;
import com.abc123.hsp.entity.PaymentGatewayVersionEntity;
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
        assertEquals("STATUS_CHANGE", captor.getValue().getOperationType());
        assertEquals(64, captor.getValue().getBaseFingerprint().length());
        verify(paymentConfigMapper, never()).updateGateway(any());
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
        when(paymentConfigMapper.updateGateway(any(PaymentGatewayConfigEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeGatewayReview(any(), any(), any(), any(), any())).thenReturn(1);

        service.approveGatewayReview(review.getRequestNo(), action("checker-b", "确认启用"));

        verify(paymentConfigMapper).updateGateway(org.mockito.ArgumentMatchers.argThat(
                candidate -> "GATEWAY_BANK_OFFLINE".equals(candidate.getGatewayCode())
                        && "ENABLED".equals(candidate.getStatus())
                        && Integer.valueOf(3000).equals(candidate.getTimeoutMs())));
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
        verify(paymentConfigMapper, never()).updateGateway(any());
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
        verify(paymentConfigMapper, never()).updateGateway(any());
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
        verify(paymentConfigMapper, never()).updateGateway(any());
    }

    @Test
    void shouldSubmitCreateCandidateWithoutChangingRuntime() {
        when(paymentConfigMapper.findGatewayReviews())
                .thenReturn(Collections.singletonList(new PaymentGatewayReviewDTO()));

        new PaymentConfigServiceImpl(paymentConfigMapper).submitGatewayReview(
                reviewRequest(gatewayRequest("gateway_new_acq")));

        verify(paymentConfigMapper).insertGatewayReview(org.mockito.ArgumentMatchers.argThat(
                review -> "GATEWAY_NEW_ACQ".equals(review.getGatewayCode())
                        && "CREATE".equals(review.getOperationType())
                        && review.getBaseFingerprint() == null
                        && "https://gateway.example.test".equals(review.getApiBaseUrl())));
        verify(paymentConfigMapper, never()).insertGateway(any());
        verify(paymentConfigMapper, never()).updateGateway(any());
    }

    @Test
    void shouldSubmitUpdateAndApproveOnlyWhenBaselineMatches() {
        PaymentGatewayConfigEntity current = gateway("ENABLED");
        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_BANK_OFFLINE")).thenReturn(current);
        when(paymentConfigMapper.findGatewayReviews()).thenReturn(Collections.emptyList());
        PaymentGatewayUpsertRequestDTO candidate = gatewayRequest("gateway_bank_offline");
        candidate.setGatewayName("线下银行网关新版");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        service.submitGatewayReview(reviewRequest(candidate));

        ArgumentCaptor<PaymentGatewayReviewEntity> captor = ArgumentCaptor.forClass(PaymentGatewayReviewEntity.class);
        verify(paymentConfigMapper).insertGatewayReview(captor.capture());
        PaymentGatewayReviewEntity review = captor.getValue();
        assertEquals("UPDATE", review.getOperationType());
        assertEquals("线下银行网关新版", review.getGatewayName());
        assertEquals(64, review.getBaseFingerprint().length());
        when(paymentConfigMapper.findGatewayReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.updateGateway(any(PaymentGatewayConfigEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeGatewayReview(any(), any(), any(), any(), any())).thenReturn(1);

        service.approveGatewayReview(review.getRequestNo(), action("checker-b", "确认修改"));

        verify(paymentConfigMapper).updateGateway(org.mockito.ArgumentMatchers.argThat(
                gateway -> "线下银行网关新版".equals(gateway.getGatewayName())
                        && "VALID".equals(gateway.getCertificateStatus())
                        && "success".equals(gateway.getCertificateStatusType())));
    }

    @Test
    void shouldApproveCreateOnlyWhenGatewayCodeRemainsUnused() {
        PaymentGatewayReviewEntity review = review(null);
        review.setOperationType("CREATE");
        review.setGatewayCode("GATEWAY_NEW_ACQ");
        when(paymentConfigMapper.findGatewayReviewForUpdate("GWR-001")).thenReturn(review);
        when(paymentConfigMapper.insertGateway(any(PaymentGatewayConfigEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeGatewayReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findGatewayReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveGatewayReview(
                "GWR-001", action("checker-b", "确认新增"));

        verify(paymentConfigMapper).insertGateway(org.mockito.ArgumentMatchers.argThat(
                gateway -> "GATEWAY_NEW_ACQ".equals(gateway.getGatewayCode())
                        && "ENABLED".equals(gateway.getStatus())));

        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_NEW_ACQ"))
                .thenReturn(gateway("ENABLED"));
        IllegalArgumentException occupied = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveGatewayReview(
                        "GWR-001", action("checker-b", "重复确认新增")));
        assertTrue(occupied.getMessage().contains("已存在"));
    }

    @Test
    void shouldSubmitAndApproveGatewayRollbackAsNewVersion() {
        PaymentGatewayConfigEntity current = gateway("ENABLED");
        PaymentGatewayVersionEntity target = version(gateway("DISABLED"), 1);
        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_BANK_OFFLINE")).thenReturn(current);
        when(paymentConfigMapper.findLatestGatewayVersionNo("GATEWAY_BANK_OFFLINE"))
                .thenReturn(Integer.valueOf(2));
        when(paymentConfigMapper.findGatewayVersion("GATEWAY_BANK_OFFLINE", Integer.valueOf(1)))
                .thenReturn(target);
        when(paymentConfigMapper.findGatewayReviews()).thenReturn(Collections.emptyList());
        PaymentGatewayRollbackRequestDTO rollback = new PaymentGatewayRollbackRequestDTO();
        rollback.setVersionNo(Integer.valueOf(1));
        rollback.setRequester("maker-a");
        rollback.setChangeReason("回退到停用基线排查异常");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        service.submitGatewayRollbackReview("gateway_bank_offline", rollback);

        ArgumentCaptor<PaymentGatewayReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(PaymentGatewayReviewEntity.class);
        verify(paymentConfigMapper).insertGatewayReview(reviewCaptor.capture());
        PaymentGatewayReviewEntity review = reviewCaptor.getValue();
        assertEquals("ROLLBACK", review.getOperationType());
        assertEquals(Integer.valueOf(2), review.getBaseVersionNo());
        assertEquals(Integer.valueOf(1), review.getSourceVersionNo());
        verify(paymentConfigMapper, never()).updateGateway(any());

        when(paymentConfigMapper.findGatewayReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.updateGateway(any(PaymentGatewayConfigEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeGatewayReview(any(), any(), any(), any(), any())).thenReturn(1);
        service.approveGatewayReview(review.getRequestNo(), action("checker-b", "确认回滚"));

        verify(paymentConfigMapper).updateGateway(org.mockito.ArgumentMatchers.argThat(
                gateway -> "DISABLED".equals(gateway.getStatus())));
        verify(paymentConfigMapper).insertGatewayVersion(org.mockito.ArgumentMatchers.argThat(
                version -> "REVIEW_ROLLBACK".equals(version.getChangeType())
                        && Integer.valueOf(1).equals(version.getSourceVersionNo())
                        && Integer.valueOf(3).equals(version.getVersionNo())));
    }

    @Test
    void shouldRejectGatewayApprovalWhenOnlyLatestVersionDrifts() {
        PaymentGatewayConfigEntity current = gateway("ENABLED");
        when(paymentConfigMapper.findGatewayByCodeForUpdate("GATEWAY_BANK_OFFLINE")).thenReturn(current);
        when(paymentConfigMapper.findLatestGatewayVersionNo("GATEWAY_BANK_OFFLINE"))
                .thenReturn(Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(2));
        when(paymentConfigMapper.findGatewayReviews()).thenReturn(Collections.emptyList());
        PaymentGatewayUpsertRequestDTO candidate = gatewayRequest("gateway_bank_offline");
        candidate.setGatewayName("线下银行网关新版");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        service.submitGatewayReview(reviewRequest(candidate));
        ArgumentCaptor<PaymentGatewayReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentGatewayReviewEntity.class);
        verify(paymentConfigMapper).insertGatewayReview(captor.capture());
        PaymentGatewayReviewEntity review = captor.getValue();
        when(paymentConfigMapper.findGatewayReviewForUpdate(review.getRequestNo())).thenReturn(review);

        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class,
                () -> service.approveGatewayReview(review.getRequestNo(), action("checker-b", "确认修改")));

        assertTrue(stale.getMessage().contains("已变化"));
        verify(paymentConfigMapper, never()).updateGateway(any());
    }

    @Test
    void shouldListGatewayVersionsOnlyForExistingGateway() {
        when(paymentConfigMapper.findGatewayByCode("GATEWAY_BANK_OFFLINE")).thenReturn(gateway("ENABLED"));
        when(paymentConfigMapper.findGatewayVersions("GATEWAY_BANK_OFFLINE"))
                .thenReturn(Collections.singletonList(new PaymentGatewayVersionDTO()));

        assertEquals(1, new PaymentConfigServiceImpl(paymentConfigMapper)
                .listGatewayVersions("gateway_bank_offline").size());
    }

    @Test
    void shouldTranslateConcurrentGatewayCreateConflict() {
        PaymentGatewayReviewEntity review = review(null);
        review.setOperationType("CREATE");
        review.setGatewayCode("GATEWAY_NEW_ACQ");
        when(paymentConfigMapper.findGatewayReviewForUpdate("GWR-001")).thenReturn(review);
        when(paymentConfigMapper.insertGateway(any(PaymentGatewayConfigEntity.class)))
                .thenThrow(new DuplicateKeyException("uk_gateway_code"));

        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveGatewayReview(
                        "GWR-001", action("checker-b", "确认新增")));

        assertTrue(conflict.getMessage().contains("已存在"));
        verify(paymentConfigMapper, never()).completeGatewayReview(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectUnsafeGatewayCandidate() {
        PaymentGatewayUpsertRequestDTO insecureUrl = gatewayRequest("gateway_new_acq");
        insecureUrl.setApiBaseUrl("http://gateway.example.test");
        IllegalArgumentException url = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitGatewayReview(reviewRequest(insecureUrl)));
        assertTrue(url.getMessage().contains("HTTPS"));

        PaymentGatewayUpsertRequestDTO invalidCertificate = gatewayRequest("gateway_new_acq");
        invalidCertificate.setCertificateStatus("UNKNOWN");
        IllegalArgumentException certificate = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitGatewayReview(reviewRequest(invalidCertificate)));
        assertTrue(certificate.getMessage().contains("证书状态"));

        PaymentGatewayUpsertRequestDTO invalidTimeout = gatewayRequest("gateway_new_acq");
        invalidTimeout.setTimeoutMs(Integer.valueOf(0));
        IllegalArgumentException timeout = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitGatewayReview(reviewRequest(invalidTimeout)));
        assertTrue(timeout.getMessage().contains("超时时间"));
        verify(paymentConfigMapper, never()).insertGatewayReview(any());
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

    private PaymentGatewayReviewSubmitRequestDTO reviewRequest(PaymentGatewayUpsertRequestDTO candidate) {
        PaymentGatewayReviewSubmitRequestDTO request = new PaymentGatewayReviewSubmitRequestDTO();
        request.setRequester("maker-a");
        request.setCandidate(candidate);
        return request;
    }

    private PaymentGatewayUpsertRequestDTO gatewayRequest(String gatewayCode) {
        PaymentGatewayConfigEntity gateway = gateway("ENABLED");
        PaymentGatewayUpsertRequestDTO request = new PaymentGatewayUpsertRequestDTO();
        request.setGatewayCode(gatewayCode);
        request.setGatewayName(gateway.getGatewayName());
        request.setAccessMode(gateway.getAccessMode());
        request.setChannelScope(gateway.getChannelScope());
        request.setEnvironmentScope(gateway.getEnvironmentScope());
        request.setApiBaseUrl(gateway.getApiBaseUrl());
        request.setProtocolType(gateway.getProtocolType());
        request.setSignAlgorithm(gateway.getSignAlgorithm());
        request.setCertificateAlias(gateway.getCertificateAlias());
        request.setCertificateStatus(gateway.getCertificateStatus());
        request.setReleaseStage(gateway.getReleaseStage());
        request.setGrayStrategy(gateway.getGrayStrategy());
        request.setCallbackWhitelist(gateway.getCallbackWhitelist());
        request.setAdapterRegistry(gateway.getAdapterRegistry());
        request.setTimeoutMs(gateway.getTimeoutMs());
        request.setRetryPolicy(gateway.getRetryPolicy());
        request.setEnabled(Boolean.TRUE);
        request.setPriority(gateway.getPriority());
        request.setChangeReason("新增或调整网关配置");
        return request;
    }

    private PaymentGatewayReviewEntity review(String baseline) {
        PaymentGatewayReviewEntity review = new PaymentGatewayReviewEntity();
        review.setRequestNo("GWR-001");
        review.setGatewayCode("GATEWAY_BANK_OFFLINE");
        review.setOperationType("STATUS_CHANGE");
        PaymentGatewayConfigEntity gateway = gateway("ENABLED");
        review.setGatewayName(gateway.getGatewayName());
        review.setAccessMode(gateway.getAccessMode());
        review.setChannelScope(gateway.getChannelScope());
        review.setEnvironmentScope(gateway.getEnvironmentScope());
        review.setApiBaseUrl(gateway.getApiBaseUrl());
        review.setProtocolType(gateway.getProtocolType());
        review.setSignAlgorithm(gateway.getSignAlgorithm());
        review.setCertificateAlias(gateway.getCertificateAlias());
        review.setCertificateStatus(gateway.getCertificateStatus());
        review.setCertificateStatusType(gateway.getCertificateStatusType());
        review.setReleaseStage(gateway.getReleaseStage());
        review.setGrayStrategy(gateway.getGrayStrategy());
        review.setCallbackWhitelist(gateway.getCallbackWhitelist());
        review.setAdapterRegistry(gateway.getAdapterRegistry());
        review.setTimeoutMs(gateway.getTimeoutMs());
        review.setRetryPolicy(gateway.getRetryPolicy());
        review.setPriority(gateway.getPriority());
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

    private PaymentGatewayVersionEntity version(PaymentGatewayConfigEntity gateway, int versionNo) {
        PaymentGatewayVersionEntity version = new PaymentGatewayVersionEntity();
        version.setGatewayCode(gateway.getGatewayCode());
        version.setVersionNo(Integer.valueOf(versionNo));
        version.setGatewayName(gateway.getGatewayName());
        version.setAccessMode(gateway.getAccessMode());
        version.setChannelScope(gateway.getChannelScope());
        version.setEnvironmentScope(gateway.getEnvironmentScope());
        version.setApiBaseUrl(gateway.getApiBaseUrl());
        version.setProtocolType(gateway.getProtocolType());
        version.setSignAlgorithm(gateway.getSignAlgorithm());
        version.setCertificateAlias(gateway.getCertificateAlias());
        version.setCertificateStatus(gateway.getCertificateStatus());
        version.setCertificateStatusType(gateway.getCertificateStatusType());
        version.setReleaseStage(gateway.getReleaseStage());
        version.setGrayStrategy(gateway.getGrayStrategy());
        version.setCallbackWhitelist(gateway.getCallbackWhitelist());
        version.setAdapterRegistry(gateway.getAdapterRegistry());
        version.setTimeoutMs(gateway.getTimeoutMs());
        version.setRetryPolicy(gateway.getRetryPolicy());
        version.setStatus(gateway.getStatus());
        version.setStatusType(gateway.getStatusType());
        version.setPriority(gateway.getPriority());
        version.setChangeType("BASELINE");
        version.setChangeReason("历史版本");
        return version;
    }
}
