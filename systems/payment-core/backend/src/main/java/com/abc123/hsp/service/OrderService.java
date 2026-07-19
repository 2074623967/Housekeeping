package com.abc123.hsp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        items.add(item("ORD202607190001", "张女士", "深度保洁", "李阿姨", "¥268.00", "¥268.00", "待履约", "info", "已预约", "info", "2026-07-19 09:20:11"));
        items.add(item("ORD202607190002", "王先生", "月嫂套餐", "周阿姨", "¥8,800.00", "¥2,000.00", "待支付", "warn", "待确认", "warn", "2026-07-19 10:02:44"));
        items.add(item("ORD202607180118", "企业客户-晨星科技", "企业保洁", "企业服务组", "¥3,600.00", "¥3,600.00", "已完成", "success", "已验收", "success", "2026-07-18 18:40:07"));
        return items;
    }

    private Map<String, Object> item(String orderNo, String customerName, String serviceType, String workerName,
                                     String orderAmount, String paidAmount, String orderStatus, String orderStatusType,
                                     String fulfillmentStatus, String fulfillmentStatusType, String createdAt) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("orderNo", orderNo);
        map.put("customerName", customerName);
        map.put("serviceType", serviceType);
        map.put("workerName", workerName);
        map.put("orderAmount", orderAmount);
        map.put("paidAmount", paidAmount);
        map.put("orderStatus", orderStatus);
        map.put("orderStatusType", orderStatusType);
        map.put("fulfillmentStatus", fulfillmentStatus);
        map.put("fulfillmentStatusType", fulfillmentStatusType);
        map.put("createdAt", createdAt);
        return map;
    }
}
