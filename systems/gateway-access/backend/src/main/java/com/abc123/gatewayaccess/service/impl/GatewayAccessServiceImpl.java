package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.common.ApiResponse;
import com.abc123.gatewayaccess.dto.DashboardMetricDTO;
import com.abc123.gatewayaccess.dto.GatewayAccessSummaryDTO;
import com.abc123.gatewayaccess.dto.GatewayAppDTO;
import com.abc123.gatewayaccess.dto.GatewayCertificateDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayPermissionDTO;
import com.abc123.gatewayaccess.dto.PageResultDTO;
import com.abc123.gatewayaccess.dto.ToggleRequestDTO;
import com.abc123.gatewayaccess.service.GatewayAccessService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 网关接入服务默认实现。
 */
@Service
public class GatewayAccessServiceImpl implements GatewayAccessService {

    private final GatewayAccessMemoryStore store = new GatewayAccessMemoryStore();

    @Override
    public GatewayAccessSummaryDTO summary() {
        GatewayAccessSummaryDTO summary = new GatewayAccessSummaryDTO();
        List<DashboardMetricDTO> metrics = new ArrayList<>();
        metrics.add(metric("接入应用", String.valueOf(store.apps().size()), "info", "应用白名单"));
        metrics.add(metric("启用网关", String.valueOf(countEnabledGateways()), "success", "通道在线"));
        metrics.add(metric("有效证书", String.valueOf(countEnabledCertificates()), "warn", "30天内需关注"));
        metrics.add(metric("启用权限", String.valueOf(countEnabledPermissions()), "danger", "权限治理"));
        summary.setMetrics(metrics);
        List<String> highlights = new ArrayList<>();
        highlights.add("渠道接入与证书轮换已纳入 gateway-access");
        highlights.add("payment-core 只保留支付主链路，不再承接接入治理台账");
        highlights.add("后续将继续补灰度发布、环境隔离和调用方审计");
        summary.setHighlights(highlights);
        return summary;
    }

    @Override
    public PageResultDTO<GatewayAppDTO> applications() {
        return page(store.apps(), 1, 20);
    }

    @Override
    public PageResultDTO<GatewayChannelDTO> gateways() {
        return page(store.gateways(), 1, 20);
    }

    @Override
    public PageResultDTO<GatewayCertificateDTO> certificates() {
        return page(store.certificates(), 1, 20);
    }

    @Override
    public PageResultDTO<GatewayPermissionDTO> permissions() {
        return page(store.permissions(), 1, 20);
    }

    @Override
    public GatewayAccessSummaryDTO toggleApplication(ToggleRequestDTO request) {
        toggle(request, "接入应用", store::toggleApp);
        return summary();
    }

    @Override
    public GatewayAccessSummaryDTO toggleGateway(ToggleRequestDTO request) {
        toggle(request, "网关", store::toggleGateway);
        return summary();
    }

    @Override
    public GatewayAccessSummaryDTO toggleCertificate(ToggleRequestDTO request) {
        toggle(request, "证书", store::toggleCertificate);
        return summary();
    }

    @Override
    public GatewayAccessSummaryDTO togglePermission(ToggleRequestDTO request) {
        toggle(request, "权限", store::togglePermission);
        return summary();
    }

    private DashboardMetricDTO metric(String title, String value, String badgeType, String badgeText) {
        DashboardMetricDTO metric = new DashboardMetricDTO();
        metric.setTitle(title);
        metric.setValue(value);
        metric.setBadgeType(badgeType);
        metric.setBadgeText(badgeText);
        return metric;
    }

    private int countEnabledGateways() {
        int count = 0;
        for (GatewayChannelDTO gateway : store.gateways()) {
            if ("ENABLED".equals(gateway.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private int countEnabledCertificates() {
        int count = 0;
        for (GatewayCertificateDTO certificate : store.certificates()) {
            if ("ENABLED".equals(certificate.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private int countEnabledPermissions() {
        int count = 0;
        for (GatewayPermissionDTO permission : store.permissions()) {
            if ("ENABLED".equals(permission.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private <T> PageResultDTO<T> page(List<T> records, int pageNo, int pageSize) {
        return new PageResultDTO<>(records, records.size(), pageNo, pageSize);
    }

    private void toggle(ToggleRequestDTO request, String label, ToggleHandler handler) {
        if (request == null || !StringUtils.hasText(request.getConfigCode())) {
            throw new IllegalArgumentException(label + "编码不能为空");
        }
        boolean enabled = !Boolean.FALSE.equals(request.getEnabled());
        boolean updated = handler.toggle(request.getConfigCode().trim(), enabled);
        if (!updated) {
            throw new IllegalArgumentException(label + "不存在");
        }
    }

    private interface ToggleHandler {
        boolean toggle(String code, boolean enabled);
    }
}
