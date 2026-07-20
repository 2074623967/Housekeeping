package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.dto.PaymentEventRepublishRequestDTO;

/**
 * 支付事件出站服务。
 */
public interface PaymentEventService {

    /**
     * 查询支付事件出站列表。
     */
    PageResultDTO<PaymentEventListItemDTO> list(PaymentEventQueryDTO query);

    /**
     * 手动重发支付事件。
     */
    PageResultDTO<PaymentEventListItemDTO> republish(PaymentEventRepublishRequestDTO request, PaymentEventQueryDTO query);
}
