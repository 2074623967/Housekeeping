package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;

/**
 * 告警供应商投递回执查询适配器。
 */
public interface PaymentIssueAlertReceiptQueryAdapter {

    /**
     * 判断适配器是否支持指定通知通道。
     */
    boolean supports(String channelCode);

    /**
     * 使用已保存的供应商受理事实查询最新投递结果。
     */
    PaymentIssueAlertDeliveryResultDTO query(PaymentIssueAlertLogEntity acceptedDelivery);
}
