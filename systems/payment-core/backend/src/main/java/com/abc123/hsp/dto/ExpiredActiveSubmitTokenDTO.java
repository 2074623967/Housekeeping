package com.abc123.hsp.dto;

import lombok.Data;

@Data
public class ExpiredActiveSubmitTokenDTO {

    private String paymentOrderId;
    private String prepayOrderNo;
    private String sourceAppId;
    private String expireAt;
}
