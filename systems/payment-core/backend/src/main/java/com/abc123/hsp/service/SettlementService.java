package com.abc123.hsp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettlementService {

    public List<Map<String, Object>> workerList() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        items.add(item("SETW202607190001", "李阿姨", "2026-07-14 ~ 2026-07-19", "¥4,260.00", "¥120.00", "¥4,140.00", "¥0.00", "待审核", "warn", "待出款", "info"));
        items.add(item("SETW202607190002", "周阿姨", "2026-07-14 ~ 2026-07-19", "¥9,860.00", "¥300.00", "¥9,560.00", "¥200.00", "待出款", "info", "出款中", "warn"));
        items.add(item("SETW202607180017", "陈师傅", "2026-07-07 ~ 2026-07-13", "¥3,120.00", "¥0.00", "¥3,120.00", "¥0.00", "已完成", "success", "出款成功", "success"));
        return items;
    }

    private Map<String, Object> item(String settlementOrderId, String workerName, String period,
                                     String amountShouldSettle, String deductAmount, String amountNetSettle,
                                     String depositImpactAmount, String status, String statusType,
                                     String payoutStatus, String payoutStatusType) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("settlementOrderId", settlementOrderId);
        map.put("workerName", workerName);
        map.put("period", period);
        map.put("amountShouldSettle", amountShouldSettle);
        map.put("deductAmount", deductAmount);
        map.put("amountNetSettle", amountNetSettle);
        map.put("depositImpactAmount", depositImpactAmount);
        map.put("status", status);
        map.put("statusType", statusType);
        map.put("payoutStatus", payoutStatus);
        map.put("payoutStatusType", payoutStatusType);
        return map;
    }
}
