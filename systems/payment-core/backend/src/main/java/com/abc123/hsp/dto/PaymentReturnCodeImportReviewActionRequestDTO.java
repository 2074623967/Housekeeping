package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码导入复核动作参数。
 */
@Data
public class PaymentReturnCodeImportReviewActionRequestDTO {

    /** 复核人声明；当前未接入可信身份源。 */
    private String reviewer;
    /** 复核意见或驳回原因。 */
    private String reviewComment;
}
