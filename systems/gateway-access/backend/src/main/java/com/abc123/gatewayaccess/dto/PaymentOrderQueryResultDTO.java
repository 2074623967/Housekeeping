package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 支付渠道返回的确定性交易事实。
 */
@Data
public class PaymentOrderQueryResultDTO {

    private String tradeStatus;
    private String channelTransactionNo;
    private String querySource;
    private String channelReturnCode;
    private String standardizedCode;
    private String standardizedMessage;
    private String handlingSuggestion;
    private String retryable;
    private String manualInterventionRequired;
    private String mappingVersion;
    private Boolean returnCodeMappingMatched;
}
