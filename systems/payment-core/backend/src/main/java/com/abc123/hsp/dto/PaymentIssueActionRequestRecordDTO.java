package com.abc123.hsp.dto;

import lombok.Data;

@Data
public class PaymentIssueActionRequestRecordDTO {

    private String requestFingerprint;
    private String claimToken;
    private String responseJson;
}
