package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.service.RefundChannelQueryAdapter;
import com.abc123.hsp.service.RefundChannelQueryService;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 退款渠道查单服务实现。
 */
@Service
public class RefundChannelQueryServiceImpl implements RefundChannelQueryService {

    private final List<RefundChannelQueryAdapter> adapters;

    public RefundChannelQueryServiceImpl(List<RefundChannelQueryAdapter> adapters) {
        this.adapters = adapters == null
                ? Collections.emptyList()
                : adapters.stream().filter(adapter -> adapter != null).collect(Collectors.toList());
    }

    @Override
    public RefundChannelQueryResultDTO query(RefundCallbackSourceDTO refund) {
        if (refund == null) {
            throw invalidRequest();
        }
        for (RefundChannelQueryAdapter adapter : adapters) {
            if (adapter.supports(refund.getChannelCode())) {
                return RefundChannelQueryResultValidator.validateAndNormalize(adapter.query(refund));
            }
        }
        throw new BusinessException(
                ErrorCode.REFUND_CHANNEL_QUERY_ADAPTER_MISSING,
                "no refund channel query adapter available");
    }

    private BusinessException invalidRequest() {
        return BusinessException.sanitized(
                ErrorCode.REFUND_CHANNEL_QUERY_FAILED,
                "refund channel query request is invalid");
    }
}
