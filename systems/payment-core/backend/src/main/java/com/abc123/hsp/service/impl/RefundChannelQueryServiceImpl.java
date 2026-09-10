package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.service.RefundChannelQueryAdapter;
import com.abc123.hsp.service.RefundChannelQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 退款渠道查单服务实现。
 */
@Service
public class RefundChannelQueryServiceImpl implements RefundChannelQueryService {

    private final List<RefundChannelQueryAdapter> adapters;

    public RefundChannelQueryServiceImpl(List<RefundChannelQueryAdapter> adapters) {
        this.adapters = adapters;
    }

    @Override
    public RefundChannelQueryResultDTO query(RefundCallbackSourceDTO refund) {
        for (RefundChannelQueryAdapter adapter : adapters) {
            if (adapter.supports(refund.getChannelCode())) {
                return RefundChannelQueryResultValidator.validateAndNormalize(adapter.query(refund));
            }
        }
        throw new BusinessException(
                ErrorCode.REFUND_CHANNEL_QUERY_ADAPTER_MISSING,
                "no refund channel query adapter available");
    }
}
