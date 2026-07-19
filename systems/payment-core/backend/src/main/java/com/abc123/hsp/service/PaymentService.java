package com.abc123.hsp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        items.add(item("PAY202607190001", "ORD202607190001", "张女士", "¥268.00", "微信", "wx_jsapi", "WX99887766", "SUCCESS", "success", "2026-07-19 09:21:18"));
        items.add(item("PAY202607190002", "ORD202607190002", "王先生", "¥2,000.00", "支付宝", "alipay_h5", "ALI77665544", "WAIT_CALLBACK", "warn", "2026-07-19 10:03:01"));
        items.add(item("PAY202607180074", "ORD202607180118", "企业客户-晨星科技", "¥3,600.00", "银行转账", "offline_bank", "BANK332211", "SUCCESS", "success", "2026-07-18 18:41:09"));
        return items;
    }

    private Map<String, Object> item(String paymentOrderId, String orderNo, String customerName, String amount,
                                     String paymentMethod, String channel, String channelTransactionNo,
                                     String status, String statusType, String createdAt) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("paymentOrderId", paymentOrderId);
        map.put("orderNo", orderNo);
        map.put("customerName", customerName);
        map.put("amount", amount);
        map.put("paymentMethod", paymentMethod);
        map.put("channel", channel);
        map.put("channelTransactionNo", channelTransactionNo);
        map.put("status", status);
        map.put("statusType", statusType);
        map.put("createdAt", createdAt);
        return map;
    }
}
