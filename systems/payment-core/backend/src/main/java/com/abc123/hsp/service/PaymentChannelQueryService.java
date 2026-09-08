package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;

/**
 * 支付渠道查单服务。
 */
public interface PaymentChannelQueryService {

    /**
     * 查询指定支付单的渠道状态。
     *
     * <p>启用 gateway-access 联动时优先返回外部渠道事实，未启用时保留本地模拟结果。</p>
     */
    PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail);
}
