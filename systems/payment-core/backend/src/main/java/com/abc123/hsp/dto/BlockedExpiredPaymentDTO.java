package com.abc123.hsp.dto;

import lombok.Data;

@Data
public class BlockedExpiredPaymentDTO {

    private String paymentOrderId;
    private String prepayOrderNo;
    private String orderNo;
    private String expiresAt;
}
