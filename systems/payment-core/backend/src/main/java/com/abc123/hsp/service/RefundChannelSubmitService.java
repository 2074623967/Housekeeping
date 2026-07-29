package com.abc123.hsp.service;

import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;

/**
 * 退款渠道下单服务。
 */
public interface RefundChannelSubmitService {

    /**
     * 提交退款到渠道。
     */
    RefundChannelSubmitResultDTO submit(RefundChannelSubmitRequestDTO request);
}
