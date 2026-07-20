package com.abc123.accounting.service;

import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;

/**
 * 账务事件服务。
 */
public interface AccountingEventService {

    PageResultDTO<AccountEventDTO> list(String eventType, String bizNo, int pageNo, int pageSize);

    AccountEventDTO consumePaymentSuccess(PaymentSuccessEventRequestDTO request);

    AccountEventDTO consumeClearingGenerated(ClearingGeneratedEventRequestDTO request);
}
