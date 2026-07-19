package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 收银台会话查询条件。
 */
@Data
public class CashierSessionQueryDTO {

    /** 会话号/预付单号。 */
    private String sessionNo;
    /** 订单号。 */
    private String orderNo;
    /** 终端场景。 */
    private String terminal = "全部";
    /** 会话状态。 */
    private String sessionStatus = "全部";
}
