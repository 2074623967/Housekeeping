package com.abc123.opsconfig.service.impl;

import com.abc123.opsconfig.common.BusinessException;
import com.abc123.opsconfig.dao.OpsConfigDao;
import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.dto.BusinessLineDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.ChannelProfileDTO;
import com.abc123.opsconfig.dto.DashboardMetricDTO;
import com.abc123.opsconfig.dto.OpsConfigEffectiveSnapshotDTO;
import com.abc123.opsconfig.dto.OpsConfigSnapshotQueryDTO;
import com.abc123.opsconfig.dto.OpsConfigSummaryDTO;
import com.abc123.opsconfig.dto.PageResultDTO;
import com.abc123.opsconfig.dto.PaymentTypeDTO;
import com.abc123.opsconfig.dto.RoutingRuleDTO;
import com.abc123.opsconfig.dto.SystemControlDTO;
import com.abc123.opsconfig.dto.ToggleRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeApprovalRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeCreateRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeDTO;
import com.abc123.opsconfig.dto.OpsConfigChangePublishRequestDTO;
import com.abc123.opsconfig.service.OpsConfigService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 运营配置业务编排实现。
 */
@Service
public class OpsConfigServiceImpl implements OpsConfigService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SUPPORTED_CONFIG_TYPES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("AGREEMENT_TEMPLATE", "BUSINESS_LINE", "PAYMENT_TYPE", "CASHIER_TEMPLATE",
                    "CHANNEL_PROFILE", "ROUTING_RULE", "SYSTEM_CONTROL")));

    private final OpsConfigDao dao;

    public OpsConfigServiceImpl(OpsConfigDao dao) {
        this.dao = dao;
    }

    @Override
    public OpsConfigSummaryDTO summary() {
        OpsConfigSummaryDTO summary = new OpsConfigSummaryDTO();
        summary.setMetrics(Arrays.asList(
                metric("启用协议模板", String.valueOf(dao.countEnabledAgreementTemplates()), "success", "协议"),
                metric("启用业务线", String.valueOf(dao.countEnabledBusinessLines()), "info", "业务"),
                metric("启用支付类型", String.valueOf(dao.countEnabledPaymentTypes()), "warn", "支付类型"),
                metric("启用渠道档案", String.valueOf(dao.countEnabledChannelProfiles()), "danger", "渠道")
        ));
        summary.setHighlights(Arrays.asList(
                "协议模板、业务线和支付类型由运营配置域统一维护，避免继续散落在 payment-core。",
                "渠道档案只维护业务台账；真实证书、密钥和接入权限仍由 gateway-access 负责。",
                "收银台模板和路由规则在 V1 先提供运营编排基线，后续接入真实路由引擎和灰度发布。"
        ));
        return summary;
    }

    @Override
    public PageResultDTO<AgreementTemplateDTO> agreementTemplates() {
        return page(dao.findAgreementTemplates());
    }

    @Override
    public PageResultDTO<BusinessLineDTO> businessLines() {
        return page(dao.findBusinessLines());
    }

    @Override
    public PageResultDTO<PaymentTypeDTO> paymentTypes() {
        return page(dao.findPaymentTypes());
    }

    @Override
    public PageResultDTO<CashierTemplateDTO> cashierTemplates() {
        return page(dao.findCashierTemplates());
    }

    @Override
    public PageResultDTO<ChannelProfileDTO> channelProfiles() {
        return page(dao.findChannelProfiles());
    }

    @Override
    public PageResultDTO<RoutingRuleDTO> routingRules() {
        return page(dao.findRoutingRules());
    }

    @Override
    public PageResultDTO<SystemControlDTO> systemControls() {
        return page(dao.findSystemControls());
    }

    @Override
    public OpsConfigEffectiveSnapshotDTO effectiveSnapshot(OpsConfigSnapshotQueryDTO query) {
        if (query == null || !StringUtils.hasText(query.getBusinessCode())) {
            throw new BusinessException("业务线编码不能为空");
        }
        if (!StringUtils.hasText(query.getPayType())) {
            throw new BusinessException("支付类型编码不能为空");
        }
        if (!StringUtils.hasText(query.getTerminalType())) {
            throw new BusinessException("终端类型不能为空");
        }
        String businessCode = query.getBusinessCode().trim();
        String payType = query.getPayType().trim();
        String terminalType = query.getTerminalType().trim();

        if (dao.findEnabledBusinessLineByCode(businessCode) == null) {
            throw new BusinessException("业务线未启用或不存在");
        }
        if (dao.findEnabledPaymentTypeByCode(payType) == null) {
            throw new BusinessException("支付类型未启用或不存在");
        }

        CashierTemplateDTO cashierTemplate = dao.findEnabledCashierTemplateByTerminal(terminalType);
        if (cashierTemplate == null) {
            throw new BusinessException("终端未配置启用中的收银台模板");
        }

        RoutingRuleDTO routingRule = dao.findEnabledRoutingRule(businessCode, payType);
        if (routingRule == null) {
            throw new BusinessException("业务线与支付类型未配置启用中的路由规则");
        }
        validateRouteChannelProfiles(routingRule);

        OpsConfigEffectiveSnapshotDTO snapshot = new OpsConfigEffectiveSnapshotDTO();
        snapshot.setBusinessCode(businessCode);
        snapshot.setPayType(payType);
        snapshot.setTerminalType(terminalType);
        snapshot.setDefaultPayMethod(cashierTemplate.getDefaultPayMethod());
        snapshot.setPrimaryChannelProfileCode(routingRule.getPrimaryChannel());
        snapshot.setBackupChannelProfileCode(routingRule.getBackupChannel());
        snapshot.setRouteMatchPolicy(routingRule.getMatchPolicy());
        snapshot.setEnabledSystemControls(dao.findEnabledSystemControls());
        return snapshot;
    }

    /**
     * 导出当前业务场景的有效配置快照，便于支付和风控留档。
     */
    @Override
    public byte[] exportEffectiveSnapshot(OpsConfigSnapshotQueryDTO query) {
        OpsConfigEffectiveSnapshotDTO snapshot = effectiveSnapshot(query);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("业务线编码,支付类型,终端类型,默认支付方式,优先渠道,备选渠道,路由策略\n");
        builder.append(csv(snapshot.getBusinessCode())).append(',')
                .append(csv(snapshot.getPayType())).append(',')
                .append(csv(snapshot.getTerminalType())).append(',')
                .append(csv(snapshot.getDefaultPayMethod())).append(',')
                .append(csv(snapshot.getPrimaryChannelProfileCode())).append(',')
                .append(csv(snapshot.getBackupChannelProfileCode())).append(',')
                .append(csv(snapshot.getRouteMatchPolicy())).append('\n');
        builder.append('\n');
        builder.append("系统控制项\n");
        builder.append("控制编码,控制名称,控制范围,控制值,风险级别,状态,更新时间\n");
        for (SystemControlDTO control : snapshot.getEnabledSystemControls()) {
            builder.append(csv(control.getControlCode())).append(',')
                    .append(csv(control.getControlName())).append(',')
                    .append(csv(control.getControlScope())).append(',')
                    .append(csv(control.getControlValue())).append(',')
                    .append(csv(control.getRiskLevel())).append(',')
                    .append(csv(control.getStatus())).append(',')
                    .append(csv(control.getUpdatedAt())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public OpsConfigSummaryDTO toggleAgreementTemplate(ToggleRequestDTO request) {
        if (dao.updateAgreementTemplateStatus(required(request, "协议模板"), status(request), statusType(request)) != 1) {
            throw new BusinessException("协议模板不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public OpsConfigSummaryDTO toggleBusinessLine(ToggleRequestDTO request) {
        if (dao.updateBusinessLineStatus(required(request, "业务线"), status(request), statusType(request)) != 1) {
            throw new BusinessException("业务线不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public OpsConfigSummaryDTO togglePaymentType(ToggleRequestDTO request) {
        if (dao.updatePaymentTypeStatus(required(request, "支付类型"), status(request), statusType(request)) != 1) {
            throw new BusinessException("支付类型不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public OpsConfigSummaryDTO toggleCashierTemplate(ToggleRequestDTO request) {
        if (dao.updateCashierTemplateStatus(required(request, "收银台模板"), status(request), statusType(request)) != 1) {
            throw new BusinessException("收银台模板不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public OpsConfigSummaryDTO toggleChannelProfile(ToggleRequestDTO request) {
        if (dao.updateChannelProfileStatus(required(request, "渠道档案"), status(request), statusType(request)) != 1) {
            throw new BusinessException("渠道档案不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public OpsConfigSummaryDTO toggleRoutingRule(ToggleRequestDTO request) {
        if (dao.updateRoutingRuleStatus(required(request, "路由规则"), status(request), statusType(request)) != 1) {
            throw new BusinessException("路由规则不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public OpsConfigSummaryDTO toggleSystemControl(ToggleRequestDTO request) {
        if (dao.updateSystemControlStatus(required(request, "系统控制"), status(request), statusType(request)) != 1) {
            throw new BusinessException("系统控制不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public OpsConfigChangeDTO createConfigChange(OpsConfigChangeCreateRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getConfigType())) {
            throw new BusinessException("配置类型不能为空");
        }
        String configType = request.getConfigType().trim().toUpperCase();
        if (!SUPPORTED_CONFIG_TYPES.contains(configType)) {
            throw new BusinessException("不支持的配置类型");
        }
        if (!StringUtils.hasText(request.getConfigCode())) {
            throw new BusinessException("配置编码不能为空");
        }
        if (request.getEnabled() == null) {
            throw new BusinessException("目标启停状态不能为空");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new BusinessException("变更原因不能为空");
        }
        if (!StringUtils.hasText(request.getApplicant())) {
            throw new BusinessException("申请人不能为空");
        }
        validateTimeWindow(request.getGrayStartAt(), request.getGrayEndAt());

        String requestNo = "OPSCHG-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String grayScope = StringUtils.hasText(request.getGrayScope()) ? request.getGrayScope().trim() : "ALL";
        int affected = dao.insertConfigChange(
                requestNo,
                configType,
                request.getConfigCode().trim(),
                status(request.getEnabled()),
                request.getReason().trim(),
                grayScope,
                trimToNull(request.getGrayStartAt()),
                trimToNull(request.getGrayEndAt()),
                request.getApplicant().trim());
        if (affected != 1) {
            throw new BusinessException("创建配置变更失败");
        }
        return changeOrFail(requestNo);
    }

    @Override
    public PageResultDTO<OpsConfigChangeDTO> configChanges() {
        List<OpsConfigChangeDTO> records = dao.findConfigChanges();
        return page(records);
    }

    @Override
    @Transactional
    public OpsConfigChangeDTO approveConfigChange(OpsConfigChangeApprovalRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRequestNo())) {
            throw new BusinessException("变更申请号不能为空");
        }
        if (request.getApproved() == null) {
            throw new BusinessException("审批结果不能为空");
        }
        if (!StringUtils.hasText(request.getApprover())) {
            throw new BusinessException("审批人不能为空");
        }
        OpsConfigChangeDTO change = changeOrFail(request.getRequestNo().trim());
        if (!"PENDING_APPROVAL".equals(change.getStatus())) {
            throw new BusinessException("当前变更不处于待审批状态");
        }
        String status = Boolean.TRUE.equals(request.getApproved()) ? "APPROVED" : "REJECTED";
        String statusType = Boolean.TRUE.equals(request.getApproved()) ? "success" : "danger";
        if (dao.approveConfigChange(change.getRequestNo(), status, statusType,
                request.getApprover().trim(), trimToNull(request.getApprovalComment())) != 1) {
            throw new BusinessException("配置变更审批失败");
        }
        return changeOrFail(change.getRequestNo());
    }

    @Override
    @Transactional
    public OpsConfigChangeDTO publishConfigChange(OpsConfigChangePublishRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRequestNo())) {
            throw new BusinessException("变更申请号不能为空");
        }
        if (!StringUtils.hasText(request.getOperator())) {
            throw new BusinessException("发布人不能为空");
        }
        OpsConfigChangeDTO change = changeOrFail(request.getRequestNo().trim());
        if (!"APPROVED".equals(change.getStatus())) {
            throw new BusinessException("只有审批通过的变更才能发布");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = parseTime(change.getGrayStartAt(), "灰度开始时间");
        LocalDateTime endAt = parseTime(change.getGrayEndAt(), "灰度结束时间");
        if (startAt != null && now.isBefore(startAt)) {
            throw new BusinessException("当前尚未到达灰度发布时间窗");
        }
        if (endAt != null && now.isAfter(endAt)) {
            dao.expireConfigChange(change.getRequestNo());
            throw new BusinessException("配置变更已超过灰度发布时间窗");
        }

        applyConfigChange(change);
        if (dao.publishConfigChange(change.getRequestNo()) != 1) {
            throw new BusinessException("配置变更发布失败");
        }
        return changeOrFail(change.getRequestNo());
    }

    private DashboardMetricDTO metric(String title, String value, String badgeType, String badgeText) {
        DashboardMetricDTO metric = new DashboardMetricDTO();
        metric.setTitle(title);
        metric.setValue(value);
        metric.setBadgeType(badgeType);
        metric.setBadgeText(badgeText);
        return metric;
    }

    private <T> PageResultDTO<T> page(List<T> records) {
        return new PageResultDTO<>(records, records.size(), 1, Math.max(records.size(), 1));
    }

    private String required(ToggleRequestDTO request, String label) {
        if (request == null || !StringUtils.hasText(request.getConfigCode())) {
            throw new BusinessException(label + "编码不能为空");
        }
        return request.getConfigCode().trim();
    }

    private String status(ToggleRequestDTO request) {
        return Boolean.TRUE.equals(request.getEnabled()) ? "ENABLED" : "DISABLED";
    }

    private String statusType(ToggleRequestDTO request) {
        return Boolean.TRUE.equals(request.getEnabled()) ? "success" : "danger";
    }

    private String status(Boolean enabled) {
        return Boolean.TRUE.equals(enabled) ? "ENABLED" : "DISABLED";
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    /**
     * 快照下发前校验路由依赖的渠道档案都已启用，避免 payment-core 拿到失效渠道配置。
     */
    private void validateRouteChannelProfiles(RoutingRuleDTO routingRule) {
        if (dao.findEnabledChannelProfileByCode(routingRule.getPrimaryChannel()) == null) {
            throw new BusinessException("主路由渠道档案未启用或不存在");
        }
        if (StringUtils.hasText(routingRule.getBackupChannel())
                && dao.findEnabledChannelProfileByCode(routingRule.getBackupChannel().trim()) == null) {
            throw new BusinessException("备选路由渠道档案未启用或不存在");
        }
    }

    private OpsConfigChangeDTO changeOrFail(String requestNo) {
        OpsConfigChangeDTO change = dao.findConfigChangeByRequestNo(requestNo);
        if (change == null) {
            throw new BusinessException("配置变更不存在");
        }
        return change;
    }

    private void applyConfigChange(OpsConfigChangeDTO change) {
        int affected;
        switch (change.getConfigType()) {
            case "AGREEMENT_TEMPLATE":
                affected = dao.updateAgreementTemplateStatus(change.getConfigCode(), change.getTargetStatus(),
                        statusType(change.getTargetStatus()));
                break;
            case "BUSINESS_LINE":
                affected = dao.updateBusinessLineStatus(change.getConfigCode(), change.getTargetStatus(),
                        statusType(change.getTargetStatus()));
                break;
            case "PAYMENT_TYPE":
                affected = dao.updatePaymentTypeStatus(change.getConfigCode(), change.getTargetStatus(),
                        statusType(change.getTargetStatus()));
                break;
            case "CASHIER_TEMPLATE":
                affected = dao.updateCashierTemplateStatus(change.getConfigCode(), change.getTargetStatus(),
                        statusType(change.getTargetStatus()));
                break;
            case "CHANNEL_PROFILE":
                affected = dao.updateChannelProfileStatus(change.getConfigCode(), change.getTargetStatus(),
                        statusType(change.getTargetStatus()));
                break;
            case "ROUTING_RULE":
                affected = dao.updateRoutingRuleStatus(change.getConfigCode(), change.getTargetStatus(),
                        statusType(change.getTargetStatus()));
                break;
            case "SYSTEM_CONTROL":
                affected = dao.updateSystemControlStatus(change.getConfigCode(), change.getTargetStatus(),
                        statusType(change.getTargetStatus()));
                break;
            default:
                throw new BusinessException("不支持的配置类型");
        }
        if (affected != 1) {
            throw new BusinessException("目标配置不存在");
        }
    }

    private String statusType(String targetStatus) {
        return "ENABLED".equals(targetStatus) ? "success" : "danger";
    }

    private void validateTimeWindow(String grayStartAt, String grayEndAt) {
        LocalDateTime startAt = parseTime(grayStartAt, "灰度开始时间");
        LocalDateTime endAt = parseTime(grayEndAt, "灰度结束时间");
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new BusinessException("灰度结束时间不能早于开始时间");
        }
    }

    private LocalDateTime parseTime(String value, String label) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (RuntimeException exception) {
            throw new BusinessException(label + "格式必须为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
