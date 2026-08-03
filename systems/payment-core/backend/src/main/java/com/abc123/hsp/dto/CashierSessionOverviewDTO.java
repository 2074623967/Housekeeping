package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 收银台会话总览指标。
 */
@Data
public class CashierSessionOverviewDTO {

    /** 会话总数。 */
    private Long totalSessionCount;
    /** 已失效会话数。 */
    private Long expiredSessionCount;
    /** 成功或已完成会话数。 */
    private Long successSessionCount;
    /** 支付中会话数。 */
    private Long payingSessionCount;
    /** 待支付会话数。 */
    private Long pendingSessionCount;
    /** 涉及终端数。 */
    private Integer distinctTerminalCount;
    /** 即将失效会话数。 */
    private Integer expiringSoonCount;
    /** 会话金额合计。 */
    private String totalAmount;
}
