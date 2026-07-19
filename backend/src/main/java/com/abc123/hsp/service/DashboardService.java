package com.abc123.hsp.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    public Map<String, Object> getSummary() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("cards", Arrays.asList(
                card("pay_success", "今日支付成功金额", "¥128,420", "success", "较昨日 +8.4%"),
                card("refund_amount", "今日退款金额", "¥7,240", "warn", "待复核 3 笔"),
                card("worker_pending", "待结算服务者金额", "¥89,360", "info", "账期待生成"),
                card("recon_gap", "未关闭差异金额", "¥3,120", "danger", "阻断级 2 笔")
        ));
        return result;
    }

    private Map<String, Object> card(String key, String title, String value, String badgeType, String badgeText) {
        Map<String, Object> card = new HashMap<String, Object>();
        card.put("key", key);
        card.put("title", title);
        card.put("value", value);
        card.put("badgeType", badgeType);
        card.put("badgeText", badgeText);
        return card;
    }
}
