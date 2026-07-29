package com.abc123.clearing.service.impl;

import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import com.abc123.clearing.service.ClearingEventDispatchService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 清分结果下游派发实现。
 */
@Service
public class ClearingEventDispatchServiceImpl implements ClearingEventDispatchService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final String settlementUrl;
    private final String accountingUrl;
    private final String accountingAccountNo;
    private final RestTemplate restTemplate;

    @Autowired
    public ClearingEventDispatchServiceImpl(
            ClearingMemoryStore clearingMemoryStore,
            @Value("${clearing.downstream.settlement.clearing-generated-url:http://127.0.0.1:18130/api/settlements/events/clearing/generated}")
            String settlementUrl,
            @Value("${clearing.downstream.accounting.clearing-generated-url:http://127.0.0.1:18110/api/accounting/events/clearing/generated}")
            String accountingUrl,
            @Value("${clearing.downstream.accounting.clearing-generated-account-no:ACT10002}")
            String accountingAccountNo) {
        this(clearingMemoryStore, settlementUrl, accountingUrl, accountingAccountNo, new RestTemplate());
    }

    ClearingEventDispatchServiceImpl(
            ClearingMemoryStore clearingMemoryStore,
            String settlementUrl,
            String accountingUrl,
            String accountingAccountNo,
            RestTemplate restTemplate) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.settlementUrl = settlementUrl;
        this.accountingUrl = accountingUrl;
        this.accountingAccountNo = accountingAccountNo;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean publishClearingGenerated(String paymentOrderId) {
        if (!StringUtils.hasText(paymentOrderId)) {
            return false;
        }
        ClearingOrderEntity clearingOrder = clearingMemoryStore.orders().stream()
                .filter(item -> paymentOrderId.equals(item.getPaymentOrderId()))
                .findFirst()
                .orElse(null);
        if (clearingOrder == null) {
            return false;
        }
        List<ShareItemEntity> shares = clearingMemoryStore.sharesByClearingNo(clearingOrder.getClearingNo());
        ShareItemEntity workerShare = findShare(shares, "WORKER").orElse(null);
        BigDecimal workerAmount = workerShare != null ? workerShare.getShareAmount() : clearingOrder.getWorkerAmount();
        try {
            postForSuccess(settlementUrl, buildSettlementPayload(clearingOrder, workerShare, workerAmount));
            postForSuccess(accountingUrl, buildAccountingPayload(clearingOrder, workerAmount));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Optional<ShareItemEntity> findShare(List<ShareItemEntity> shares, String shareType) {
        return shares.stream()
                .filter(item -> shareType.equals(item.getShareType()))
                .findFirst();
    }

    private void postForSuccess(String url, Object payload) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        ResponseEntity<String> response = restTemplate.postForEntity(url.trim(), payload, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("下游系统响应非 2xx");
        }
    }

    private SettlementClearingGeneratedPayload buildSettlementPayload(
            ClearingOrderEntity clearingOrder,
            ShareItemEntity workerShare,
            BigDecimal workerAmount) {
        String targetNo = workerShare == null ? "WRK1001" : workerShare.getShareTargetNo();
        String targetName = workerShare == null ? "待分配服务者" : workerShare.getShareTargetName();
        return new SettlementClearingGeneratedPayload(
                clearingOrder.getClearingNo(),
                clearingOrder.getPaymentOrderId(),
                "WORKER",
                targetNo,
                targetName,
                workerAmount,
                BigDecimal.ZERO,
                workerAmount);
    }

    private AccountingClearingGeneratedPayload buildAccountingPayload(
            ClearingOrderEntity clearingOrder,
            BigDecimal workerAmount) {
        return new AccountingClearingGeneratedPayload(
                accountingAccountNo,
                clearingOrder.getClearingNo(),
                clearingOrder.getPaymentOrderId(),
                workerAmount,
                "清分结果入账至服务者应收");
    }

    /**
     * 结算系统清分结果载荷。
     */
    private static class SettlementClearingGeneratedPayload {

        private final String clearingNo;
        private final String paymentOrderId;
        private final String targetType;
        private final String targetNo;
        private final String targetName;
        private final BigDecimal shouldSettleAmount;
        private final BigDecimal deductAmount;
        private final BigDecimal netSettleAmount;

        SettlementClearingGeneratedPayload(String clearingNo,
                                           String paymentOrderId,
                                           String targetType,
                                           String targetNo,
                                           String targetName,
                                           BigDecimal shouldSettleAmount,
                                           BigDecimal deductAmount,
                                           BigDecimal netSettleAmount) {
            this.clearingNo = clearingNo;
            this.paymentOrderId = paymentOrderId;
            this.targetType = targetType;
            this.targetNo = targetNo;
            this.targetName = targetName;
            this.shouldSettleAmount = shouldSettleAmount;
            this.deductAmount = deductAmount;
            this.netSettleAmount = netSettleAmount;
        }

        public String getClearingNo() {
            return clearingNo;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getTargetType() {
            return targetType;
        }

        public String getTargetNo() {
            return targetNo;
        }

        public String getTargetName() {
            return targetName;
        }

        public BigDecimal getShouldSettleAmount() {
            return shouldSettleAmount;
        }

        public BigDecimal getDeductAmount() {
            return deductAmount;
        }

        public BigDecimal getNetSettleAmount() {
            return netSettleAmount;
        }
    }

    /**
     * 账务系统清分结果载荷。
     */
    private static class AccountingClearingGeneratedPayload {

        private final String accountNo;
        private final String clearingOrderNo;
        private final String bizNo;
        private final BigDecimal amount;
        private final String summary;

        AccountingClearingGeneratedPayload(String accountNo,
                                           String clearingOrderNo,
                                           String bizNo,
                                           BigDecimal amount,
                                           String summary) {
            this.accountNo = accountNo;
            this.clearingOrderNo = clearingOrderNo;
            this.bizNo = bizNo;
            this.amount = amount;
            this.summary = summary;
        }

        public String getAccountNo() {
            return accountNo;
        }

        public String getClearingOrderNo() {
            return clearingOrderNo;
        }

        public String getBizNo() {
            return bizNo;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getSummary() {
            return summary;
        }
    }
}
