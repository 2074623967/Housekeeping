package com.abc123.clearing.service;

import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;

/**
 * 清分事件服务。
 */
public interface ClearingEventService {

    PageResultDTO<ClearingEventDTO> list(String eventType, String bizNo, int pageNo, int pageSize);

    ClearingEventDTO consumePaymentSuccess(PaymentSuccessEventRequestDTO request);
}
