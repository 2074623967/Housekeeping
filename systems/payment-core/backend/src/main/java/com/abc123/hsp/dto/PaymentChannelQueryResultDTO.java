package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道查单适配器返回结果。
 */
@Data
public class PaymentChannelQueryResultDTO {

    /** 渠道状态，例如 SUCCESS、WAIT_CALLBACK、FAIL。 */
    private String tradeStatus;
    /** 渠道交易流水号。 */
    private String channelTransactionNo;
    /** 查单来源，例如 WECHAT_SIMULATION、ALIPAY_SIMULATION、LOCAL_SIMULATION_FALLBACK。 */
    private String querySource;
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
