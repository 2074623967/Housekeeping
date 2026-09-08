package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportResultDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 渠道返回码批量导入预检与应用测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentReturnCodeImportServiceTest {

    private static final String HEADER = "channelCode,channelReturnCode,standardizedCode,"
            + "standardizedMessage,handlingSuggestion,retryable,manualInterventionRequired,"
            + "mappingVersion,status,priority\n";

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

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
    void shouldMarkDuplicateAndArchivedRowsInvalidAndBlockApply() {
        PaymentChannelReturnCodeConfigDTO archived =
                mapping("offline_bank", "BANK_TIMEOUT", "PAYMENT-CHANNEL-3001", "银行超时", 3, "ARCHIVED");
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate())
                .thenReturn(Collections.singletonList(archived));
        PaymentReturnCodeImportRequestDTO request = request(HEADER
                + "offline_bank,bank_timeout,PAYMENT-CHANNEL-3002,银行超时,人工复核,未知,是,v2,DISABLED,3\n"
                + "offline_bank,BANK_TIMEOUT,PAYMENT-CHANNEL-3002,银行超时,人工复核,未知,是,v2,DISABLED,3\n");
        request.setBaselineFingerprint(previewFingerprint(Collections.singletonList(archived)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).applyReturnCodeImport(request));

        assertTrue(exception.getMessage().contains("无效行"));
        verify(paymentConfigMapper, never()).upsertReturnCodeMapping(any());
        verify(paymentConfigMapper, never()).insertReturnCodeImportBatch(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectApplyWhenConfigurationChangedAfterPreview() {
        PaymentChannelReturnCodeConfigDTO original =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        PaymentChannelReturnCodeConfigDTO changed =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1099", "配置已变化", 1, "ACTIVE");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,用户支付中,等待回调,是,否,v1,ENABLED,1\n";
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.singletonList(original));
        PaymentReturnCodeImportRequestDTO request = request(csv);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        request.setBaselineFingerprint(service.previewReturnCodeImport(request).getBaselineFingerprint());
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(changed));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.applyReturnCodeImport(request));

        assertTrue(exception.getMessage().contains("重新预检"));
        verify(paymentConfigMapper, never()).upsertReturnCodeMapping(any());
    }

    @Test
    void shouldAtomicallyApplyChangedRowsAndRecordAuditBatch() {
        PaymentChannelReturnCodeConfigDTO current =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中", 1, "ACTIVE");
        PaymentChannelReturnCodeConfigDTO updated =
                mapping("wx_h5", "USERPAYING", "PAYMENT-CHANNEL-1001", "等待用户支付", 1, "ACTIVE");
        PaymentChannelReturnCodeConfigDTO created =
                mapping("alipay_h5", "WAIT_BUYER_PAY", "PAYMENT-CHANNEL-2001", "等待买家付款", 2, "ACTIVE");
        String csv = HEADER
                + "wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,等待用户支付,等待回调,是,否,v2,ENABLED,1\n"
                + "alipay_h5,WAIT_BUYER_PAY,PAYMENT-CHANNEL-2001,等待买家付款,等待付款,是,否,v1,ENABLED,2\n";
        when(paymentConfigMapper.findReturnCodeMappings())
                .thenReturn(Collections.singletonList(current))
                .thenReturn(Arrays.asList(updated, created));
        when(paymentConfigMapper.findReturnCodeMappingsForUpdate()).thenReturn(Collections.singletonList(current));
        stubChannels("wx_h5", "alipay_h5");
        PaymentReturnCodeImportRequestDTO request = request(csv);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        request.setBaselineFingerprint(service.previewReturnCodeImport(request).getBaselineFingerprint());

        PaymentReturnCodeImportResultDTO result = service.applyReturnCodeImport(request);

        assertTrue(result.getApplied());
        assertEquals(Integer.valueOf(2), result.getAppliedCount());
        assertEquals(64, result.getResultFingerprint().length());
        assertTrue(result.getBatchNo().startsWith("RCI"));
        verify(paymentConfigMapper, times(2)).upsertReturnCodeMapping(any());
        verify(paymentConfigMapper).insertReturnCodeImportBatch(
                eq(result.getBatchNo()), eq("ops-user"), eq("同步渠道码表"), any(),
                eq(result.getBaselineFingerprint()), eq(result.getResultFingerprint()),
                eq(2), eq(1), eq(1), eq(0), eq(2), eq("APPLIED"), eq("success"));
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
        request.setOperator("ops-user");
        request.setChangeReason("同步渠道码表");
        return request;
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
