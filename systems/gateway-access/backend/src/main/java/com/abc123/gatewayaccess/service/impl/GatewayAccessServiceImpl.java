package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.dto.DashboardMetricDTO;
import com.abc123.gatewayaccess.dto.GatewayAccessSummaryDTO;
import com.abc123.gatewayaccess.dto.GatewayAppDTO;
import com.abc123.gatewayaccess.dto.GatewayAuditLogDTO;
import com.abc123.gatewayaccess.dto.GatewayAuditQueryDTO;
import com.abc123.gatewayaccess.dto.GatewayCertificateDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelQueryDTO;
import com.abc123.gatewayaccess.dto.GatewayPermissionDTO;
import com.abc123.gatewayaccess.dto.GatewayReleaseRouteDTO;
import com.abc123.gatewayaccess.dto.GatewayReleaseRouteQueryDTO;
import com.abc123.gatewayaccess.dto.PageResultDTO;
import com.abc123.gatewayaccess.dto.ToggleRequestDTO;
import com.abc123.gatewayaccess.entity.GatewayAppEntity;
import com.abc123.gatewayaccess.entity.GatewayCertificateEntity;
import com.abc123.gatewayaccess.entity.GatewayChannelEntity;
import com.abc123.gatewayaccess.entity.GatewayPermissionEntity;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import com.abc123.gatewayaccess.service.GatewayAccessService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 网关接入服务默认实现。
 */
@Service
public class GatewayAccessServiceImpl implements GatewayAccessService {

    private final GatewayAccessMapper gatewayAccessMapper;

    public GatewayAccessServiceImpl(GatewayAccessMapper gatewayAccessMapper) {
        this.gatewayAccessMapper = gatewayAccessMapper;
    }

    @Override
    public GatewayAccessSummaryDTO summary() {
        GatewayAccessSummaryDTO summary = new GatewayAccessSummaryDTO();
        List<DashboardMetricDTO> metrics = new ArrayList<>();
        metrics.add(metric("接入应用", String.valueOf(gatewayAccessMapper.countApplications()), "info", "应用白名单"));
        metrics.add(metric("启用网关", String.valueOf(gatewayAccessMapper.countEnabledGateways()), "success", "通道在线"));
        metrics.add(metric("有效证书", String.valueOf(gatewayAccessMapper.countEnabledCertificates()), "warn", "30天内需关注"));
        metrics.add(metric("启用权限", String.valueOf(gatewayAccessMapper.countEnabledPermissions()), "danger", "权限治理"));
        summary.setMetrics(metrics);
        List<String> highlights = new ArrayList<>();
        highlights.add("渠道接入与证书轮换已纳入 gateway-access");
        highlights.add("payment-core 只保留支付主链路，不再承接接入治理台账");
        highlights.add("调用方审计台账已补齐，后续将继续补灰度发布和环境隔离");
        summary.setHighlights(highlights);
        return summary;
    }

    @Override
    public PageResultDTO<GatewayAppDTO> applications() {
        List<GatewayAppDTO> records = gatewayAccessMapper.findApplications();
        return page(records, 1, 20);
    }

    @Override
    public PageResultDTO<GatewayChannelDTO> gateways(GatewayChannelQueryDTO query) {
        List<GatewayChannelDTO> records = gatewayAccessMapper.findGateways(normalizeGatewayQuery(query));
        return page(records, 1, 20);
    }

    @Override
    public PageResultDTO<GatewayCertificateDTO> certificates(String riskLevel) {
        List<GatewayCertificateDTO> records = new ArrayList<>(gatewayAccessMapper.findCertificates());
        records.forEach(this::enrichCertificateRisk);
        if (StringUtils.hasText(riskLevel) && !"全部".equals(riskLevel.trim())) {
            String normalizedRiskLevel = riskLevel.trim();
            records.removeIf(item -> !normalizedRiskLevel.equals(item.getRiskLevel()));
        }
        return page(records, 1, 20);
    }

    @Override
    public PageResultDTO<GatewayPermissionDTO> permissions() {
        List<GatewayPermissionDTO> records = gatewayAccessMapper.findPermissions();
        return page(records, 1, 20);
    }

    @Override
    public PageResultDTO<GatewayAuditLogDTO> auditLogs(GatewayAuditQueryDTO query) {
        List<GatewayAuditLogDTO> records = gatewayAccessMapper.findAuditLogs(normalizeAuditQuery(query));
        return page(records, 1, 20);
    }

    @Override
    public PageResultDTO<GatewayReleaseRouteDTO> releaseRoutes(GatewayReleaseRouteQueryDTO query) {
        List<GatewayReleaseRouteDTO> records = gatewayAccessMapper.findReleaseRoutes(normalizeReleaseRouteQuery(query));
        return page(records, 1, 20);
    }

    @Override
    @Transactional
    public GatewayAccessSummaryDTO toggleApplication(ToggleRequestDTO request) {
        toggleApplicationRequest(request);
        return summary();
    }

    @Override
    @Transactional
    public GatewayAccessSummaryDTO toggleGateway(ToggleRequestDTO request) {
        toggleGatewayRequest(request);
        return summary();
    }

    @Override
    @Transactional
    public GatewayAccessSummaryDTO toggleCertificate(ToggleRequestDTO request) {
        toggleCertificateRequest(request);
        return summary();
    }

    @Override
    @Transactional
    public GatewayAccessSummaryDTO togglePermission(ToggleRequestDTO request) {
        togglePermissionRequest(request);
        return summary();
    }

    @Override
    @Transactional
    public GatewayAccessSummaryDTO toggleReleaseRoute(ToggleRequestDTO request) {
        toggleReleaseRouteRequest(request);
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

    private void toggleApplicationRequest(ToggleRequestDTO request) {
        String configCode = requireConfigCode(request, "接入应用");
        int affectedRows = gatewayAccessMapper.updateApplicationStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("接入应用不存在");
        }
    }

    private void toggleGatewayRequest(ToggleRequestDTO request) {
        String configCode = requireConfigCode(request, "网关");
        int affectedRows = gatewayAccessMapper.updateGatewayStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("网关不存在");
        }
    }

    private void toggleCertificateRequest(ToggleRequestDTO request) {
        String configCode = requireConfigCode(request, "证书");
        if (Boolean.TRUE.equals(request.getEnabled())) {
            GatewayCertificateEntity certificate = gatewayAccessMapper.findCertificateByCode(configCode);
            if (certificate == null) {
                throw new IllegalArgumentException("证书不存在");
            }
            validateCertificateCanBeEnabled(certificate);
        }
        int affectedRows = gatewayAccessMapper.updateCertificateStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("证书不存在");
        }
    }

    private <T> PageResultDTO<T> page(List<T> records, int pageNo, int pageSize) {
        return new PageResultDTO<>(records, records.size(), pageNo, pageSize);
    }

    /**
     * 基于证书到期日补齐剩余天数和风险分层，便于运营优先关注临期或已过期证书。
     */
    private void enrichCertificateRisk(GatewayCertificateDTO certificate) {
        if (certificate == null || !StringUtils.hasText(certificate.getExpireAt())) {
            return;
        }
        try {
            LocalDate expireDate = LocalDate.parse(certificate.getExpireAt().trim());
            long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), expireDate);
            certificate.setRemainingDays(remainingDays);
            if (remainingDays < 0) {
                certificate.setRiskLevel("已过期");
                certificate.setRiskLevelType("danger");
                certificate.setRiskHint("证书已过期，需立即更换并验证回调验签链路");
                return;
            }
            if (remainingDays <= 7) {
                certificate.setRiskLevel("7天内到期");
                certificate.setRiskLevelType("danger");
                certificate.setRiskHint("证书将在 7 天内到期，建议立即安排轮换");
                return;
            }
            if (remainingDays <= 30) {
                certificate.setRiskLevel("30天内到期");
                certificate.setRiskLevelType("warn");
                certificate.setRiskHint("证书进入临期窗口，需排入轮换计划并完成联调");
                return;
            }
            certificate.setRiskLevel("正常");
            certificate.setRiskLevelType("success");
            certificate.setRiskHint("证书有效期充足，可按常规节奏维护");
        } catch (DateTimeParseException exception) {
            certificate.setRiskLevel("日期异常");
            certificate.setRiskLevelType("warn");
            certificate.setRiskHint("证书到期日格式异常，请尽快核对台账");
        }
    }

    private GatewayChannelQueryDTO normalizeGatewayQuery(GatewayChannelQueryDTO query) {
        GatewayChannelQueryDTO normalizedQuery = query == null ? new GatewayChannelQueryDTO() : query;
        normalizedQuery.setKeyword(normalizedQuery.getKeyword() == null ? null : normalizedQuery.getKeyword().trim());
        normalizedQuery.setChannelType(normalizedQuery.getChannelType() == null ? "全部" : normalizedQuery.getChannelType().trim());
        normalizedQuery.setStatus(normalizedQuery.getStatus() == null ? "全部" : normalizedQuery.getStatus().trim());
        return normalizedQuery;
    }

    private GatewayAuditQueryDTO normalizeAuditQuery(GatewayAuditQueryDTO query) {
        GatewayAuditQueryDTO normalizedQuery = query == null ? new GatewayAuditQueryDTO() : query;
        normalizedQuery.setKeyword(normalizedQuery.getKeyword() == null ? null : normalizedQuery.getKeyword().trim());
        normalizedQuery.setAppCode(normalizedQuery.getAppCode() == null ? "全部" : normalizedQuery.getAppCode().trim());
        normalizedQuery.setResultStatus(normalizedQuery.getResultStatus() == null ? "全部" : normalizedQuery.getResultStatus().trim());
        return normalizedQuery;
    }

    private GatewayReleaseRouteQueryDTO normalizeReleaseRouteQuery(GatewayReleaseRouteQueryDTO query) {
        GatewayReleaseRouteQueryDTO normalizedQuery = query == null ? new GatewayReleaseRouteQueryDTO() : query;
        normalizedQuery.setEnvironment(normalizedQuery.getEnvironment() == null ? "全部" : normalizedQuery.getEnvironment().trim());
        normalizedQuery.setStatus(normalizedQuery.getStatus() == null ? "全部" : normalizedQuery.getStatus().trim());
        return normalizedQuery;
    }

    private void togglePermissionRequest(ToggleRequestDTO request) {
        String configCode = requireConfigCode(request, "权限");
        if (Boolean.TRUE.equals(request.getEnabled())) {
            GatewayPermissionEntity permission = gatewayAccessMapper.findPermissionByCode(configCode);
            if (permission == null) {
                throw new IllegalArgumentException("权限不存在");
            }
            validatePermissionCanBeEnabled(permission);
        }
        int affectedRows = gatewayAccessMapper.updatePermissionStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("权限不存在");
        }
    }

    private void toggleReleaseRouteRequest(ToggleRequestDTO request) {
        String configCode = requireConfigCode(request, "灰度路由");
        if (Boolean.TRUE.equals(request.getEnabled())) {
            GatewayReleaseRouteDTO route = gatewayAccessMapper.findReleaseRouteByCode(configCode);
            if (route == null) {
                throw new IllegalArgumentException("灰度路由不存在");
            }
            validateReleaseRouteCanBeEnabled(route);
        }
        int affectedRows = gatewayAccessMapper.updateReleaseRouteStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("灰度路由不存在");
        }
    }

    private String requireConfigCode(ToggleRequestDTO request, String label) {
        if (request == null || !StringUtils.hasText(request.getConfigCode())) {
            throw new IllegalArgumentException(label + "编码不能为空");
        }
        return request.getConfigCode().trim();
    }

    private String resolveStatus(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? "DISABLED" : "ENABLED";
    }

    private String resolveStatusType(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? "danger" : "success";
    }

    /**
     * 启用证书前校验证书未过期，且所归属网关处于启用状态。
     */
    private void validateCertificateCanBeEnabled(GatewayCertificateEntity certificate) {
        LocalDate expireDate = parseExpireDate(certificate.getExpireAt(), "证书到期日格式异常");
        if (expireDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("已过期证书不允许启用");
        }
        GatewayChannelEntity gateway = gatewayAccessMapper.findGatewayByCode(certificate.getGatewayCode());
        if (gateway == null) {
            throw new IllegalArgumentException("证书归属网关不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(gateway.getStatus())) {
            throw new IllegalArgumentException("证书归属网关未启用，不允许启用证书");
        }
    }

    /**
     * 启用权限前校验所属接入应用仍处于启用状态，避免停用应用恢复局部权限。
     */
    private void validatePermissionCanBeEnabled(GatewayPermissionEntity permission) {
        GatewayAppEntity application = gatewayAccessMapper.findApplicationByCode(permission.getAppCode());
        if (application == null) {
            throw new IllegalArgumentException("权限归属应用不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(application.getStatus())) {
            throw new IllegalArgumentException("权限归属应用未启用，不允许单独启用权限");
        }
    }

    /**
     * 启用灰度路由前校验关联网关已经启用，避免把路由流量切到停用渠道。
     */
    private void validateReleaseRouteCanBeEnabled(GatewayReleaseRouteDTO route) {
        GatewayChannelEntity gateway = gatewayAccessMapper.findGatewayByCode(route.getGatewayCode());
        if (gateway == null) {
            throw new IllegalArgumentException("灰度路由归属网关不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(gateway.getStatus())) {
            throw new IllegalArgumentException("灰度路由归属网关未启用，不允许启用路由");
        }
    }

    private LocalDate parseExpireDate(String expireAt, String errorMessage) {
        if (!StringUtils.hasText(expireAt)) {
            throw new IllegalArgumentException(errorMessage);
        }
        try {
            return LocalDate.parse(expireAt.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
