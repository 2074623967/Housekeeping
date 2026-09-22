package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.entity.SettlementOrderEntity;
import com.abc123.settlement.service.SettlementEventService;
import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 结算事件服务实现。
 */
@Service
public class SettlementEventServiceImpl implements SettlementEventService {

    private final SettlementMemoryStore settlementMemoryStore;
    private final SettlementMapper settlementMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${settlement.downstream.reconciliation.fact-url:}")
    private String reconciliationFactUrl;

    public SettlementEventServiceImpl(SettlementMemoryStore settlementMemoryStore, SettlementMapper settlementMapper) {
        this.settlementMemoryStore = settlementMemoryStore;
        this.settlementMapper = settlementMapper;
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
        SettlementEventDTO event = settlementMapper.toEventDTO(
                settlementMemoryStore.consumeClearingGenerated(request));
        reportReconciliationFact(request);
        return event;
    }

    private void reportReconciliationFact(ClearingGeneratedEventRequestDTO request) {
        if (!StringUtils.hasText(reconciliationFactUrl)) {
            return;
        }
        SettlementOrderEntity order = settlementMemoryStore.findOrderByClearingTarget(
                request.getClearingNo(), request.getTargetType(), request.getTargetNo());
        if (order == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("businessChainId", order.getBusinessChainId());
        payload.put("systemCode", "settlement-system");
        payload.put("factType", "SETTLEMENT_CREATED");
        payload.put("factNo", order.getSettlementNo());
        payload.put("businessNo", request.getPaymentOrderId());
        payload.put("factStatus", "SUCCESS");
        payload.put("amount", order.getNetSettleAmount());
        payload.put("payload", "{\"settlementNo\":\"" + order.getSettlementNo() + "\"}");
        ResponseEntity<Map> response = restTemplate.postForEntity(reconciliationFactUrl.trim(), payload, Map.class);
        Object code = response.getBody() == null ? null : response.getBody().get("code");
        if (!response.getStatusCode().is2xxSuccessful() || !"0".equals(String.valueOf(code))) {
            throw new IllegalStateException("结算事实上报失败");
        }
    }

    private PageResultDTO<SettlementEventDTO> page(List<SettlementEventDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
