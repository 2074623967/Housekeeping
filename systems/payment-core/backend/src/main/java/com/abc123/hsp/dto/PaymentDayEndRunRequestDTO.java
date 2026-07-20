package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付日终任务触发请求。
 */
@Data
public class PaymentDayEndRunRequestDTO {

    /** 业务日期，格式：yyyy-MM-dd。 */
    private String bizDate;
    /** 触发人。 */
    private String triggeredBy;
    /** 运行方式。 */
    private String runMode;
}
