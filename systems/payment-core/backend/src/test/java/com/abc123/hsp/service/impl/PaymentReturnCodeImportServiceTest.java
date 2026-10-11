package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportResultDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewSubmitRequestDTO;
import com.abc123.hsp.entity.PaymentReturnCodeImportReviewEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 渠道返回码批量导入预检与复核测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentReturnCodeImportServiceTest {

    private static final String HEADER = "channelCode,channelReturnCode,standardizedCode,"
            + "standardizedMessage,handlingSuggestion,retryable,manualInterventionRequired,"
            + "mappingVersion,status,priority\n";

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @BeforeEach
    void defaultReturnCodeImportReviewInsertSucceeds() {
        lenient().when(paymentConfigMapper.insertReturnCodeImportReview(any())).thenReturn(1);
        lenient().when(paymentConfigMapper.insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        lenient().when(paymentConfigMapper.upsertReturnCodeMapping(any())).thenReturn(1);
    }

    @Test
    void shouldPreviewCreateUpdateAndUnchangedRowsWithQuotedCsvValue() {
        List<PaymentChannelReturnCodeConfigDTO> current = Arrays.asList(
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE"),
                mapping("alipay_h5", "WAIT_BUYER_PAY", "PAYMENT-CHANNEL-2001", "等待付款", 2, "ACTIVE"));
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(current);
        stubChannels("wx_h5", "alipay_h5", "offline_bank");
        PaymentReturnCodeImportRequestDTO request = request(HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,用户支付中,等待回调,是,否,v1,ENABLED,1\n"
                + "alipay_h5,WAIT_BUYER_PAY,PAYMENT-CHANNEL-2001,等待买家付款,等待付款,是,否,v2,ENABLED,2\n"
                + "offline_bank,BANK_TIMEOUT,PAYMENT-CHANNEL-3001,银行超时,\"人工复核, 稍后补单\",未知,是,v2,DISABLED,3\n");

        PaymentReturnCodeImportResultDTO result =
                new PaymentConfigServiceImpl(paymentConfigMapper).previewReturnCodeImport(request);

        assertTrue(result.getValid());
        assertFalse(result.getApplied());
        assertEquals(Integer.valueOf(1), result.getCreateCount());
        assertEquals(Integer.valueOf(1), result.getUpdateCount());
        assertEquals(Integer.valueOf(1), result.getUnchangedCount());
        assertEquals("人工复核, 稍后补单", result.getItems().get(2).getCandidate().getHandlingSuggestion());
        assertEquals(64, result.getBaselineFingerprint().length());
    }

    @Test
    void shouldMarkDuplicateAndArchivedRowsInvalidAndBlockReviewSubmission() {
        PaymentChannelReturnCodeConfigDTO archived =
                mapping("offline_bank", "BANK_TIMEOUT", "PAYMENT-CHANNEL-3001", "银行超时", 3, "ARCHIVED");
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate())
                .thenReturn(Collections.singletonList(archived));
        PaymentReturnCodeImportReviewSubmitRequestDTO request = submitRequest(HEADER
                + "offline_bank,bank_timeout,PAYMENT-CHANNEL-3002,银行超时,人工复核,未知,是,v2,DISABLED,3\n"
                + "offline_bank,BANK_TIMEOUT,PAYMENT-CHANNEL-3002,银行超时,人工复核,未知,是,v2,DISABLED,3\n");
        request.setBaselineFingerprint(previewFingerprint(Collections.singletonList(archived)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).submitReturnCodeImportReview(request));

        assertTrue(exception.getMessage().contains("无效行"));
        verify(paymentConfigMapper, never()).upsertReturnCodeMapping(any());
        verify(paymentConfigMapper, never()).insertReturnCodeImportReview(any());
        verify(paymentConfigMapper, never()).insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectReviewSubmissionWhenConfigurationChangedAfterPreview() {
        PaymentChannelReturnCodeConfigDTO original =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        PaymentChannelReturnCodeConfigDTO changed =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1099", "配置已变化", 1, "ACTIVE");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,用户支付中,等待回调,是,否,v1,ENABLED,1\n";
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(original));
        PaymentReturnCodeImportRequestDTO previewRequest = request(csv);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentReturnCodeImportReviewSubmitRequestDTO request = submitRequest(csv);
        request.setBaselineFingerprint(service.previewReturnCodeImport(previewRequest).getBaselineFingerprint());
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(changed));
        stubChannels("wx_h5");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.submitReturnCodeImportReview(request));

        assertTrue(exception.getMessage().contains("重新预检"));
        verify(paymentConfigMapper, never()).upsertReturnCodeMapping(any());
    }

    @Test
    void shouldSubmitValidCandidateWithoutChangingRuntimeMappings() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,等待用户支付,等待回调,是,否,v2,ENABLED,1\n";
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(current));
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(current));
        when(paymentConfigMapper.findReturnCodeImportReviews())
                .thenReturn(Collections.singletonList(new PaymentReturnCodeImportReviewDTO()));
        stubChannels("wx_h5");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentReturnCodeImportReviewSubmitRequestDTO request = submitRequest(csv);
        request.setBaselineFingerprint(service.previewReturnCodeImport(request(csv)).getBaselineFingerprint());

        List<PaymentReturnCodeImportReviewDTO> reviews = service.submitReturnCodeImportReview(request);

        assertEquals(1, reviews.size());
        verify(paymentConfigMapper).insertReturnCodeImportReview(any());
        verify(paymentConfigMapper, never()).upsertReturnCodeMapping(any());
        verify(paymentConfigMapper, never()).insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldStopReviewSubmissionWhenDatabaseAffectsNoRow() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,等待用户支付,等待回调,是,否,v2,ENABLED,1\n";
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(current));
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate())
                .thenReturn(Collections.singletonList(current));
        when(paymentConfigMapper.insertReturnCodeImportReview(any())).thenReturn(0);
        stubChannels("wx_h5");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentReturnCodeImportReviewSubmitRequestDTO request = submitRequest(csv);
        request.setBaselineFingerprint(service.previewReturnCodeImport(request(csv)).getBaselineFingerprint());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.submitReturnCodeImportReview(request));

        assertTrue(exception.getMessage().contains("未实际生效"));
        verify(paymentConfigMapper, never()).insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectReviewSubmissionWithoutAnyConfigurationChange() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,用户支付中,等待回调,是,否,v1,ENABLED,1\n";
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(current));
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(current));
        stubChannels("wx_h5");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentReturnCodeImportReviewSubmitRequestDTO request = submitRequest(csv);
        request.setBaselineFingerprint(service.previewReturnCodeImport(request(csv)).getBaselineFingerprint());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.submitReturnCodeImportReview(request));

        assertTrue(exception.getMessage().contains("无需提交复核"));
        verify(paymentConfigMapper, never()).insertReturnCodeImportReview(any());
    }

    @Test
    void shouldApproveWithDistinctReviewerAndAtomicallyApplyCandidate() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        PaymentChannelReturnCodeConfigDTO updated =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "等待用户支付", 1, "ACTIVE");
        updated.setMappingVersion("v2");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,等待用户支付,等待回调,是,否,v2,ENABLED,1\n";
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(current));
        stubChannels("wx_h5");
        String baseline = service.previewReturnCodeImport(request(csv)).getBaselineFingerprint();
        PaymentReturnCodeImportReviewEntity review = review(csv, baseline, "maker-a");
        when(paymentConfigMapper.findReturnCodeImportReviewForUpdate("RCR-001")).thenReturn(review);
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(current));
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(updated));
        when(paymentConfigMapper.upsertReturnCodeMapping(any())).thenReturn(2);
        when(paymentConfigMapper.findReturnCodeImportReviews())
                .thenReturn(Collections.singletonList(new PaymentReturnCodeImportReviewDTO()));
        when(paymentConfigMapper.completeReturnCodeImportReview(
                any(), any(), any(), any(), any(), any())).thenReturn(1);

        List<PaymentReturnCodeImportReviewDTO> reviews =
                service.approveReturnCodeImportReview("RCR-001", action("checker-b", "复核通过"));

        assertEquals(1, reviews.size());
        verify(paymentConfigMapper).upsertReturnCodeMapping(any());
        verify(paymentConfigMapper).insertReturnCodeImportBatch(
                any(), eq("maker-a"), eq("同步渠道码表"), eq(review.getSourceSha256()),
                eq(baseline), any(), eq(1), eq(0), eq(1), eq(0), eq(1), eq("APPLIED"), eq("success"));
        verify(paymentConfigMapper).completeReturnCodeImportReview(
                eq("RCR-001"), eq("APPROVED"), eq("success"), eq("checker-b"), eq("复核通过"), any());
    }

    @Test
    void shouldStopImportApprovalWhenMappingUpsertDoesNotTakeEffect() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,等待用户支付,等待回调,是,否,v2,ENABLED,1\n";
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(current));
        stubChannels("wx_h5");
        String baseline = service.previewReturnCodeImport(request(csv)).getBaselineFingerprint();
        PaymentReturnCodeImportReviewEntity review = review(csv, baseline, "maker-a");
        when(paymentConfigMapper.findReturnCodeImportReviewForUpdate("RCR-001")).thenReturn(review);
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(current));
        when(paymentConfigMapper.upsertReturnCodeMapping(any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.approveReturnCodeImportReview("RCR-001", action("checker-b", "复核通过")));
        verify(paymentConfigMapper, never()).insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(paymentConfigMapper, never()).completeReturnCodeImportReview(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectAliasUpsertReportedAsSuccessfulWithoutExactMapping() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        String csv = HEADER
                + "wx_h5,userpaying,PAYMENT-CHANNEL-1001,等待用户支付,等待回调,是,否,v2,ENABLED,1\n";
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(current));
        stubChannels("wx_h5");
        String baseline = service.previewReturnCodeImport(request(csv)).getBaselineFingerprint();
        when(paymentConfigMapper.findReturnCodeImportReviewForUpdate("RCR-001"))
                .thenReturn(review(csv, baseline, "maker-a"));
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(current));

        assertThrows(IllegalStateException.class,
                () -> service.approveReturnCodeImportReview("RCR-001", action("checker-b", "复核通过")));
        verify(paymentConfigMapper).upsertReturnCodeMapping(any());
        verify(paymentConfigMapper, never()).insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldStopImportApprovalWhenAuditBatchInsertDoesNotTakeEffect() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        PaymentChannelReturnCodeConfigDTO updated =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "等待用户支付", 1, "ACTIVE");
        updated.setMappingVersion("v2");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,等待用户支付,等待回调,是,否,v2,ENABLED,1\n";
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(current));
        stubChannels("wx_h5");
        String baseline = service.previewReturnCodeImport(request(csv)).getBaselineFingerprint();
        PaymentReturnCodeImportReviewEntity review = review(csv, baseline, "maker-a");
        when(paymentConfigMapper.findReturnCodeImportReviewForUpdate("RCR-001")).thenReturn(review);
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(current));
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(updated));
        when(paymentConfigMapper.insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.approveReturnCodeImportReview("RCR-001", action("checker-b", "复核通过")));
        verify(paymentConfigMapper, never()).completeReturnCodeImportReview(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectSamePersonReviewAndAllowDistinctRejection() {
        PaymentReturnCodeImportReviewEntity review = review("csv", "baseline", "maker-a");
        when(paymentConfigMapper.findReturnCodeImportReviewForUpdate("RCR-001")).thenReturn(review);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveReturnCodeImportReview("RCR-001", action("MAKER-A", "同人处理")));

        assertTrue(exception.getMessage().contains("不能相同"));
        when(paymentConfigMapper.findReturnCodeImportReviews())
                .thenReturn(Collections.singletonList(new PaymentReturnCodeImportReviewDTO()));
        when(paymentConfigMapper.completeReturnCodeImportReview(
                any(), any(), any(), any(), any(), any())).thenReturn(1);
        assertEquals(1, service.rejectReturnCodeImportReview(
                "RCR-001", action("checker-b", "数据来源待确认")).size());
        verify(paymentConfigMapper).completeReturnCodeImportReview(
                "RCR-001", "REJECTED", "danger", "checker-b", "数据来源待确认", null);
    }

    @Test
    void shouldRejectRepeatedProcessingOfCompletedReview() {
        PaymentReturnCodeImportReviewEntity review = review("csv", "baseline", "maker-a");
        review.setReviewStatus("APPROVED");
        when(paymentConfigMapper.findReturnCodeImportReviewForUpdate("RCR-001")).thenReturn(review);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .rejectReturnCodeImportReview("RCR-001", action("checker-b", "重复处理")));

        assertTrue(exception.getMessage().contains("已完成"));
        verify(paymentConfigMapper, never()).completeReturnCodeImportReview(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectSecondPendingReviewBeforeLockingMappings() {
        when(paymentConfigMapper.findPendingReturnCodeImportReviewForUpdate())
                .thenReturn(review("csv", "baseline", "maker-existing"));
        PaymentReturnCodeImportReviewSubmitRequestDTO request = submitRequest("csv");
        request.setBaselineFingerprint(String.join("", Collections.nCopies(64, "b")));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitReturnCodeImportReview(request));

        assertTrue(exception.getMessage().contains("已有待复核"));
        verify(paymentConfigMapper, never()).findReturnCodeMappingsForUpdate();
    }

    @Test
    void shouldRejectApprovalWhenStoredCsvDigestDoesNotMatch() {
        PaymentReturnCodeImportReviewEntity review = review("csv", "baseline", "maker-a");
        review.setSourceSha256(String.join("", Collections.nCopies(64, "0")));
        when(paymentConfigMapper.findReturnCodeImportReviewForUpdate("RCR-001")).thenReturn(review);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveReturnCodeImportReview("RCR-001", action("checker-b", "复核通过")));

        assertTrue(exception.getMessage().contains("内容校验失败"));
        verify(paymentConfigMapper, never()).findReturnCodeMappingsForUpdate();
        verify(paymentConfigMapper, never()).upsertReturnCodeMapping(any());
    }

    @Test
    void shouldRejectApprovalWhenConfigurationChangedAfterSubmission() {
        PaymentChannelReturnCodeConfigDTO original =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        PaymentChannelReturnCodeConfigDTO changed =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1099", "配置已变化", 1, "ACTIVE");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,等待用户支付,等待回调,是,否,v2,ENABLED,1\n";
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(original));
        stubChannels("wx_h5");
        String baseline = service.previewReturnCodeImport(request(csv)).getBaselineFingerprint();
        when(paymentConfigMapper.findReturnCodeImportReviewForUpdate("RCR-001"))
                .thenReturn(review(csv, baseline, "maker-a"));
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(changed));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveReturnCodeImportReview(
                        "RCR-001", action("checker-b", "复核通过")));

        assertTrue(exception.getMessage().contains("配置已变化"));
        verify(paymentConfigMapper, never()).upsertReturnCodeMapping(any());
        verify(paymentConfigMapper, never()).insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectUnexpectedCsvHeaderBeforeDiffing() {
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        PaymentReturnCodeImportRequestDTO request = request("channelCode,wrongHeader\nwx_h5,USERPAYING\n");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).previewReturnCodeImport(request));

        assertTrue(exception.getMessage().contains("表头"));
    }

    @Test
    void shouldRejectUnknownChannelInvalidEnumsAndUnversionedUpdate() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(current));
        stubChannels("wx_h5");
        PaymentReturnCodeImportRequestDTO request = request(HEADER
                + "missing_channel,UNKNOWN,PAYMENT-CHANNEL-9001,未知渠道,人工判断,可能,未知,v1,ENABLED,-1\n"
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,新文案,等待回调,是,否,v1,ENABLED,1\n");

        PaymentReturnCodeImportResultDTO result =
                new PaymentConfigServiceImpl(paymentConfigMapper).previewReturnCodeImport(request);

        assertFalse(result.getValid());
        assertEquals(Integer.valueOf(2), result.getInvalidCount());
        assertTrue(result.getItems().get(0).getMessage().contains("支付渠道不存在"));
        assertTrue(result.getItems().get(0).getMessage().contains("retryable"));
        assertTrue(result.getItems().get(1).getMessage().contains("mappingVersion"));
    }

    private String previewFingerprint(List<PaymentChannelReturnCodeConfigDTO> mappings) {
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(mappings);
        return new PaymentConfigServiceImpl(paymentConfigMapper)
                .previewReturnCodeImport(request(HEADER
                        + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,用户支付中,等待回调,是,否,v1,ENABLED,1\n"))
                .getBaselineFingerprint();
    }

    private PaymentReturnCodeImportRequestDTO request(String csv) {
        PaymentReturnCodeImportRequestDTO request = new PaymentReturnCodeImportRequestDTO();
        request.setCsvContent(csv);
        return request;
    }

    private PaymentReturnCodeImportReviewSubmitRequestDTO submitRequest(String csv) {
        PaymentReturnCodeImportReviewSubmitRequestDTO request = new PaymentReturnCodeImportReviewSubmitRequestDTO();
        request.setCsvContent(csv);
        request.setRequester("maker-a");
        request.setChangeReason("同步渠道码表");
        return request;
    }

    private PaymentReturnCodeImportReviewActionRequestDTO action(String reviewer, String comment) {
        PaymentReturnCodeImportReviewActionRequestDTO request = new PaymentReturnCodeImportReviewActionRequestDTO();
        request.setReviewer(reviewer);
        request.setReviewComment(comment);
        return request;
    }

    private PaymentReturnCodeImportReviewEntity review(String csv, String baseline, String requester) {
        PaymentReturnCodeImportReviewEntity review = new PaymentReturnCodeImportReviewEntity();
        review.setRequestNo("RCR-001");
        review.setCsvContent(csv);
        review.setSourceSha256(sha256(csv));
        review.setBaselineFingerprint(baseline);
        review.setRequester(requester);
        review.setRequestReason("同步渠道码表");
        review.setReviewStatus("PENDING_REVIEW");
        return review;
    }

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : digest) {
                builder.append(String.format(java.util.Locale.ROOT, "%02x", current & 0xff));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PaymentChannelReturnCodeConfigDTO mapping(
            String channelCode,
            String channelReturnCode,
            String standardizedCode,
            String standardizedMessage,
            int priority,
            String archiveStatus) {
        PaymentChannelReturnCodeConfigDTO mapping = new PaymentChannelReturnCodeConfigDTO();
        mapping.setChannelCode(channelCode);
        mapping.setChannelReturnCode(channelReturnCode);
        mapping.setStandardizedCode(standardizedCode);
        mapping.setStandardizedMessage(standardizedMessage);
        mapping.setHandlingSuggestion("等待回调");
        mapping.setRetryable("是");
        mapping.setManualInterventionRequired("否");
        mapping.setMappingVersion("v1");
        mapping.setArchiveStatus(archiveStatus);
        mapping.setArchiveStatusType("ACTIVE".equals(archiveStatus) ? "success" : "warn");
        mapping.setStatus("ENABLED");
        mapping.setStatusType("success");
        mapping.setPriority(Integer.valueOf(priority));
        return mapping;
    }

    private void stubChannels(String... channelCodes) {
        List<PaymentChannelConfigDTO> channels = new java.util.ArrayList<>();
        for (String channelCode : channelCodes) {
            PaymentChannelConfigDTO channel = new PaymentChannelConfigDTO();
            channel.setChannelCode(channelCode);
            channels.add(channel);
        }
        when(paymentConfigMapper.findChannels()).thenReturn(channels);
    }
}
