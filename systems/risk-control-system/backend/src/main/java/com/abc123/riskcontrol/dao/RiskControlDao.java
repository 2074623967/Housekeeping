package com.abc123.riskcontrol.dao;

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

    List<BlocklistEntity> findEnabledBlocklists();

    List<LimitRuleEntity> findEnabledLimitRulesByScene(String sceneCode);

    List<RiskPolicyEntity> findEnabledPoliciesForDecision();

    ReviewOrderEntity findReviewOrderByReviewNo(String reviewNo);

    ReviewOrderEntity findLatestReviewOrderByBusinessNo(String businessNo);

    InterceptEventEntity findLatestInterceptEvent(String paymentOrderId, String hitPolicy, String decisionResult);

    int insertInterceptEvent(InterceptEventEntity entity);

    int insertReviewOrder(ReviewOrderEntity entity);

    int updatePolicyStatus(String policyCode, String status, String statusType);

    int updateLimitRuleStatus(String ruleCode, String status, String statusType);

    int updateBlocklistStatus(String blockCode, String status, String statusType);

    int updateMonitorRuleStatus(String monitorCode, String status, String statusType);

    int updateReviewOrderWhenPending(String reviewNo, String status, String statusType, String reviewer);
}
