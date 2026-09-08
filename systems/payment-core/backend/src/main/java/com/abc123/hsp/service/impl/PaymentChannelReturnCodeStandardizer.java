package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将渠道原始返回码映射为平台统一运营语义，不改变渠道交易状态事实。
 */
@Service
public class PaymentChannelReturnCodeStandardizer {

    private static final String UNMAPPED_CODE = "PAYMENT-CHANNEL-UNMAPPED";

    private final PaymentConfigMapper paymentConfigMapper;
    private final ObjectMapper objectMapper;

    public PaymentChannelReturnCodeStandardizer(PaymentConfigMapper paymentConfigMapper,
                                                ObjectMapper objectMapper) {
        this.paymentConfigMapper = paymentConfigMapper;
        this.objectMapper = objectMapper;
    }

    public PaymentChannelSubmitResultDTO standardizeSubmit(
            String channelCode, PaymentChannelSubmitResultDTO result) {
        if (result == null || !StringUtils.hasText(result.getChannelReturnCode())) {
            return result;
        }
        PaymentChannelReturnCodeConfigDTO mapping = findMapping(channelCode, result.getChannelReturnCode());
        apply(result, mapping);
        result.setResponsePayload(enrichPayload(result));
        return result;
    }

    public PaymentChannelQueryResultDTO standardizeQuery(
            String channelCode, PaymentChannelQueryResultDTO result) {
        if (result == null || !StringUtils.hasText(result.getChannelReturnCode())) {
            return result;
        }
        PaymentChannelReturnCodeConfigDTO mapping = findMapping(channelCode, result.getChannelReturnCode());
        apply(result, mapping);
        if (mapping == null && "SUCCESS".equalsIgnoreCase(result.getTradeStatus())) {
            result.setStandardizedCode("PAYMENT-CHANNEL-SUCCESS");
            result.setStandardizedMessage("渠道确认支付成功");
            result.setHandlingSuggestion("按支付成功结果执行幂等收口");
            result.setRetryable("否");
            result.setManualInterventionRequired("否");
        }
        return result;
    }

    private PaymentChannelReturnCodeConfigDTO findMapping(String channelCode, String channelReturnCode) {
        if (!StringUtils.hasText(channelCode)) {
            return null;
        }
        try {
            return paymentConfigMapper.findActiveReturnCodeMapping(
                    channelCode.trim(), channelReturnCode.trim());
        } catch (RuntimeException exception) {
            // 渠道调用已经发生时，配置读取异常降级为未匹配，避免诱发重复下单。
            return null;
        }
    }

    private void apply(PaymentChannelSubmitResultDTO result,
                       PaymentChannelReturnCodeConfigDTO mapping) {
        result.setChannelReturnCode(result.getChannelReturnCode().trim());
        result.setReturnCodeMappingMatched(Boolean.valueOf(mapping != null));
        result.setStandardizedCode(mapping == null ? UNMAPPED_CODE : mapping.getStandardizedCode());
        result.setStandardizedMessage(mapping == null ? "未配置渠道返回码映射" : mapping.getStandardizedMessage());
        result.setHandlingSuggestion(mapping == null
                ? "保留渠道原始事实并转人工判断" : mapping.getHandlingSuggestion());
        result.setRetryable(mapping == null ? "未知" : mapping.getRetryable());
        result.setManualInterventionRequired(mapping == null
                ? "是" : mapping.getManualInterventionRequired());
        result.setMappingVersion(mapping == null ? null : mapping.getMappingVersion());
    }

    private void apply(PaymentChannelQueryResultDTO result,
                       PaymentChannelReturnCodeConfigDTO mapping) {
        result.setChannelReturnCode(result.getChannelReturnCode().trim());
        result.setReturnCodeMappingMatched(Boolean.valueOf(mapping != null));
        result.setStandardizedCode(mapping == null ? UNMAPPED_CODE : mapping.getStandardizedCode());
        result.setStandardizedMessage(mapping == null ? "未配置渠道返回码映射" : mapping.getStandardizedMessage());
        result.setHandlingSuggestion(mapping == null
                ? "保留渠道原始事实并转人工判断" : mapping.getHandlingSuggestion());
        result.setRetryable(mapping == null ? "未知" : mapping.getRetryable());
        result.setManualInterventionRequired(mapping == null
                ? "是" : mapping.getManualInterventionRequired());
        result.setMappingVersion(mapping == null ? null : mapping.getMappingVersion());
    }

    private String enrichPayload(PaymentChannelSubmitResultDTO result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(result.getResponsePayload())) {
            try {
                payload.putAll(objectMapper.readValue(
                        result.getResponsePayload(), new TypeReference<Map<String, Object>>() { }));
            } catch (IOException exception) {
                payload.put("rawResponse", result.getResponsePayload());
            }
        }
        payload.put("channelReturnCode", result.getChannelReturnCode());
        payload.put("standardizedCode", result.getStandardizedCode());
        payload.put("standardizedMessage", result.getStandardizedMessage());
        payload.put("handlingSuggestion", result.getHandlingSuggestion());
        payload.put("retryable", result.getRetryable());
        payload.put("manualInterventionRequired", result.getManualInterventionRequired());
        payload.put("mappingVersion", result.getMappingVersion());
        payload.put("returnCodeMappingMatched", result.getReturnCodeMappingMatched());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            return result.getResponsePayload();
        }
    }
}
