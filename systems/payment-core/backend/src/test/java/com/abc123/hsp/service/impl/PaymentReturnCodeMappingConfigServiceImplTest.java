package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeStatusReviewRequestDTO;
import com.abc123.hsp.entity.PaymentReturnCodeReviewEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 渠道返回码映射状态复核服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentReturnCodeMappingConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldSubmitStatusCandidateWithoutChangingRuntimeMapping() {
        PaymentChannelReturnCodeConfigDTO current = mapping("ENABLED", "v1");
        when(paymentConfigMapper.findReturnCodeMappingForUpdate("offline_bank", "BANK_TIMEOUT"))
                .thenReturn(current);
        when(paymentConfigMapper.findReturnCodeReviews())
                .thenReturn(Collections.singletonList(new PaymentReturnCodeReviewDTO()));

        List<PaymentReturnCodeReviewDTO> reviews = new PaymentConfigServiceImpl(paymentConfigMapper)
                .submitReturnCodeStatusReview(statusRequest(false));

        assertEquals(1, reviews.size());
        ArgumentCaptor<PaymentReturnCodeReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentReturnCodeReviewEntity.class);
        verify(paymentConfigMapper).insertReturnCodeReview(captor.capture());
        assertEquals("DISABLED", captor.getValue().getCandidateStatus());
        assertEquals(64, captor.getValue().getBaseFingerprint().length());
        assertEquals(64, captor.getValue().getPendingMappingKey().length());
        verify(paymentConfigMapper, never()).updateReturnCodeMappingStatus(any(), any(), any(), any());
    }

    @Test
    void shouldRejectSecondPendingReviewBeforeLockingMapping() {
        when(paymentConfigMapper.findPendingReturnCodeReviewForUpdate(any()))
                .thenReturn(review("PENDING_REVIEW", "ENABLED", "base"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitReturnCodeStatusReview(statusRequest(false)));

        assertTrue(exception.getMessage().contains("已有待复核"));
        verify(paymentConfigMapper, never()).findReturnCodeMappingForUpdate(any(), any());
    }

    @Test
    void shouldRejectArchivedMappingWithoutCreatingReview() {
        PaymentChannelReturnCodeConfigDTO current = mapping("ENABLED", "v1");
        current.setArchiveStatus("ARCHIVED");
        when(paymentConfigMapper.findReturnCodeMappingForUpdate("offline_bank", "BANK_TIMEOUT"))
                .thenReturn(current);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitReturnCodeStatusReview(statusRequest(false)));

        assertTrue(exception.getMessage().contains("已归档"));
        verify(paymentConfigMapper, never()).insertReturnCodeReview(any());
        verify(paymentConfigMapper, never()).updateReturnCodeMappingStatus(any(), any(), any(), any());
    }

    @Test
    void shouldApproveWithDistinctReviewerAndApplyCandidate() {
        PaymentChannelReturnCodeConfigDTO current = mapping("ENABLED", "v1");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentReturnCodeReviewEntity review = review(
                "PENDING_REVIEW", "DISABLED", fingerprint(current));
        when(paymentConfigMapper.findReturnCodeReviewForUpdate("RCS-001")).thenReturn(review);
        when(paymentConfigMapper.findReturnCodeMappingForUpdate("offline_bank", "BANK_TIMEOUT"))
                .thenReturn(current);
        when(paymentConfigMapper.updateReturnCodeMappingStatus(
                "offline_bank", "BANK_TIMEOUT", "DISABLED", "danger")).thenReturn(1);
        when(paymentConfigMapper.completeReturnCodeReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findReturnCodeReviews())
                .thenReturn(Collections.singletonList(new PaymentReturnCodeReviewDTO()));

        assertEquals(1, service.approveReturnCodeReview(
                "RCS-001", action("checker-b", "确认停用")).size());

        verify(paymentConfigMapper).updateReturnCodeMappingStatus(
                "offline_bank", "BANK_TIMEOUT", "DISABLED", "danger");
        verify(paymentConfigMapper).completeReturnCodeReview(
                "RCS-001", "APPROVED", "success", "checker-b", "确认停用");
    }

    @Test
    void shouldRejectSamePersonAndChangedBaseline() {
        PaymentChannelReturnCodeConfigDTO current = mapping("ENABLED", "v2");
        PaymentReturnCodeReviewEntity review = review("PENDING_REVIEW", "DISABLED", "stale");
        when(paymentConfigMapper.findReturnCodeReviewForUpdate("RCS-001")).thenReturn(review);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        IllegalArgumentException samePerson = assertThrows(IllegalArgumentException.class,
                () -> service.approveReturnCodeReview(
                        "RCS-001", action("MAKER-A", "同人处理")));
        assertTrue(samePerson.getMessage().contains("不能相同"));

        when(paymentConfigMapper.findReturnCodeMappingForUpdate("offline_bank", "BANK_TIMEOUT"))
                .thenReturn(current);
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class,
                () -> service.approveReturnCodeReview(
                        "RCS-001", action("checker-b", "确认停用")));
        assertTrue(stale.getMessage().contains("已变化"));
        verify(paymentConfigMapper, never()).updateReturnCodeMappingStatus(any(), any(), any(), any());
    }

    @Test
    void shouldRejectCandidateWithoutChangingRuntimeMapping() {
        PaymentReturnCodeReviewEntity review = review("PENDING_REVIEW", "DISABLED", "base");
        when(paymentConfigMapper.findReturnCodeReviewForUpdate("RCS-001")).thenReturn(review);
        when(paymentConfigMapper.completeReturnCodeReview(any(), any(), any(), any(), any())).thenReturn(1);
        when(paymentConfigMapper.findReturnCodeReviews())
                .thenReturn(Collections.singletonList(new PaymentReturnCodeReviewDTO()));

        assertEquals(1, new PaymentConfigServiceImpl(paymentConfigMapper).rejectReturnCodeReview(
                "RCS-001", action("checker-b", "继续观察")).size());

        verify(paymentConfigMapper).completeReturnCodeReview(
                "RCS-001", "REJECTED", "danger", "checker-b", "继续观察");
        verify(paymentConfigMapper, never()).updateReturnCodeMappingStatus(any(), any(), any(), any());
    }

    @Test
    void shouldRejectRepeatedProcessingOfCompletedReview() {
        PaymentReturnCodeReviewEntity review = review("APPROVED", "DISABLED", "base");
        when(paymentConfigMapper.findReturnCodeReviewForUpdate("RCS-001")).thenReturn(review);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        IllegalArgumentException approve = assertThrows(IllegalArgumentException.class,
                () -> service.approveReturnCodeReview(
                        "RCS-001", action("checker-b", "重复通过")));
        IllegalArgumentException reject = assertThrows(IllegalArgumentException.class,
                () -> service.rejectReturnCodeReview(
                        "RCS-001", action("checker-b", "重复驳回")));

        assertTrue(approve.getMessage().contains("已完成"));
        assertTrue(reject.getMessage().contains("已完成"));
        verify(paymentConfigMapper, never()).completeReturnCodeReview(any(), any(), any(), any(), any());
        verify(paymentConfigMapper, never()).updateReturnCodeMappingStatus(any(), any(), any(), any());
    }

    private PaymentReturnCodeStatusReviewRequestDTO statusRequest(boolean enabled) {
        PaymentReturnCodeStatusReviewRequestDTO request = new PaymentReturnCodeStatusReviewRequestDTO();
        request.setChannelCode("offline_bank");
        request.setChannelReturnCode("BANK_TIMEOUT");
        request.setEnabled(Boolean.valueOf(enabled));
        request.setRequester("maker-a");
        request.setChangeReason("调整渠道错误码承接策略");
        return request;
    }

    private PaymentReturnCodeReviewActionRequestDTO action(String reviewer, String comment) {
        PaymentReturnCodeReviewActionRequestDTO request = new PaymentReturnCodeReviewActionRequestDTO();
        request.setReviewer(reviewer);
        request.setReviewComment(comment);
        return request;
    }

    private PaymentReturnCodeReviewEntity review(String status, String candidateStatus, String baseline) {
        PaymentReturnCodeReviewEntity review = new PaymentReturnCodeReviewEntity();
        review.setRequestNo("RCS-001");
        review.setChannelCode("offline_bank");
        review.setChannelReturnCode("BANK_TIMEOUT");
        review.setBaseFingerprint(baseline);
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType("ENABLED".equals(candidateStatus) ? "success" : "danger");
        review.setRequester("maker-a");
        review.setReviewStatus(status);
        return review;
    }

    private PaymentChannelReturnCodeConfigDTO mapping(String status, String version) {
        PaymentChannelReturnCodeConfigDTO mapping = new PaymentChannelReturnCodeConfigDTO();
        mapping.setChannelCode("offline_bank");
        mapping.setChannelReturnCode("BANK_TIMEOUT");
        mapping.setStandardizedCode("PAYMENT-CHANNEL-3001");
        mapping.setStandardizedMessage("银行处理超时");
        mapping.setHandlingSuggestion("人工复核后补单");
        mapping.setRetryable("未知");
        mapping.setManualInterventionRequired("是");
        mapping.setMappingVersion(version);
        mapping.setArchiveStatus("ACTIVE");
        mapping.setStatus(status);
        mapping.setPriority(Integer.valueOf(3));
        return mapping;
    }

    private String fingerprint(PaymentChannelReturnCodeConfigDTO mapping) {
        try {
            StringBuilder canonical = new StringBuilder();
            append(canonical, mapping.getChannelCode());
            append(canonical, mapping.getChannelReturnCode());
            append(canonical, mapping.getStandardizedCode());
            append(canonical, mapping.getStandardizedMessage());
            append(canonical, mapping.getHandlingSuggestion());
            append(canonical, mapping.getRetryable());
            append(canonical, mapping.getManualInterventionRequired());
            append(canonical, mapping.getMappingVersion());
            append(canonical, mapping.getArchiveStatus());
            append(canonical, mapping.getStatus());
            append(canonical, mapping.getPriority());
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte current : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", current & 0xff));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void append(StringBuilder builder, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        builder.append(text.length()).append(':').append(text).append('|');
    }
}
