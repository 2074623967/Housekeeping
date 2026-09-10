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
     * <p>非 demo 环境只接受已启用的真实查询适配器；本地模拟结果仅用于 demo 联调。</p>
     */
    PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail);
}
