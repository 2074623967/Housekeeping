package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentChannelReviewDTO;
import com.abc123.hsp.dto.PaymentChannelReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentChannelStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentChannelUpsertRequestDTO;
import com.abc123.hsp.entity.PaymentChannelConfigEntity;
import com.abc123.hsp.entity.PaymentChannelReviewEntity;
import com.abc123.hsp.entity.PaymentChannelVersionEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/** 支付渠道状态复核服务测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentChannelStatusReviewServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldSubmitCandidateWithoutChangingRuntimeChannel() {
        PaymentChannelConfigEntity current = channel("ENABLED", "MCH-001");
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);
        when(paymentConfigMapper.findLatestChannelVersionNo("wx_h5")).thenReturn(Integer.valueOf(1));
        when(paymentConfigMapper.findChannelReviews())
                .thenReturn(Collections.singletonList(new PaymentChannelReviewDTO()));

        List<PaymentChannelReviewDTO> reviews = new PaymentConfigServiceImpl(paymentConfigMapper)
                .submitChannelStatusReview(statusRequest(false));

        assertEquals(1, reviews.size());
        ArgumentCaptor<PaymentChannelReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentChannelReviewEntity.class);
        verify(paymentConfigMapper).insertChannelReview(captor.capture());
        assertEquals("wx_h5", captor.getValue().getPendingChannelCode());
        assertEquals("DISABLED", captor.getValue().getCandidateStatus());
        assertEquals("STATUS_CHANGE", captor.getValue().getOperationType());
        assertEquals(64, captor.getValue().getBaseFingerprint().length());
        verify(paymentConfigMapper, never()).updateChannelStatus(any(), any(), any());
    }

    @Test
    void shouldRejectSecondPendingReviewBeforeLockingChannel() {
        when(paymentConfigMapper.findPendingChannelReviewForUpdate("wx_h5"))
                .thenReturn(review("PENDING_REVIEW", "DISABLED", "base"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelStatusReview(statusRequest(false)));

        assertTrue(exception.getMessage().contains("已有待复核"));
        verify(paymentConfigMapper, never()).findChannelByCodeForUpdate(any());
    }

    @Test
    void shouldRejectUnchangedStatus() {
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5"))
                .thenReturn(channel("ENABLED", "MCH-001"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelStatusReview(statusRequest(true)));

        assertTrue(exception.getMessage().contains("已启用"));
        verify(paymentConfigMapper, never()).insertChannelReview(any());
    }

    @Test
    void shouldRejectEnablingChannelWithUncoveredCallbackWindow() {
        PaymentChannelConfigEntity current = channel("DISABLED", "MCH-001");
        current.setNotifySignWindowSec(Integer.valueOf(301));
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelStatusReview(statusRequest(true)));

        assertTrue(exception.getMessage().contains("nonce TTL"));
        verify(paymentConfigMapper, never()).insertChannelReview(any());
        verify(paymentConfigMapper, never()).findLatestChannelVersionNo(any());
    }

    @Test
    void shouldTranslateConcurrentPendingReviewConflict() {
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5"))
                .thenReturn(channel("ENABLED", "MCH-001"));
        when(paymentConfigMapper.insertChannelReview(any()))
                .thenThrow(new DuplicateKeyException("uk_channel_review_pending"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelStatusReview(statusRequest(false)));

        assertTrue(exception.getMessage().contains("已有待复核"));
        verify(paymentConfigMapper, never()).updateChannelStatus(any(), any(), any());
    }

    @Test
    void shouldApproveWithDistinctReviewerAndApplyCandidate() {
        PaymentChannelConfigEntity current = channel("ENABLED", "MCH-001");
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "DISABLED", fingerprint(current));
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);
        when(paymentConfigMapper.findLatestChannelVersionNo("wx_h5")).thenReturn(Integer.valueOf(1));
        when(paymentConfigMapper.updateChannel(any(PaymentChannelConfigEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeChannelReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findChannelReviews())
                .thenReturn(Collections.singletonList(new PaymentChannelReviewDTO()));

        assertEquals(1, new PaymentConfigServiceImpl(paymentConfigMapper).approveChannelReview(
                "CSR-001", action("checker-b", "确认停用")).size());

        verify(paymentConfigMapper).updateChannel(org.mockito.ArgumentMatchers.argThat(
                candidate -> "wx_h5".equals(candidate.getChannelCode())
                        && "DISABLED".equals(candidate.getStatus())
                        && "MCH-001".equals(candidate.getMerchantNo())));
        verify(paymentConfigMapper).completeChannelReview(
                "CSR-001", "APPROVED", "success", "checker-b", "确认停用");
    }

    @Test
    void shouldRejectSamePersonAndChangedBaseline() {
        PaymentChannelConfigEntity current = channel("ENABLED", "MCH-CHANGED");
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "DISABLED", "stale");
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        IllegalArgumentException samePerson = assertThrows(IllegalArgumentException.class,
                () -> service.approveChannelReview("CSR-001", action("MAKER-A", "同人处理")));
        assertTrue(samePerson.getMessage().contains("不能相同"));

        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class,
                () -> service.approveChannelReview("CSR-001", action("checker-b", "确认停用")));
        assertTrue(stale.getMessage().contains("已变化"));
        verify(paymentConfigMapper, never()).updateChannelStatus(any(), any(), any());
    }

    @Test
    void shouldRejectReviewWhenVersionChangesWithoutConfigChange() {
        PaymentChannelConfigEntity current = channel("ENABLED", "MCH-001");
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "DISABLED", fingerprint(current));
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);
        when(paymentConfigMapper.findLatestChannelVersionNo("wx_h5")).thenReturn(Integer.valueOf(2));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveChannelReview("CSR-001", action("checker-b", "确认停用")));

        assertTrue(exception.getMessage().contains("已变化"));
        verify(paymentConfigMapper, never()).updateChannel(any());
        verify(paymentConfigMapper, never()).insertChannelVersion(any());
    }

    @Test
    void shouldRejectCandidateWithoutChangingRuntimeChannel() {
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "DISABLED", "base");
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);
        when(paymentConfigMapper.completeChannelReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findChannelReviews())
                .thenReturn(Collections.singletonList(new PaymentChannelReviewDTO()));

        assertEquals(1, new PaymentConfigServiceImpl(paymentConfigMapper).rejectChannelReview(
                "CSR-001", action("checker-b", "保持现状")).size());

        verify(paymentConfigMapper).completeChannelReview(
                "CSR-001", "REJECTED", "danger", "checker-b", "保持现状");
        verify(paymentConfigMapper, never()).updateChannelStatus(any(), any(), any());
    }

    @Test
    void shouldRejectRepeatedProcessingOfCompletedReview() {
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001"))
                .thenReturn(review("APPROVED", "DISABLED", "base"));
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        IllegalArgumentException approve = assertThrows(IllegalArgumentException.class,
                () -> service.approveChannelReview("CSR-001", action("checker-b", "重复通过")));
        IllegalArgumentException reject = assertThrows(IllegalArgumentException.class,
                () -> service.rejectChannelReview("CSR-001", action("checker-b", "重复驳回")));

        assertTrue(approve.getMessage().contains("已完成"));
        assertTrue(reject.getMessage().contains("已完成"));
        verify(paymentConfigMapper, never()).completeChannelReview(any(), any(), any(), any(), any());
        verify(paymentConfigMapper, never()).updateChannelStatus(any(), any(), any());
    }

    @Test
    void shouldSubmitCreateCandidateWithoutPublishingSecrets() {
        when(paymentConfigMapper.findChannelReviews())
                .thenReturn(Collections.singletonList(new PaymentChannelReviewDTO()));

        List<PaymentChannelReviewDTO> reviews = new PaymentConfigServiceImpl(paymentConfigMapper)
                .submitChannelReview(reviewRequest(channelRequest("NEW_H5", "secret-new")));

        assertEquals(1, reviews.size());
        verify(paymentConfigMapper).insertChannelReview(org.mockito.ArgumentMatchers.argThat(
                review -> "new_h5".equals(review.getChannelCode())
                        && "CREATE".equals(review.getOperationType())
                        && review.getBaseFingerprint() == null
                        && "secret-new".equals(review.getCallbackSecret())));
        verify(paymentConfigMapper, never()).insertChannel(any());
        verify(paymentConfigMapper, never()).updateChannel(any());
    }

    @Test
    void shouldPreserveExistingSecretWhenSubmittingUpdate() {
        PaymentChannelConfigEntity current = channel("ENABLED", "MCH-001");
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);
        when(paymentConfigMapper.findLatestChannelVersionNo("wx_h5")).thenReturn(Integer.valueOf(1));
        when(paymentConfigMapper.findChannelReviews()).thenReturn(Collections.emptyList());
        PaymentChannelUpsertRequestDTO candidate = channelRequest("WX_H5", " ");
        candidate.setChannelName("微信 H5 支付新名称");

        new PaymentConfigServiceImpl(paymentConfigMapper).submitChannelReview(reviewRequest(candidate));

        verify(paymentConfigMapper).insertChannelReview(org.mockito.ArgumentMatchers.argThat(
                review -> "UPDATE".equals(review.getOperationType())
                        && "secret".equals(review.getCallbackSecret())
                        && "微信 H5 支付新名称".equals(review.getChannelName())
                        && review.getBaseFingerprint() != null));
        verify(paymentConfigMapper, never()).updateChannel(any());
    }

    @Test
    void shouldApproveCreateCandidateAfterRevalidatingFields() {
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "ENABLED", null);
        review.setOperationType("CREATE");
        review.setChannelCode("new_h5");
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);
        when(paymentConfigMapper.insertChannel(any(PaymentChannelConfigEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeChannelReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findChannelReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveChannelReview(
                "CSR-001", action("checker-b", "确认新增"));

        verify(paymentConfigMapper).insertChannel(org.mockito.ArgumentMatchers.argThat(
                candidate -> "new_h5".equals(candidate.getChannelCode())
                        && "ENABLED".equals(candidate.getStatus())
                        && "secret".equals(candidate.getCallbackSecret())));
        verify(paymentConfigMapper, never()).updateChannel(any());
    }

    @Test
    void shouldApproveUpdateCandidateOnlyWhenBaselineStillMatches() {
        PaymentChannelConfigEntity current = channel("ENABLED", "MCH-001");
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "ENABLED", fingerprint(current));
        review.setOperationType("UPDATE");
        review.setMerchantNo("MCH-UPDATED");
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);
        when(paymentConfigMapper.findLatestChannelVersionNo("wx_h5")).thenReturn(Integer.valueOf(1));
        when(paymentConfigMapper.updateChannel(any(PaymentChannelConfigEntity.class))).thenReturn(1);
        when(paymentConfigMapper.completeChannelReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findChannelReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveChannelReview(
                "CSR-001", action("checker-b", "确认修改"));

        verify(paymentConfigMapper).updateChannel(org.mockito.ArgumentMatchers.argThat(
                candidate -> "MCH-UPDATED".equals(candidate.getMerchantNo())
                        && "secret".equals(candidate.getCallbackSecret())));
    }

    @Test
    void shouldSubmitAndApproveRollbackAsNewVersion() {
        PaymentChannelConfigEntity current = channel("ENABLED", "MCH-NEW");
        PaymentChannelVersionEntity target = version(channel("DISABLED", "MCH-OLD"), 1);
        target.setNotifySignWindowSec(Integer.valueOf(301));
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);
        when(paymentConfigMapper.findLatestChannelVersionNo("wx_h5")).thenReturn(Integer.valueOf(2));
        when(paymentConfigMapper.findChannelVersion("wx_h5", Integer.valueOf(1))).thenReturn(target);
        when(paymentConfigMapper.findChannelReviews()).thenReturn(Collections.emptyList());
        PaymentChannelRollbackRequestDTO request = new PaymentChannelRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(1));
        request.setRequester("maker-a");
        request.setChangeReason("恢复稳定渠道参数");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        service.submitChannelRollbackReview("WX_H5", request);

        ArgumentCaptor<PaymentChannelReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentChannelReviewEntity.class);
        verify(paymentConfigMapper).insertChannelReview(captor.capture());
        PaymentChannelReviewEntity review = captor.getValue();
        assertEquals("ROLLBACK", review.getOperationType());
        assertEquals(Integer.valueOf(2), review.getBaseVersionNo());
        assertEquals(Integer.valueOf(1), review.getSourceVersionNo());
        assertEquals("MCH-OLD", review.getMerchantNo());
        verify(paymentConfigMapper, never()).updateChannel(any());

        when(paymentConfigMapper.findChannelReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.updateChannel(any())).thenReturn(1);
        when(paymentConfigMapper.completeChannelReview(any(), any(), any(), any(), any())).thenReturn(1);
        service.approveChannelReview(review.getRequestNo(), action("checker-b", "确认回滚"));

        verify(paymentConfigMapper).insertChannelVersion(org.mockito.ArgumentMatchers.argThat(
                item -> Integer.valueOf(3).equals(item.getVersionNo())
                        && "REVIEW_ROLLBACK".equals(item.getChangeType())
                        && Integer.valueOf(1).equals(item.getSourceVersionNo())
                        && "secret".equals(item.getCallbackSecret())));
    }

    @Test
    void shouldRejectEnabledRollbackTargetWithUncoveredCallbackWindow() {
        PaymentChannelConfigEntity current = channel("ENABLED", "MCH-NEW");
        PaymentChannelVersionEntity target = version(channel("ENABLED", "MCH-OLD"), 1);
        target.setNotifySignWindowSec(Integer.valueOf(301));
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5")).thenReturn(current);
        when(paymentConfigMapper.findLatestChannelVersionNo("wx_h5")).thenReturn(Integer.valueOf(2));
        when(paymentConfigMapper.findChannelVersion("wx_h5", Integer.valueOf(1))).thenReturn(target);
        PaymentChannelRollbackRequestDTO request = new PaymentChannelRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(1));
        request.setRequester("maker-a");
        request.setChangeReason("恢复历史渠道参数");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelRollbackReview("WX_H5", request));

        assertTrue(exception.getMessage().contains("nonce TTL"));
        verify(paymentConfigMapper, never()).insertChannelReview(any());
    }

    @Test
    void shouldRejectCreateWhenChannelCodeAppearsBeforeApproval() {
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "ENABLED", null);
        review.setOperationType("CREATE");
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);
        when(paymentConfigMapper.findChannelByCodeForUpdate("wx_h5"))
                .thenReturn(channel("ENABLED", "MCH-001"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveChannelReview(
                        "CSR-001", action("checker-b", "确认新增")));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(paymentConfigMapper, never()).insertChannel(any());
        verify(paymentConfigMapper, never()).updateChannel(any());
    }

    @Test
    void shouldRejectUnsafeChannelCandidate() {
        PaymentChannelUpsertRequestDTO rsaCandidate = channelRequest("NEW_H5", "secret-new");
        rsaCandidate.setCallbackSignAlgorithm("RSA2");
        rsaCandidate.setCallbackPublicKey(" ");
        IllegalArgumentException missingPublicKey = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelReview(reviewRequest(rsaCandidate)));
        assertTrue(missingPublicKey.getMessage().contains("公钥"));

        PaymentChannelUpsertRequestDTO insecureUrl = channelRequest("NEW_H5", "secret-new");
        insecureUrl.setCallbackNotifyUrl("http://example.test/notify");
        IllegalArgumentException invalidUrl = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelReview(reviewRequest(insecureUrl)));
        assertTrue(invalidUrl.getMessage().contains("HTTPS"));

        PaymentChannelUpsertRequestDTO missingHost = channelRequest("NEW_H5", "secret-new");
        missingHost.setCallbackNotifyUrl("https://");
        IllegalArgumentException invalidHost = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelReview(reviewRequest(missingHost)));
        assertTrue(invalidHost.getMessage().contains("HTTPS"));

        PaymentChannelUpsertRequestDTO invalidWeight = channelRequest("NEW_H5", "secret-new");
        invalidWeight.setRouteWeight(Integer.valueOf(101));
        IllegalArgumentException weight = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelReview(reviewRequest(invalidWeight)));
        assertTrue(weight.getMessage().contains("不能大于100"));

        PaymentChannelUpsertRequestDTO uncoveredWindow = channelRequest("NEW_H5", "secret-new");
        uncoveredWindow.setNotifySignWindowSec(Integer.valueOf(301));
        IllegalArgumentException nonceTtl = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelReview(reviewRequest(uncoveredWindow)));
        assertTrue(nonceTtl.getMessage().contains("nonce TTL"));
        verify(paymentConfigMapper, never()).insertChannelReview(any());
    }

    @Test
    void shouldRejectChannelSecretWhenRawValueExceedsCapacityBeforeTrim() {
        PaymentChannelUpsertRequestDTO candidate = channelRequest(
                "NEW_H5", String.join("", Collections.nCopies(256, "x")) + " ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitChannelReview(reviewRequest(candidate)));

        assertTrue(exception.getMessage().contains("回调验签密钥"));
        verify(paymentConfigMapper, never()).insertChannelReview(any());
    }

    @Test
    void shouldValidateChannelDailyLimitCapacity() {
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        PaymentChannelUpsertRequestDTO excessiveScale = channelRequest("NEW_H5", "secret-new");
        excessiveScale.setDailyLimit(new BigDecimal("0.001"));
        IllegalArgumentException scale = assertThrows(IllegalArgumentException.class,
                () -> service.submitChannelReview(reviewRequest(excessiveScale)));
        assertTrue(scale.getMessage().contains("16 位整数和 2 位小数"));

        PaymentChannelUpsertRequestDTO excessiveAmount = channelRequest("NEW_H5", "secret-new");
        excessiveAmount.setDailyLimit(new BigDecimal("10000000000000000.00"));
        IllegalArgumentException amount = assertThrows(IllegalArgumentException.class,
                () -> service.submitChannelReview(reviewRequest(excessiveAmount)));
        assertTrue(amount.getMessage().contains("16 位整数和 2 位小数"));

        PaymentChannelUpsertRequestDTO zero = channelRequest("ZERO_H5", "secret-zero");
        zero.setDailyLimit(BigDecimal.ZERO);
        service.submitChannelReview(reviewRequest(zero));

        PaymentChannelUpsertRequestDTO maximum = channelRequest("MAX_H5", "secret-max");
        maximum.setDailyLimit(new BigDecimal("9999999999999999.99"));
        service.submitChannelReview(reviewRequest(maximum));

        verify(paymentConfigMapper).insertChannelReview(org.mockito.ArgumentMatchers.argThat(
                review -> BigDecimal.ZERO.compareTo(review.getDailyLimit()) == 0));
        verify(paymentConfigMapper).insertChannelReview(org.mockito.ArgumentMatchers.argThat(
                review -> new BigDecimal("9999999999999999.99").compareTo(review.getDailyLimit()) == 0));
    }

    @Test
    void shouldRevalidateChannelDailyLimitBeforeApproval() {
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "ENABLED", null);
        review.setOperationType("CREATE");
        review.setChannelCode("new_h5");
        review.setDailyLimit(new BigDecimal("0.001"));
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveChannelReview(
                        "CSR-001", action("checker-b", "确认新增")));

        assertTrue(exception.getMessage().contains("16 位整数和 2 位小数"));
        verify(paymentConfigMapper, never()).insertChannel(any());
        verify(paymentConfigMapper, never()).completeChannelReview(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRevalidateCallbackWindowAgainstCurrentNonceTtlBeforeApproval() {
        PaymentChannelReviewEntity review = review("PENDING_REVIEW", "ENABLED", null);
        review.setOperationType("CREATE");
        review.setChannelCode("new_h5");
        review.setNotifySignWindowSec(Integer.valueOf(500));
        when(paymentConfigMapper.findChannelReviewForUpdate("CSR-001")).thenReturn(review);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        service.configureCallbackNonceTtlSeconds(1000L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveChannelReview(
                        "CSR-001", action("checker-b", "确认新增")));

        assertTrue(exception.getMessage().contains("nonce TTL"));
        verify(paymentConfigMapper, never()).insertChannel(any());
        verify(paymentConfigMapper, never()).completeChannelReview(any(), any(), any(), any(), any());
    }

    @Test
    void shouldAllowDisabledCandidateWithLegacyCallbackWindow() {
        PaymentChannelUpsertRequestDTO candidate = channelRequest("NEW_H5", "secret-new");
        candidate.setEnabled(Boolean.FALSE);
        candidate.setNotifySignWindowSec(Integer.valueOf(301));
        when(paymentConfigMapper.findChannelReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper)
                .submitChannelReview(reviewRequest(candidate));

        verify(paymentConfigMapper).insertChannelReview(org.mockito.ArgumentMatchers.argThat(
                review -> "DISABLED".equals(review.getCandidateStatus())
                        && Integer.valueOf(301).equals(review.getNotifySignWindowSec())));
    }

    private PaymentChannelStatusReviewRequestDTO statusRequest(boolean enabled) {
        PaymentChannelStatusReviewRequestDTO request = new PaymentChannelStatusReviewRequestDTO();
        request.setChannelCode("WX_H5");
        request.setEnabled(Boolean.valueOf(enabled));
        request.setRequester("maker-a");
        request.setChangeReason("调整渠道承接状态");
        return request;
    }

    private PaymentChannelReviewActionRequestDTO action(String reviewer, String comment) {
        PaymentChannelReviewActionRequestDTO request = new PaymentChannelReviewActionRequestDTO();
        request.setReviewer(reviewer);
        request.setReviewComment(comment);
        return request;
    }

    private PaymentChannelReviewEntity review(String status, String candidateStatus, String baseline) {
        PaymentChannelReviewEntity review = new PaymentChannelReviewEntity();
        review.setRequestNo("CSR-001");
        review.setChannelCode("wx_h5");
        review.setOperationType("STATUS_CHANGE");
        review.setBaseVersionNo(Integer.valueOf(1));
        review.setChannelName("微信H5支付");
        review.setPaymentMethod("微信支付");
        review.setMerchantNo("MCH-001");
        review.setMerchantAppId("APP-001");
        review.setCallbackSecret("secret");
        review.setCallbackSignAlgorithm("HMAC-SHA256");
        review.setCallbackPublicKey("public-key");
        review.setCallbackNotifyUrl("https://example.test/notify");
        review.setCertificateProfile("CERT-WX");
        review.setNotifySignWindowSec(Integer.valueOf(300));
        review.setRefundWindowDays(Integer.valueOf(180));
        review.setRiskControlTag("实名校验");
        review.setSceneScope("家政服务");
        review.setDailyLimit(new BigDecimal("500000.00"));
        review.setPriority(Integer.valueOf(1));
        review.setRouteWeight(Integer.valueOf(100));
        review.setBaseFingerprint(baseline);
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType("ENABLED".equals(candidateStatus) ? "success" : "danger");
        review.setRequester("maker-a");
        review.setRequestReason("调整渠道配置");
        review.setReviewStatus(status);
        return review;
    }

    private PaymentChannelVersionEntity version(PaymentChannelConfigEntity channel, int versionNo) {
        PaymentChannelVersionEntity version = new PaymentChannelVersionEntity();
        version.setChannelCode(channel.getChannelCode());
        version.setVersionNo(Integer.valueOf(versionNo));
        version.setChannelName(channel.getChannelName());
        version.setPaymentMethod(channel.getPaymentMethod());
        version.setMerchantNo(channel.getMerchantNo());
        version.setMerchantAppId(channel.getMerchantAppId());
        version.setCallbackSecret(channel.getCallbackSecret());
        version.setCallbackSignAlgorithm(channel.getCallbackSignAlgorithm());
        version.setCallbackPublicKey(channel.getCallbackPublicKey());
        version.setCallbackNotifyUrl(channel.getCallbackNotifyUrl());
        version.setCertificateProfile(channel.getCertificateProfile());
        version.setNotifySignWindowSec(channel.getNotifySignWindowSec());
        version.setRefundWindowDays(channel.getRefundWindowDays());
        version.setRiskControlTag(channel.getRiskControlTag());
        version.setSceneScope(channel.getSceneScope());
        version.setStatus(channel.getStatus());
        version.setStatusType(channel.getStatusType());
        version.setDailyLimit(channel.getDailyLimit());
        version.setPriority(channel.getPriority());
        version.setRouteWeight(channel.getRouteWeight());
        version.setChangeType("REVIEW_RELEASE");
        version.setChangeReason("历史版本");
        return version;
    }

    private PaymentChannelConfigEntity channel(String status, String merchantNo) {
        PaymentChannelConfigEntity channel = new PaymentChannelConfigEntity();
        channel.setChannelCode("wx_h5");
        channel.setChannelName("微信H5支付");
        channel.setPaymentMethod("微信支付");
        channel.setMerchantNo(merchantNo);
        channel.setMerchantAppId("APP-001");
        channel.setCallbackSecret("secret");
        channel.setCallbackSignAlgorithm("HMAC-SHA256");
        channel.setCallbackPublicKey("public-key");
        channel.setCallbackNotifyUrl("https://example.test/notify");
        channel.setCertificateProfile("CERT-WX");
        channel.setNotifySignWindowSec(Integer.valueOf(300));
        channel.setRefundWindowDays(Integer.valueOf(180));
        channel.setRiskControlTag("实名校验");
        channel.setSceneScope("家政服务");
        channel.setStatus(status);
        channel.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        channel.setDailyLimit(new BigDecimal("500000.00"));
        channel.setPriority(Integer.valueOf(1));
        channel.setRouteWeight(Integer.valueOf(100));
        return channel;
    }

    private PaymentChannelReviewSubmitRequestDTO reviewRequest(PaymentChannelUpsertRequestDTO candidate) {
        PaymentChannelReviewSubmitRequestDTO request = new PaymentChannelReviewSubmitRequestDTO();
        request.setRequester("maker-a");
        request.setCandidate(candidate);
        return request;
    }

    private PaymentChannelUpsertRequestDTO channelRequest(String channelCode, String callbackSecret) {
        PaymentChannelUpsertRequestDTO request = new PaymentChannelUpsertRequestDTO();
        request.setChannelCode(channelCode);
        request.setChannelName("微信H5支付");
        request.setPaymentMethod("微信支付");
        request.setMerchantNo("MCH-001");
        request.setMerchantAppId("APP-001");
        request.setCallbackSecret(callbackSecret);
        request.setCallbackSignAlgorithm("HMAC-SHA256");
        request.setCallbackPublicKey("public-key");
        request.setCallbackNotifyUrl("https://example.test/notify");
        request.setCertificateProfile("CERT-WX");
        request.setNotifySignWindowSec(Integer.valueOf(300));
        request.setRefundWindowDays(Integer.valueOf(180));
        request.setRiskControlTag("实名校验");
        request.setSceneScope("家政服务");
        request.setEnabled(Boolean.TRUE);
        request.setDailyLimit(new BigDecimal("500000.00"));
        request.setPriority(Integer.valueOf(1));
        request.setRouteWeight(Integer.valueOf(100));
        request.setChangeReason("新增或编辑渠道");
        return request;
    }

    private String fingerprint(PaymentChannelConfigEntity channel) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, channel.getChannelCode());
        append(canonical, channel.getChannelName());
        append(canonical, channel.getPaymentMethod());
        append(canonical, channel.getMerchantNo());
        append(canonical, channel.getMerchantAppId());
        append(canonical, channel.getCallbackSecret());
        append(canonical, channel.getCallbackSignAlgorithm());
        append(canonical, channel.getCallbackPublicKey());
        append(canonical, channel.getCallbackNotifyUrl());
        append(canonical, channel.getCertificateProfile());
        append(canonical, channel.getNotifySignWindowSec());
        append(canonical, channel.getRefundWindowDays());
        append(canonical, channel.getRiskControlTag());
        append(canonical, channel.getSceneScope());
        append(canonical, channel.getStatus());
        append(canonical, channel.getStatusType());
        append(canonical, channel.getDailyLimit());
        append(canonical, channel.getPriority());
        append(canonical, channel.getRouteWeight());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte current : digest) {
                result.append(String.format(Locale.ROOT, "%02x", current & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void append(StringBuilder builder, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        builder.append(text.length()).append(':').append(text).append('|');
    }
}
