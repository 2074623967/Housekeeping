package com.abc123.riskcontrol.dao;

import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import java.util.List;

/**
 * 风控数据访问编排层。
 */
public interface RiskControlDao {

    List<RiskPolicyDTO> findPolicies();

    List<LimitRuleDTO> findLimitRules();

    List<BlocklistDTO> findBlocklists();

    List<InterceptEventDTO> findInterceptEvents();

    List<ReviewOrderDTO> findReviewOrders();

    List<MonitorRuleDTO> findMonitorRules();

    long countEnabledPolicies();

    long countEnabledLimits();

    long countPendingReviews();

    long countInterceptedEvents();

    int updatePolicyStatus(String policyCode, String status, String statusType);

    int updateLimitRuleStatus(String ruleCode, String status, String statusType);

    int updateBlocklistStatus(String blockCode, String status, String statusType);

    int updateMonitorRuleStatus(String monitorCode, String status, String statusType);

    int updateReviewOrder(String reviewNo, String status, String statusType, String reviewer);
}

