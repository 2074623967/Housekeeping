package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.service.RefundChannelQueryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * demo 环境退款查单兜底，只回显当前事实，不制造退款终态。
 */
@Component
@Profile("demo")
@Order(1000)
public class LocalRefundChannelQueryAdapter implements RefundChannelQueryAdapter {

    @Override
    public boolean supports(String channelCode) {
        return true;
    }

    @Override
    public RefundChannelQueryResultDTO query(RefundCallbackSourceDTO refund) {
        RefundChannelQueryResultDTO result = new RefundChannelQueryResultDTO();
        result.setRefundStatus(refund.getStatus());
        result.setChannelRefundNo(refund.getChannelRefundNo());
        result.setQuerySource("LOCAL_SIMULATION_FALLBACK");
        return result;
    }
}
