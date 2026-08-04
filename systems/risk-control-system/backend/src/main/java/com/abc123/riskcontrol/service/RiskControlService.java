package com.abc123.riskcontrol.service;

import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.PageResultDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import com.abc123.riskcontrol.dto.RiskReviewActionRequestDTO;
import com.abc123.riskcontrol.dto.RiskSummaryDTO;
import com.abc123.riskcontrol.dto.ToggleRequestDTO;

/**
 * 风控服务。
 */
public interface RiskControlService {

    RiskSummaryDTO summary();

    PageResultDTO<RiskPolicyDTO> policies();

    PageResultDTO<LimitRuleDTO> limitRules();

    PageResultDTO<BlocklistDTO> blocklists();

    PageResultDTO<InterceptEventDTO> interceptEvents();

    PageResultDTO<ReviewOrderDTO> reviewOrders();

    PageResultDTO<MonitorRuleDTO> monitorRules();

    RiskSummaryDTO togglePolicy(ToggleRequestDTO request);

    RiskSummaryDTO toggleLimitRule(ToggleRequestDTO request);

    RiskSummaryDTO toggleBlocklist(ToggleRequestDTO request);

    RiskSummaryDTO toggleMonitorRule(ToggleRequestDTO request);

    PageResultDTO<ReviewOrderDTO> reviewAction(RiskReviewActionRequestDTO request);
}

