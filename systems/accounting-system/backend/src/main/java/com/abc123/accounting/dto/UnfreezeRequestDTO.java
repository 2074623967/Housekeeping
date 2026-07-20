package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 解冻请求。
 */
@Data
public class UnfreezeRequestDTO {

    private String operatorName;
    private String unfreezeReason;
}
