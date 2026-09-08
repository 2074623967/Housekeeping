package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道下单结果。
 */
@Data
public class PaymentChannelSubmitResultDTO {

    /** 渠道交易流水号。 */
    private String channelTransactionNo;
    /** 支付尝试状态。 */
    private String attemptStatus;
    /** 支付尝试状态类型。 */
    private String attemptStatusType;
    /** 渠道响应留痕。 */
    private String responsePayload;
    /** 渠道原始返回码。 */
    private String channelReturnCode;
    /** 平台标准错误码。 */
    private String standardizedCode;
    /** 平台标准错误文案。 */
    private String standardizedMessage;
    /** 运营处理建议。 */
    private String handlingSuggestion;
    /** 是否可重试。 */
    private String retryable;
    /** 是否需要人工介入。 */
    private String manualInterventionRequired;
    /** 命中的映射版本。 */
    private String mappingVersion;
    /** 是否命中启用且未归档的映射。 */
    private Boolean returnCodeMappingMatched;
}
