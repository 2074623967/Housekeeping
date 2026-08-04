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
import com.abc123.opsconfig.service.OpsConfigService;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 运营配置业务编排实现。
 */
@Service
public class OpsConfigServiceImpl implements OpsConfigService {

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
        CashierTemplateDTO cashierTemplate = dao.findEnabledCashierTemplateByTerminal(query.getTerminalType().trim());
        RoutingRuleDTO routingRule = dao.findEnabledRoutingRule(query.getBusinessCode().trim(), query.getPayType().trim());
        OpsConfigEffectiveSnapshotDTO snapshot = new OpsConfigEffectiveSnapshotDTO();
        snapshot.setBusinessCode(query.getBusinessCode().trim());
        snapshot.setPayType(query.getPayType().trim());
        snapshot.setTerminalType(query.getTerminalType().trim());
        if (cashierTemplate != null) {
            snapshot.setDefaultPayMethod(cashierTemplate.getDefaultPayMethod());
        }
        if (routingRule != null) {
            snapshot.setPrimaryChannelProfileCode(routingRule.getPrimaryChannel());
            snapshot.setBackupChannelProfileCode(routingRule.getBackupChannel());
            snapshot.setRouteMatchPolicy(routingRule.getMatchPolicy());
        }
        snapshot.setEnabledSystemControls(dao.findEnabledSystemControls());
        return snapshot;
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
}
