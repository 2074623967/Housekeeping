package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import com.abc123.hsp.service.PaymentChannelQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 支付渠道查单服务实现。
 */
@Service
public class PaymentChannelQueryServiceImpl implements PaymentChannelQueryService {

    private final List<PaymentChannelQueryAdapter> adapters;

    public PaymentChannelQueryServiceImpl(List<PaymentChannelQueryAdapter> adapters) {
        this.adapters = adapters;
    }

    @Override
    public PaymentDetailDTO query(PaymentDetailDTO paymentDetail) {
        for (PaymentChannelQueryAdapter adapter : adapters) {
            if (adapter.supports(paymentDetail.getChannel())) {
                PaymentChannelQueryResultDTO result = adapter.query(paymentDetail);
                // 当前查单结果只补充查询来源，不直接越过回调流程修改支付状态。
                paymentDetail.setChannelTransactionNo(result.getChannelTransactionNo());
                return paymentDetail;
            }
        }
        throw new IllegalArgumentException("no payment channel query adapter available");
    }
}
