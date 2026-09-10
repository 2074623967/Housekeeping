package com.abc123.hsp.service;

import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;

/**
 * 退款渠道查单适配器。
 */
public interface RefundChannelQueryAdapter {

    boolean supports(String channelCode);

    RefundChannelQueryResultDTO query(RefundCallbackSourceDTO refund);
}
