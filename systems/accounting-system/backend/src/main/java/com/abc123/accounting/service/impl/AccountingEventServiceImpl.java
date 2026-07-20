package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 事件服务实现。
 */
@Service
public class AccountingEventServiceImpl implements AccountingEventService {

    private final AccountingMemoryStore accountingMemoryStore;
    private final AccountingMapper accountingMapper;

    public AccountingEventServiceImpl(AccountingMemoryStore accountingMemoryStore, AccountingMapper accountingMapper) {
        this.accountingMemoryStore = accountingMemoryStore;
        this.accountingMapper = accountingMapper;
    }

    @Override
    public PageResultDTO<AccountEventDTO> list(String eventType, String bizNo, int pageNo, int pageSize) {
        String normalizedBizNo = bizNo == null ? "" : bizNo.toLowerCase(Locale.ROOT);
        List<AccountEventDTO> items = accountingMemoryStore.events().stream()
                .filter(item -> eventType == null || eventType.isEmpty() || eventType.equals(item.getEventType()))
                .filter(item -> normalizedBizNo.isEmpty() || item.getBizNo().toLowerCase(Locale.ROOT).contains(normalizedBizNo))
                .map(accountingMapper::toEventDTO)
                .collect(Collectors.toList());
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    @Override
    public AccountEventDTO consumePaymentSuccess(PaymentSuccessEventRequestDTO request) {
        accountingMemoryStore.credit(
                request.getAccountNo(),
                "PAYMENT_SUCCESS_EVENT",
                request.getPaymentOrderId(),
                request.getAmount(),
                "支付事件消费者");
        return accountingMapper.toEventDTO(accountingMemoryStore.recordEvent(
                "PAYMENT_SUCCESS",
                request.getPaymentOrderId(),
                "消费支付成功后自动入账",
                "{\"orderNo\":\"" + request.getOrderNo() + "\",\"amount\":" + request.getAmount() + "}"));
    }

    @Override
    public AccountEventDTO consumeClearingGenerated(ClearingGeneratedEventRequestDTO request) {
        accountingMemoryStore.credit(
                request.getAccountNo(),
                "CLEARING_GENERATED_EVENT",
                request.getClearingOrderNo(),
                request.getAmount(),
                "清分事件消费者");
        return accountingMapper.toEventDTO(accountingMemoryStore.recordEvent(
                "CLEARING_GENERATED",
                request.getClearingOrderNo(),
                request.getSummary(),
                "{\"bizNo\":\"" + request.getBizNo() + "\",\"amount\":" + request.getAmount() + "}"));
    }
}
