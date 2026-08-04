package com.abc123.riskcontrol.mapper;

import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 风控系统 Mapper。
 */
public interface RiskControlMapper {

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

    int updatePolicyStatus(@Param("policyCode") String policyCode,
                           @Param("status") String status,
                           @Param("statusType") String statusType);

    int updateLimitRuleStatus(@Param("ruleCode") String ruleCode,
                              @Param("status") String status,
                              @Param("statusType") String statusType);

    int updateBlocklistStatus(@Param("blockCode") String blockCode,
                              @Param("status") String status,
                              @Param("statusType") String statusType);

    int updateMonitorRuleStatus(@Param("monitorCode") String monitorCode,
                                @Param("status") String status,
                                @Param("statusType") String statusType);

    int updateReviewOrder(@Param("reviewNo") String reviewNo,
                          @Param("status") String status,
                          @Param("statusType") String statusType,
                          @Param("reviewer") String reviewer);
}

