package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 交易账单查询条件。
 */
@Data
public class BillQueryDTO {

    /** 账单号。 */
    private String billNo;
    /** 订单号。 */
    private String orderNo;
    /** 账单状态。 */
    private String billStatus = "全部";
}
