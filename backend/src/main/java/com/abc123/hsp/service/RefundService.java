package com.abc123.hsp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RefundService {

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        items.add(item("REF202607190001", "PAY202607190001", "ORD202607190001", "张女士", "¥68.00", "原路退款", "PROCESSING", "warn", "2026-07-19 11:05:10", "-"));
        items.add(item("REF202607180019", "PAY202607180074", "ORD202607180118", "企业客户-晨星科技", "¥600.00", "原路退款", "SUCCESS", "success", "2026-07-18 19:00:11", "2026-07-18 19:03:26"));
        items.add(item("REF202607170088", "PAY202607160031", "ORD202607160071", "赵女士", "¥200.00", "退转付", "FAIL", "danger", "2026-07-17 15:10:32", "-"));
        return items;
    }

    private Map<String, Object> item(String refundOrderId, String paymentOrderId, String orderNo, String customerName,
                                     String refundAmount, String refundMethod, String status, String statusType,
                                     String appliedAt, String successAt) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("refundOrderId", refundOrderId);
        map.put("paymentOrderId", paymentOrderId);
        map.put("orderNo", orderNo);
        map.put("customerName", customerName);
        map.put("refundAmount", refundAmount);
        map.put("refundMethod", refundMethod);
        map.put("status", status);
        map.put("statusType", statusType);
        map.put("appliedAt", appliedAt);
        map.put("successAt", successAt);
        return map;
    }
}
