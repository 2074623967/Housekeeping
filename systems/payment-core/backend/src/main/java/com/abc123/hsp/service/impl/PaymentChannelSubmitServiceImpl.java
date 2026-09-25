package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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
        this.adapters = adapters == null
                ? Collections.emptyList()
                : adapters.stream().filter(adapter -> adapter != null).collect(Collectors.toList());
        this.returnCodeStandardizer = returnCodeStandardizer;
    }

    @Override
    public void ensureAdapterAvailable(String channelCode) {
        for (PaymentChannelSubmitAdapter adapter : adapters) {
            try {
                if (adapter.supports(channelCode)) {
                    return;
                }
            } catch (RuntimeException exception) {
                throw BusinessException.sanitized(
                        ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                        "payment channel submit adapter selection failed; query before retrying");
            }
        }
        throw adapterMissing();
    }

    @Override
    public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
        if (request == null) {
            throw invalidRequest();
        }
        for (PaymentChannelSubmitAdapter adapter : adapters) {
            boolean supported;
            try {
                supported = adapter.supports(request.getResolvedChannelCode());
            } catch (RuntimeException exception) {
                throw BusinessException.sanitized(
                        ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                        "payment channel submit adapter selection failed; query before retrying");
            }
            if (!supported) {
                continue;
            }
            try {
                PaymentChannelSubmitResultDTO result = PaymentChannelSubmitResultValidator.validateAndNormalize(
                        adapter.submit(request));
                return PaymentChannelSubmitResultValidator.validateAndNormalize(
                        returnCodeStandardizer.standardizeSubmit(request.getResolvedChannelCode(), result));
            } catch (BusinessException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw BusinessException.sanitized(
                        ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                        "payment channel submit failed unexpectedly; query before retrying");
            }
        }
        throw adapterMissing();
    }

    private BusinessException invalidRequest() {
        return BusinessException.sanitized(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                "payment channel submit request is invalid");
    }

    private BusinessException adapterMissing() {
        return new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING,
                "no payment channel submit adapter available"
        );
    }
}
