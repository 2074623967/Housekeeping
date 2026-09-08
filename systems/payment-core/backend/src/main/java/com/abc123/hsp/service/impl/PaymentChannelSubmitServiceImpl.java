package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 支付渠道下单服务实现。
 */
@Service
public class PaymentChannelSubmitServiceImpl implements PaymentChannelSubmitService {

    private final List<PaymentChannelSubmitAdapter> adapters;
    private final PaymentChannelReturnCodeStandardizer returnCodeStandardizer;

    public PaymentChannelSubmitServiceImpl(List<PaymentChannelSubmitAdapter> adapters,
                                           PaymentChannelReturnCodeStandardizer returnCodeStandardizer) {
        this.adapters = adapters;
        this.returnCodeStandardizer = returnCodeStandardizer;
    }

    @Override
    public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
        for (PaymentChannelSubmitAdapter adapter : adapters) {
            if (adapter.supports(request.getResolvedChannelCode())) {
                return returnCodeStandardizer.standardizeSubmit(
                        request.getResolvedChannelCode(), adapter.submit(request));
            }
        }
        throw new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING,
                "no payment channel submit adapter available"
        );
    }
}
