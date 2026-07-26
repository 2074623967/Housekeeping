package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 创建预付单请求。
 */
@Data
public class PrepayRequestDTO {

    /** 业务订单号。 */
    private String orderNo;
    /** 支付场景。 */
    private String payScene;
    /** 客户名称。 */
    private String customerName;
    /** 预付金额。 */
    private BigDecimal amount;
    /** 收银台标题。 */
    private String cashierTitle;
}
