package com.abc123.accounting.service.impl;

import com.abc123.accounting.common.PaymentEventHttpSignature;
import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.dto.RefundSuccessEventRequestDTO;
import com.abc123.accounting.dto.SettlementCompensationEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 事件服务实现。
 */
@Service
public class AccountingEventServiceImpl implements AccountingEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountingEventServiceImpl.class);
    private final AccountingMemoryStore accountingMemoryStore;
    private final AccountingMapper accountingMapper;
    private final String reconciliationFactUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${HSP_ACCOUNTING_RECONCILIATION_FACT_SECRET:}")
    private String reconciliationFactSecret;

    @Autowired
    public AccountingEventServiceImpl(
            AccountingMemoryStore accountingMemoryStore,
            AccountingMapper accountingMapper,
            @Value("${accounting.downstream.reconciliation.fact-url:}") String reconciliationFactUrl) {
        this(accountingMemoryStore, accountingMapper, reconciliationFactUrl, new RestTemplate(), new ObjectMapper());
    }

    AccountingEventServiceImpl(
            AccountingMemoryStore accountingMemoryStore,
            AccountingMapper accountingMapper,
            String reconciliationFactUrl,
            RestTemplate restTemplate) {
        this(accountingMemoryStore, accountingMapper, reconciliationFactUrl, restTemplate, new ObjectMapper());
    }

    AccountingEventServiceImpl(
            AccountingMemoryStore accountingMemoryStore,
            AccountingMapper accountingMapper,
            String reconciliationFactUrl,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.accountingMemoryStore = accountingMemoryStore;
        this.accountingMapper = accountingMapper;
        this.reconciliationFactUrl = reconciliationFactUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
    @Transactional
    public AccountEventDTO consumePaymentSuccess(PaymentSuccessEventRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())
                || !request.getOrderNo().equals(request.getOrderNo().trim())) {
            throw new IllegalArgumentException("支付成功事件业务订单号非法");
        }
        request.setBusinessChainId(normalizePaymentSuccessBusinessChainId(
                request.getBusinessChainId(), request.getPaymentOrderId()));
        accountingMemoryStore.lockAccountBalance(request.getAccountNo());
        AccountEventDTO existingEvent = findExistingEvent("PAYMENT_SUCCESS", request.getPaymentOrderId());
        if (existingEvent != null) {
            validateExistingEventAccount(request.getAccountNo(), "PAYMENT_SUCCESS_EVENT", existingEvent.getBizNo());
            validateExistingEvent(existingEvent, request.getBusinessChainId(), request.getAmount(), request.getPayScene());
            validateExistingPaymentOrderNo(existingEvent, request.getOrderNo());
            postReconciliationFact(existingEvent.getBusinessChainId(), existingEvent.getEventNo(),
                    existingEvent.getBizNo(), request.getAmount(), existingEvent.getSummary(), request.getPayScene());
            return existingEvent;
        }
        AccountEventDTO event = accountingMapper.toEventDTO(accountingMemoryStore.recordEvent(
                "PAYMENT_SUCCESS",
                request.getPaymentOrderId(),
                "消费支付成功后自动入账",
                paymentSuccessPayload(request),
                resolveBusinessChainId(request.getBusinessChainId(), request.getPaymentOrderId())));
        if (!accountingMemoryStore.hasLedger(request.getAccountNo(), "PAYMENT_SUCCESS_EVENT", request.getPaymentOrderId())) {
            accountingMemoryStore.credit(
                    request.getAccountNo(),
                    "PAYMENT_SUCCESS_EVENT",
                    request.getPaymentOrderId(),
                    request.getAmount(),
                    "支付事件消费者",
                    request.getPayScene());
        }
        postReconciliationFact(event.getBusinessChainId(), event.getEventNo(), event.getBizNo(),
                request.getAmount(), event.getSummary(), request.getPayScene());
        return event;
    }

    @Override
    @Transactional
    public AccountEventDTO consumeClearingGenerated(ClearingGeneratedEventRequestDTO request) {
        request.setBusinessChainId(normalizePaymentSuccessBusinessChainId(
                request.getBusinessChainId(), request.getBizNo()));
        accountingMemoryStore.lockAccountBalance(request.getAccountNo());
        AccountEventDTO existingEvent = findExistingEvent("CLEARING_GENERATED", request.getClearingOrderNo());
        if (existingEvent != null) {
            validateExistingEventAccount(request.getAccountNo(), "CLEARING_GENERATED_EVENT", existingEvent.getBizNo());
            validateExistingEvent(existingEvent, request.getBusinessChainId(), request.getAmount(), request.getPayScene());
            postReconciliationFact(existingEvent.getBusinessChainId(), existingEvent.getEventNo(),
                    existingEvent.getBizNo(), request.getAmount(), existingEvent.getSummary(), request.getPayScene());
            return existingEvent;
        }
        AccountEventDTO event = accountingMapper.toEventDTO(accountingMemoryStore.recordEvent(
                "CLEARING_GENERATED",
                request.getClearingOrderNo(),
                StringUtils.hasText(request.getSummary()) ? request.getSummary() : "消费清分生成事件",
                "{\"bizNo\":\"" + request.getBizNo()
                        + "\",\"payScene\":\"" + (request.getPayScene() == null ? "" : request.getPayScene())
                        + "\",\"amount\":" + request.getAmount() + "}",
                resolveBusinessChainId(request.getBusinessChainId(), request.getClearingOrderNo())));
        if (!accountingMemoryStore.hasLedger(request.getAccountNo(), "CLEARING_GENERATED_EVENT", request.getClearingOrderNo())) {
            accountingMemoryStore.credit(
                    request.getAccountNo(),
                    "CLEARING_GENERATED_EVENT",
                    request.getClearingOrderNo(),
                    request.getAmount(),
                    "清分事件消费者",
                    request.getPayScene());
        }
        postReconciliationFact(event.getBusinessChainId(), event.getEventNo(), event.getBizNo(),
                request.getAmount(), event.getSummary(), request.getPayScene());
        return event;
    }

    @Override
    @Transactional
    public AccountEventDTO consumeRefundSuccess(RefundSuccessEventRequestDTO request) {
        request.setBusinessChainId(normalizePaymentSuccessBusinessChainId(
                request.getBusinessChainId(), request.getPaymentOrderId()));
        accountingMemoryStore.lockAccountBalance(request.getAccountNo());
        AccountEventDTO existingEvent = findExistingEvent("REFUND_SUCCESS", request.getRefundOrderId());
        if (existingEvent != null) {
            validateExistingEventAccount(request.getAccountNo(), "REFUND_SUCCESS_EVENT", existingEvent.getBizNo());
            validateExistingEvent(existingEvent, request.getBusinessChainId(), request.getAmount(), request.getPayScene());
            postReconciliationFact(existingEvent.getBusinessChainId(), existingEvent.getEventNo(),
                    request.getPaymentOrderId(), request.getAmount(), existingEvent.getSummary(), request.getPayScene());
            return existingEvent;
        }
        String summary = request.getSummary() == null || request.getSummary().trim().isEmpty()
                ? "退款成功后冲回原支付入账"
                : request.getSummary().trim();
        AccountEventDTO event = accountingMapper.toEventDTO(accountingMemoryStore.recordEvent(
                "REFUND_SUCCESS",
                request.getRefundOrderId(),
                summary,
                "{\"paymentOrderId\":\"" + request.getPaymentOrderId()
                        + "\",\"payScene\":\"" + (request.getPayScene() == null ? "" : request.getPayScene())
                        + "\",\"amount\":" + request.getAmount() + "}",
                resolveBusinessChainId(request.getBusinessChainId(), request.getPaymentOrderId())));
        if (!accountingMemoryStore.hasLedger(
                request.getAccountNo(), "REFUND_SUCCESS_EVENT", request.getRefundOrderId())) {
            accountingMemoryStore.debit(
                    request.getAccountNo(),
                    "REFUND_SUCCESS_EVENT",
                    request.getRefundOrderId(),
                    request.getAmount(),
                    "退款成功事件消费者",
                    request.getPayScene());
        }
        postReconciliationFact(event.getBusinessChainId(), event.getEventNo(), request.getPaymentOrderId(),
                request.getAmount(), event.getSummary(), request.getPayScene());
        return event;
    }

    @Override
    @Transactional
    public AccountEventDTO consumeSettlementCompensation(SettlementCompensationEventRequestDTO request) {
        if (!StringUtils.hasText(request.getSettlementOrderNo()) || !StringUtils.hasText(request.getBizNo())
                || request.getAmount() == null || request.getAmount().signum() <= 0
                || !("借方".equals(request.getCompensationDirection())
                        || "贷方".equals(request.getCompensationDirection()))) {
            throw new IllegalArgumentException("结算补偿事件业务字段无效");
        }
        accountingMemoryStore.lockAccountBalance(request.getAccountNo());
        AccountEventDTO existingEvent = findExistingEvent("SETTLEMENT_COMPENSATION", request.getSettlementOrderNo());
        if (existingEvent != null) {
            validateExistingEventAccount(request.getAccountNo(), "SETTLEMENT_COMPENSATION_EVENT", existingEvent.getBizNo());
            validateExistingSettlementCompensation(existingEvent, request);
            return existingEvent;
        }
        String operatorName = request.getOperatorName() == null || request.getOperatorName().trim().isEmpty()
                ? "结算补偿消费者"
                : request.getOperatorName();
        AccountEventDTO event = accountingMapper.toEventDTO(accountingMemoryStore.recordEvent(
                "SETTLEMENT_COMPENSATION",
                request.getSettlementOrderNo(),
                request.getSummary() == null || request.getSummary().trim().isEmpty()
                        ? "结算差异补偿入账"
                        : request.getSummary(),
                "{\"bizNo\":\"" + request.getBizNo()
                        + "\",\"amount\":" + request.getAmount()
                        + ",\"compensationDirection\":\"" + request.getCompensationDirection()
                        + "\"}"));
        if (!accountingMemoryStore.hasLedger(
                request.getAccountNo(),
                "SETTLEMENT_COMPENSATION_EVENT",
                request.getSettlementOrderNo())) {
            if ("借方".equals(request.getCompensationDirection())) {
                accountingMemoryStore.debit(
                        request.getAccountNo(),
                        "SETTLEMENT_COMPENSATION_EVENT",
                        request.getSettlementOrderNo(),
                        request.getAmount(),
                        operatorName);
            } else {
                accountingMemoryStore.credit(
                        request.getAccountNo(),
                        "SETTLEMENT_COMPENSATION_EVENT",
                        request.getSettlementOrderNo(),
                        request.getAmount(),
                        operatorName);
            }
        }
        return event;
    }

    /**
     * 先按事件台账做幂等收口，兼容历史演练或旧版本留下“有事件、无流水”的数据形态。
     */
    private AccountEventDTO findExistingEvent(String eventType, String bizNo) {
        if (bizNo == null || bizNo.trim().isEmpty()) {
            return null;
        }
        com.abc123.accounting.entity.AccountEventEntity existingEvent =
                accountingMemoryStore.findEvent(eventType, bizNo);
        return existingEvent == null ? null : accountingMapper.toEventDTO(existingEvent);
    }

    private void validateExistingEventAccount(String accountNo, String ledgerBizType, String bizNo) {
        if (accountingMemoryStore.hasLedgerForOtherAccount(accountNo, ledgerBizType, bizNo)) {
            throw new IllegalArgumentException("重复账务事件账户与原流水不一致");
        }
    }

    private String resolveBusinessChainId(String businessChainId, String businessNo) {
        return businessChainId == null || businessChainId.trim().isEmpty()
                ? "CHAIN-" + businessNo
                : businessChainId.trim();
    }

    private String normalizePaymentSuccessBusinessChainId(String businessChainId, String paymentOrderId) {
        String normalizedPaymentOrderId = paymentOrderId == null ? null : paymentOrderId.trim();
        String normalizedChainId = businessChainId == null ? null : businessChainId.trim();
        if (normalizedChainId == null || normalizedChainId.isEmpty()) {
            return "CHAIN-" + normalizedPaymentOrderId;
        }
        if (normalizedPaymentOrderId == null || normalizedPaymentOrderId.isEmpty()
                || !normalizedChainId.equals("CHAIN-" + normalizedPaymentOrderId)) {
            throw new IllegalArgumentException("业务链路与支付单号不匹配");
        }
        return normalizedChainId;
    }

    private void validateExistingEvent(AccountEventDTO existingEvent, String expectedBusinessChainId,
            BigDecimal expectedAmount, String expectedPayScene) {
        if (!expectedBusinessChainId.equals(existingEvent.getBusinessChainId())) {
            throw new IllegalArgumentException("重复账务事件业务链路与原事件不一致");
        }
        try {
            JsonNode payload = objectMapper.readTree(existingEvent.getPayload());
            JsonNode amount = payload == null ? null : payload.get("amount");
            JsonNode payScene = payload == null ? null : payload.get("payScene");
            if (amount == null || expectedAmount == null
                    || amount.decimalValue().compareTo(expectedAmount) != 0) {
                throw new IllegalArgumentException("重复账务事件金额与原事件不一致");
            }
            String existingPayScene = payScene == null || payScene.isNull() ? null : payScene.asText();
            if (!Objects.equals(StringUtils.hasText(existingPayScene) ? existingPayScene : null,
                    StringUtils.hasText(expectedPayScene) ? expectedPayScene : null)) {
                throw new IllegalArgumentException("重复账务事件支付场景与原事件不一致");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("重复账务事件载荷无效", exception);
        }
    }

    private void validateExistingPaymentOrderNo(AccountEventDTO existingEvent, String expectedOrderNo) {
        try {
            JsonNode payload = objectMapper.readTree(existingEvent.getPayload());
            JsonNode orderNo = payload == null ? null : payload.get("orderNo");
            if (orderNo == null || !orderNo.isTextual() || !expectedOrderNo.equals(orderNo.asText())) {
                throw new IllegalArgumentException("重复支付成功事件订单号与原事件不一致");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("重复支付成功事件载荷无效", exception);
        }
    }

    private String paymentSuccessPayload(PaymentSuccessEventRequestDTO request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", request.getOrderNo());
        payload.put("payScene", request.getPayScene() == null ? "" : request.getPayScene());
        payload.put("amount", request.getAmount());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("支付成功事件载荷无效", exception);
        }
    }

    private void validateExistingSettlementCompensation(AccountEventDTO existingEvent,
            SettlementCompensationEventRequestDTO request) {
        try {
            JsonNode payload = objectMapper.readTree(existingEvent.getPayload());
            JsonNode amount = payload == null ? null : payload.get("amount");
            JsonNode bizNo = payload == null ? null : payload.get("bizNo");
            JsonNode direction = payload == null ? null : payload.get("compensationDirection");
            if (amount == null || !amount.isNumber() || request.getAmount() == null
                    || amount.decimalValue().compareTo(request.getAmount()) != 0
                    || bizNo == null || !Objects.equals(bizNo.asText(), request.getBizNo())
                    || direction == null || !Objects.equals(direction.asText(), request.getCompensationDirection())) {
                throw new IllegalArgumentException("重复结算补偿事件业务事实与原事件不一致");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("重复结算补偿事件载荷无效", exception);
        }
    }

    private HttpEntity<String> signedReconciliationFact(Map<String, Object> payload) {
        if (!StringUtils.hasText(reconciliationFactSecret)) {
            throw new IllegalStateException("账务对账事实签名密钥未配置");
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            String source = "accounting-system";
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
            headers.set("X-HSP-Reconciliation-Fact-Source", source);
            headers.set("X-HSP-Reconciliation-Fact-Timestamp", timestamp);
            headers.set("X-HSP-Reconciliation-Fact-Signature",
                    PaymentEventHttpSignature.signTimed(source + "\n" + body, timestamp, reconciliationFactSecret));
            return new HttpEntity<>(body, headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("账务对账事实序列化失败", exception);
        }
    }

    private void postReconciliationFact(String businessChainId, String eventNo, String businessNo,
            BigDecimal amount, String summary, String payScene) {
        if (!StringUtils.hasText(reconciliationFactUrl)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("businessChainId", businessChainId);
        payload.put("systemCode", "accounting-system");
        payload.put("factType", "ACCOUNTING_POSTED");
        payload.put("factNo", eventNo);
        payload.put("businessNo", businessNo);
        payload.put("payScene", payScene);
        payload.put("factStatus", "SUCCESS");
        payload.put("amount", amount);
        payload.put("payload", "{\"eventNo\":\"" + eventNo + "\",\"summary\":\""
                + (summary == null ? "" : summary) + "\"}");
        ResponseEntity<Map> response = restTemplate.postForEntity(reconciliationFactUrl.trim(),
                signedReconciliationFact(payload), Map.class);
        Object code = response == null || response.getBody() == null ? null : response.getBody().get("code");
        if (response == null || !response.getStatusCode().is2xxSuccessful()
                || !"0".equals(String.valueOf(code))
                || !(response.getBody().get("data") instanceof Map)) {
            LOGGER.warn("账务对账事实回执拒绝 factNo={}, reason=HTTP_CODE_OR_STRUCTURE", eventNo);
            throw new IllegalStateException("账务事实上报失败");
        }
        Map<?, ?> snapshot = (Map<?, ?>) response.getBody().get("data");
        if (!Objects.equals(payload.get("businessChainId"), snapshot.get("businessChainId"))
                || !(snapshot.get("facts") instanceof List)) {
            LOGGER.warn("账务对账事实回执拒绝 factNo={}, reason=CHAIN_OR_FACTS", eventNo);
            throw new IllegalStateException("账务对账回执业务链不匹配");
        }
        for (Object item : (List<?>) snapshot.get("facts")) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> fact = (Map<?, ?>) item;
            if (Objects.equals(payload.get("businessChainId"), fact.get("businessChainId"))
                    && Objects.equals(payload.get("systemCode"), fact.get("systemCode"))
                    && Objects.equals(payload.get("factType"), fact.get("factType"))
                    && Objects.equals(payload.get("factNo"), fact.get("factNo"))
                    && Objects.equals(payload.get("businessNo"), fact.get("businessNo"))
                    && Objects.equals(payload.get("payScene"), fact.get("payScene"))
                    && Objects.equals(payload.get("factStatus"), fact.get("factStatus"))
                    && fact.get("amount") instanceof Number
                    && new BigDecimal(fact.get("amount").toString())
                            .compareTo((BigDecimal) payload.get("amount")) == 0) {
                return;
            }
        }
        LOGGER.warn("账务对账事实回执拒绝 factNo={}, reason=FACT_MISSING", eventNo);
        throw new IllegalStateException("账务对账回执缺少本次入账事实");
    }
}
