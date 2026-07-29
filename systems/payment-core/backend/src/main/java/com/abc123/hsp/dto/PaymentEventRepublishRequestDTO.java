package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付事件重发请求。
 */
@Data
public class PaymentEventRepublishRequestDTO {

    /** 事件号。 */
    private String eventNo;
}
