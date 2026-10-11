package com.abc123.hsp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import lombok.Data;

/**
 * 预付单展示模型。
 */
@Data
public class PrepayOrderDTO {

    /**
     * 原生终端支付参数。仅由本次提交响应临时携带，不作为预付单持久化字段。
     */
    private Map<String, String> nativePaymentParams;

    /** 预付单号。 */
    private String prepayOrderNo;
    /** 账单号。 */
    private String billNo;
    /** 订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 待支付金额。 */
    private String amount;
    /** 用户本次选择的支付方式，仅由支付提交响应临时携带。 */
    private String paymentMethod;
    /** 支付场景。 */
    private String payScene;
    /** 收银台标题。 */
    private String cashierTitle;
    /** 收银台状态。 */
    private String cashierStatus;
    /** 收银台状态样式。 */
    private String cashierStatusType;
    /** 关联支付单号。 */
    private String paymentOrderId;

    @JsonIgnore
    private Boolean prepayAmountBindingValid;

    @JsonIgnore
    private String sourceKind;

    @JsonIgnore
    private Integer activePrepayCandidateCount;

    /** 统一业务链路标识。 */
    public String getBusinessChainId() {
        return paymentOrderId == null || paymentOrderId.trim().isEmpty()
                ? null : "CHAIN-" + paymentOrderId.trim();
    }

    /** 支付状态。 */
    private String paymentStatus;
    /** 实际命中的支付渠道，仅由支付提交响应临时携带。 */
    private String channel;
    /** 渠道侧返回的交易流水号，仅由支付提交响应临时携带。 */
    private String channelTransactionNo;
    /** 最近一次支付尝试的状态。 */
    private String latestAttemptStatus;
    /** 最近一次支付尝试状态对应的前端展示样式。 */
    private String latestAttemptStatusType;
    /** 最近一次支付尝试的终态完成时间。 */
    private String latestAttemptCompletedAt;
    /** 支付成功时间。 */
    private String successAt;
    /** 创建时间。 */
    private String createdAt;
    /** 过期时间。 */
    private String expiresAt;
}
