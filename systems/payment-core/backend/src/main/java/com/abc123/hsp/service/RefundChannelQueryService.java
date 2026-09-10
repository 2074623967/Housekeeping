package com.abc123.hsp.service;

import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;

/**
 * 退款渠道查单服务。
 */
public interface RefundChannelQueryService {

    RefundChannelQueryResultDTO query(RefundCallbackSourceDTO refund);
}
