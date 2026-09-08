package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
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
    private final PaymentChannelReturnCodeStandardizer returnCodeStandardizer;

    public PaymentChannelQueryServiceImpl(List<PaymentChannelQueryAdapter> adapters,
                                          PaymentChannelReturnCodeStandardizer returnCodeStandardizer) {
        this.adapters = adapters;
        this.returnCodeStandardizer = returnCodeStandardizer;
    }

    @Override
    public PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail) {
        for (PaymentChannelQueryAdapter adapter : adapters) {
            if (adapter.supports(paymentDetail.getChannel())) {
                PaymentChannelQueryResultDTO result = adapter.query(paymentDetail);
                if (result != null) {
                    return returnCodeStandardizer.standardizeQuery(paymentDetail.getChannel(), result);
                }
                throw new BusinessException(
                        ErrorCode.PAYMENT_CHANNEL_UNAVAILABLE,
                        "payment channel query returned empty result"
                );
            }
        }
        throw new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_QUERY_ADAPTER_MISSING,
                "no payment channel query adapter available"
        );
    }
}
