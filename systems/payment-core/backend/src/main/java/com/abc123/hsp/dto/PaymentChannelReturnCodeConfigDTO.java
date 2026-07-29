package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码映射配置展示对象。
 */
@Data
public class PaymentChannelReturnCodeConfigDTO {

    /** 渠道编码。 */
    private String channelCode;
    /** 渠道返回码。 */
    private String channelReturnCode;
    /** 标准错误码。 */
    private String standardizedCode;
    /** 标准错误文案。 */
    private String standardizedMessage;
    /** 处理建议。 */
    private String handlingSuggestion;
    /** 是否可重试。 */
    private String retryable;
    /** 是否需要人工介入。 */
    private String manualInterventionRequired;
    /** 映射版本号。 */
    private String mappingVersion;
    /** 归档状态。 */
    private String archiveStatus;
    /** 归档状态样式。 */
    private String archiveStatusType;
    /** 映射状态。 */
    private String status;
    /** 映射状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
