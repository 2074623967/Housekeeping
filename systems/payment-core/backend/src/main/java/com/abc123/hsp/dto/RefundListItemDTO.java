package com.abc123.hsp.dto;

import lombok.Data;

@Data
public class RefundListItemDTO {

    private String refundOrderId;
    private String paymentOrderId;
    private String orderNo;
    private String customerName;
    private String refundAmount;
    private String refundMethod;
    private String status;
    private String statusType;
    private String appliedAt;
    private String successAt;
}
