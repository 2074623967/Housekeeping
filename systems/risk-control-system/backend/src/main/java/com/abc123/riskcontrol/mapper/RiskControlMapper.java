package com.abc123.riskcontrol.mapper;

import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import com.abc123.riskcontrol.entity.BlocklistEntity;
import com.abc123.riskcontrol.entity.InterceptEventEntity;
import com.abc123.riskcontrol.entity.LimitRuleEntity;
import com.abc123.riskcontrol.entity.ReviewOrderEntity;
import com.abc123.riskcontrol.entity.RiskPolicyEntity;
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

    List<BlocklistEntity> findEnabledBlocklists();

    List<LimitRuleEntity> findEnabledLimitRulesByScene(@Param("sceneCode") String sceneCode);

    List<RiskPolicyEntity> findEnabledPoliciesForDecision();

    ReviewOrderEntity findReviewOrderByReviewNo(@Param("reviewNo") String reviewNo);

    ReviewOrderEntity findLatestReviewOrderByBusinessNo(@Param("businessNo") String businessNo);

    InterceptEventEntity findLatestInterceptEvent(@Param("paymentOrderId") String paymentOrderId,
                                                  @Param("hitPolicy") String hitPolicy,
                                                  @Param("decisionResult") String decisionResult);

    int insertInterceptEvent(InterceptEventEntity entity);

    int insertReviewOrder(ReviewOrderEntity entity);

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

    int updateReviewOrderWhenPending(@Param("reviewNo") String reviewNo,
                                     @Param("status") String status,
                                     @Param("statusType") String statusType,
                                     @Param("reviewer") String reviewer);
}
