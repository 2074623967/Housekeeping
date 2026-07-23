package com.abc123.hsp.dto;

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
}
