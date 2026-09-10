package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付任务中心人工操作请求。
 */
@Data
public class PaymentTaskOperatorRequestDTO {

    /** 人工任务操作人。 */
    private String operator;
}
