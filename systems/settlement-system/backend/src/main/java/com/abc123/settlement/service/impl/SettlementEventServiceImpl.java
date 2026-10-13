package com.abc123.settlement.service.impl;

import com.abc123.settlement.common.ClearingEventHttpSignature;
import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.entity.SettlementOrderEntity;
import com.abc123.settlement.service.SettlementEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 结算事件服务实现。
 */
@Service
public class SettlementEventServiceImpl implements SettlementEventService {

    private static final int MONEY_MAX_SCALE = 2;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");

    private final SettlementMemoryStore settlementMemoryStore;
    private final SettlementMapper settlementMapper;
    private final RestTemplate restTemplate;

    @Value("${settlement.downstream.reconciliation.fact-url:}")
    private String reconciliationFactUrl;

    @Value("${HSP_SETTLEMENT_RECONCILIATION_FACT_SECRET:}")
    private String reconciliationFactSecret;

    @Autowired
    public SettlementEventServiceImpl(SettlementMemoryStore settlementMemoryStore, SettlementMapper settlementMapper) {
        this(settlementMemoryStore, settlementMapper, new RestTemplate());
    }

    SettlementEventServiceImpl(SettlementMemoryStore settlementMemoryStore,
                               SettlementMapper settlementMapper,
                               RestTemplate restTemplate) {
        this.settlementMemoryStore = settlementMemoryStore;
        this.settlementMapper = settlementMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public PageResultDTO<SettlementEventDTO> list(String eventType, String bizNo, int pageNo, int pageSize) {
        List<SettlementEventDTO> items = settlementMemoryStore.events().stream()
                .filter(item -> eventType == null || eventType.isEmpty() || eventType.equals(item.getEventType()))
                .filter(item -> bizNo == null || bizNo.isEmpty() || bizNo.equals(item.getBizNo()))
                .map(settlementMapper::toEventDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public SettlementEventDTO consumeClearingGenerated(ClearingGeneratedEventRequestDTO request) {
        validateClearingGeneratedAmounts(request);
        SettlementEventDTO event = settlementMapper.toEventDTO(
                settlementMemoryStore.consumeClearingGenerated(request));
        reportReconciliationFact(request);
        return event;
    }

    private void validateClearingGeneratedAmounts(ClearingGeneratedEventRequestDTO request) {
        if (request == null || request.getShouldSettleAmount() == null
                || request.getDeductAmount() == null) {
            throw new IllegalArgumentException("清分事件金额字段不能为空");
        }
        validatePositiveAmount(request.getShouldSettleAmount(), "应结金额必须大于0且最多保留2位小数");
        BigDecimal deductAmount = request.getDeductAmount();
        if (deductAmount.compareTo(BigDecimal.ZERO) < 0
                || deductAmount.scale() > MONEY_MAX_SCALE
                || deductAmount.compareTo(MAX_MONEY_AMOUNT) > 0
                || deductAmount.compareTo(request.getShouldSettleAmount()) > 0) {
            throw new IllegalArgumentException("扣减金额必须为非负数且不能超过应结金额");
        }
        if (request.getNetSettleAmount() != null
                && (request.getNetSettleAmount().scale() > MONEY_MAX_SCALE
                || request.getNetSettleAmount().compareTo(MAX_MONEY_AMOUNT) > 0
                || request.getNetSettleAmount().compareTo(
                request.getShouldSettleAmount().subtract(deductAmount)) != 0)) {
            throw new IllegalArgumentException("净结金额与清分金额不一致");
        }
    }

    private void validatePositiveAmount(BigDecimal amount, String message) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > MONEY_MAX_SCALE
                || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private HttpEntity<String> signedReconciliationFact(Map<String, Object> payload) {
        if (!StringUtils.hasText(reconciliationFactSecret)) {
            throw new IllegalStateException("结算对账事实签名密钥未配置");
        }
        try {
            String body = new ObjectMapper().writeValueAsString(payload);
            String source = "settlement-system";
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
            headers.set("X-HSP-Reconciliation-Fact-Source", source);
            headers.set("X-HSP-Reconciliation-Fact-Timestamp", timestamp);
            headers.set("X-HSP-Reconciliation-Fact-Signature",
                    ClearingEventHttpSignature.sign(source + "\n" + body, timestamp, reconciliationFactSecret));
            return new HttpEntity<>(body, headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("结算对账事实序列化失败", exception);
        }
    }

    private void reportReconciliationFact(ClearingGeneratedEventRequestDTO request) {
        if (!StringUtils.hasText(reconciliationFactUrl)) {
            return;
        }
        SettlementOrderEntity order = settlementMemoryStore.findOrderByClearingTarget(
                request.getClearingNo(), request.getTargetType(), request.getTargetNo());
        if (order == null) {
            throw new IllegalStateException("结算对账事实缺少对应结算单");
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("businessChainId", order.getBusinessChainId());
        payload.put("systemCode", "settlement-system");
        payload.put("factType", "SETTLEMENT_CREATED");
        payload.put("factNo", order.getSettlementNo());
        payload.put("businessNo", request.getPaymentOrderId());
        payload.put("payScene", request.getPayScene());
        payload.put("factStatus", "SUCCESS");
        payload.put("amount", order.getNetSettleAmount());
        Map<String, String> factPayload = new LinkedHashMap<String, String>();
        factPayload.put("settlementNo", order.getSettlementNo());
        factPayload.put("payScene", request.getPayScene() == null ? "" : request.getPayScene());
        try {
            payload.put("payload", new ObjectMapper().writeValueAsString(factPayload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("结算对账事实载荷序列化失败", exception);
        }
        ResponseEntity<Map> response = restTemplate.postForEntity(reconciliationFactUrl.trim(),
                signedReconciliationFact(payload), Map.class);
        Object code = response == null || response.getBody() == null ? null : response.getBody().get("code");
        if (response == null || !response.getStatusCode().is2xxSuccessful()
                || !"0".equals(String.valueOf(code))
                || !(response.getBody().get("data") instanceof Map)) {
            throw new IllegalStateException("结算事实上报失败");
        }
        Map<?, ?> snapshot = (Map<?, ?>) response.getBody().get("data");
        if (!Objects.equals(payload.get("businessChainId"), snapshot.get("businessChainId"))
                || !(snapshot.get("facts") instanceof List)) {
            throw new IllegalStateException("结算对账回执业务链不匹配");
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
        throw new IllegalStateException("结算对账回执缺少本次结算事实");
    }

    private PageResultDTO<SettlementEventDTO> page(List<SettlementEventDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
