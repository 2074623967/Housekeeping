package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付任务执行日志查询条件。
 */
@Data
public class PaymentTaskRunLogQueryDTO {

    /** 任务编码。 */
    private String taskCode;
    /** 运行方式。 */
    private String runMode;
    /** 任务状态。 */
    private String taskStatus;
    /** 严重等级。 */
    private String severityLevel;
    /** 页码。 */
    private int pageNo = 1;
    /** 每页条数。 */
    private int pageSize = 10;

    /**
     * 分页偏移量。
     */
    public int getOffset() {
        return (Math.max(pageNo, 1) - 1) * Math.max(pageSize, 1);
    }
}
