package com.abc123.riskcontrol.service.impl;

import com.abc123.riskcontrol.common.BusinessException;
import com.abc123.riskcontrol.dao.RiskControlDao;
import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.DashboardMetricDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.PageResultDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import com.abc123.riskcontrol.dto.RiskReviewActionRequestDTO;
import com.abc123.riskcontrol.dto.RiskSummaryDTO;
import com.abc123.riskcontrol.dto.ToggleRequestDTO;
import com.abc123.riskcontrol.service.RiskControlService;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 风控业务编排实现。
 */
@Service
public class RiskControlServiceImpl implements RiskControlService {

    private final RiskControlDao dao;

    public RiskControlServiceImpl(RiskControlDao dao) {
        this.dao = dao;
    }

    @Override
    public RiskSummaryDTO summary() {
        RiskSummaryDTO summary = new RiskSummaryDTO();
        summary.setMetrics(Arrays.asList(
                metric("启用风险策略", String.valueOf(dao.countEnabledPolicies()), "danger", "策略"),
                metric("启用限额规则", String.valueOf(dao.countEnabledLimits()), "warn", "限额"),
                metric("待审复核单", String.valueOf(dao.countPendingReviews()), "info", "复核"),
                metric("累计拦截事件", String.valueOf(dao.countInterceptedEvents()), "success", "拦截")
        ));
        summary.setHighlights(Arrays.asList(
                "风险策略负责高危支付、设备异常、身份异常等基础拦截策略。",
                "限额规则与运营配置域分工：配置域维护业务参数，风控域执行场景限额和拦截。",
                "人工复核单承接高风险交易的人工判定，后续接 payment-core 与 settlement-system。"
        ));
        return summary;
    }

    @Override
    public PageResultDTO<RiskPolicyDTO> policies() {
        return page(dao.findPolicies());
    }

    @Override
    public PageResultDTO<LimitRuleDTO> limitRules() {
        return page(dao.findLimitRules());
    }

    @Override
    public PageResultDTO<BlocklistDTO> blocklists() {
        return page(dao.findBlocklists());
    }

    @Override
    public PageResultDTO<InterceptEventDTO> interceptEvents() {
        return page(dao.findInterceptEvents());
    }

    @Override
    public PageResultDTO<ReviewOrderDTO> reviewOrders() {
        return page(dao.findReviewOrders());
    }

    @Override
    public PageResultDTO<MonitorRuleDTO> monitorRules() {
        return page(dao.findMonitorRules());
    }

    @Override
    @Transactional
    public RiskSummaryDTO togglePolicy(ToggleRequestDTO request) {
        if (dao.updatePolicyStatus(requiredCode(request, "风险策略"), status(request), statusType(request)) != 1) {
            throw new BusinessException("风险策略不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public RiskSummaryDTO toggleLimitRule(ToggleRequestDTO request) {
        if (dao.updateLimitRuleStatus(requiredCode(request, "限额规则"), status(request), statusType(request)) != 1) {
            throw new BusinessException("限额规则不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public RiskSummaryDTO toggleBlocklist(ToggleRequestDTO request) {
        if (dao.updateBlocklistStatus(requiredCode(request, "黑名单"), status(request), statusType(request)) != 1) {
            throw new BusinessException("黑名单不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public RiskSummaryDTO toggleMonitorRule(ToggleRequestDTO request) {
        if (dao.updateMonitorRuleStatus(requiredCode(request, "监控规则"), status(request), statusType(request)) != 1) {
            throw new BusinessException("监控规则不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public PageResultDTO<ReviewOrderDTO> reviewAction(RiskReviewActionRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getReviewNo())) {
            throw new BusinessException("复核单号不能为空");
        }
        if (!StringUtils.hasText(request.getAction())) {
            throw new BusinessException("审核动作不能为空");
        }
        String action = request.getAction().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("审核动作仅支持 APPROVE 或 REJECT");
        }
        String status = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
        String statusType = "APPROVE".equals(action) ? "success" : "danger";
        if (dao.updateReviewOrder(request.getReviewNo().trim(), status, statusType, "风险审核员A") != 1) {
            throw new BusinessException("复核单不存在");
        }
        return reviewOrders();
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

    private String requiredCode(ToggleRequestDTO request, String label) {
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

