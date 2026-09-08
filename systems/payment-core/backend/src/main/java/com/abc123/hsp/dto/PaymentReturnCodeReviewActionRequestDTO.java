package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码单条变更复核动作参数。
 */
@Data
public class PaymentReturnCodeReviewActionRequestDTO {

    /** 复核人声明；当前未接入可信身份源。 */
    private String reviewer;
    private String reviewComment;
}
