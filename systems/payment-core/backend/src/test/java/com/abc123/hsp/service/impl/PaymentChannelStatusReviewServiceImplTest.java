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
import com.abc123.hsp.dto.PaymentChannelStatusReviewRequestDTO;
import com.abc123.hsp.entity.PaymentChannelConfigEntity;
import com.abc123.hsp.entity.PaymentChannelReviewEntity;
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
        when(paymentConfigMapper.updateChannelStatus("wx_h5", "DISABLED", "danger")).thenReturn(1);
        when(paymentConfigMapper.completeChannelReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findChannelReviews())
                .thenReturn(Collections.singletonList(new PaymentChannelReviewDTO()));

        assertEquals(1, new PaymentConfigServiceImpl(paymentConfigMapper).approveChannelReview(
                "CSR-001", action("checker-b", "确认停用")).size());

        verify(paymentConfigMapper).updateChannelStatus("wx_h5", "DISABLED", "danger");
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
        review.setChannelName("微信H5支付");
        review.setPaymentMethod("微信支付");
        review.setBaseFingerprint(baseline);
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType("ENABLED".equals(candidateStatus) ? "success" : "danger");
        review.setRequester("maker-a");
        review.setReviewStatus(status);
        return review;
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
