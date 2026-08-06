package com.abc123.refund.dto;

import lombok.Data;

/**
 * 退款成功事件出站查询条件。
 */
@Data
public class RefundOutboxQueryDTO {

    /** 事件编号。 */
    private String eventId;
    /** 聚合编号。 */
    private String aggregateId;
    /** 发送状态。 */
    private String status;
    /** 页码。 */
    private int pageNo = 1;
    /** 每页数量。 */
    private int pageSize = 20;
}
