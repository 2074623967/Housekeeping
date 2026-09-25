package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import com.abc123.hsp.service.PaymentChannelQueryService;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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
        this.adapters = adapters == null
                ? Collections.emptyList()
                : adapters.stream().filter(adapter -> adapter != null).collect(Collectors.toList());
        this.returnCodeStandardizer = returnCodeStandardizer;
    }

    @Override
    public PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail) {
        if (paymentDetail == null) {
            throw invalidRequest();
        }
        for (PaymentChannelQueryAdapter adapter : adapters) {
            if (adapter.supports(paymentDetail.getChannel())) {
                PaymentChannelQueryResultDTO result = PaymentChannelQueryResultValidator.validateAndNormalize(
                        adapter.query(paymentDetail));
                return PaymentChannelQueryResultValidator.validateAndNormalize(
                        returnCodeStandardizer.standardizeQuery(paymentDetail.getChannel(), result));
            }
        }
        throw new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_QUERY_ADAPTER_MISSING,
                "no payment channel query adapter available"
        );
    }

    private BusinessException invalidRequest() {
        return BusinessException.sanitized(
                ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                "payment channel query request is invalid");
    }
}
