package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO;
import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckItemDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterDTO;
import com.abc123.hsp.dto.PaymentProtocolConfigDTO;
import com.abc123.hsp.dto.PaymentProtocolTypeOptionDTO;
import com.abc123.hsp.dto.PaymentProtocolVersionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRuntimeDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.entity.PaymentProtocolVersionEntity;
import com.abc123.hsp.entity.PaymentRouteRuleConfigEntity;
import com.abc123.hsp.entity.PaymentRouteRuleReviewEntity;
import com.abc123.hsp.entity.PaymentRouteRuleVersionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付配置 Mapper，负责渠道和路由规则配置读写。
 */
public interface PaymentConfigMapper {

    /**
     * 查询支付渠道配置。
     */
    List<PaymentChannelConfigDTO> findChannels();

    /**
     * 查询支付路由规则配置。
     */
    List<PaymentRouteRuleConfigDTO> findRouteRules();

    /**
     * 按规则编码查询支付路由规则。
     */
    PaymentRouteRuleConfigEntity findRouteRuleByCode(@Param("ruleCode") String ruleCode);

    /**
     * 按规则编码加锁查询支付路由规则。
     */
    PaymentRouteRuleConfigEntity findRouteRuleByCodeForUpdate(@Param("ruleCode") String ruleCode);

    /**
     * 查询支付路由规则历史版本。
     */
    List<PaymentRouteRuleVersionDTO> findRouteRuleVersions(@Param("ruleCode") String ruleCode);

    /**
     * 查询支付路由规则指定历史版本。
     */
    PaymentRouteRuleVersionEntity findRouteRuleVersion(@Param("ruleCode") String ruleCode,
                                                       @Param("versionNo") Integer versionNo);

    /**
     * 查询支付路由规则当前最大版本号。
     */
    Integer findLatestRouteRuleVersionNo(@Param("ruleCode") String ruleCode);

    /**
     * 查询路由规则复核申请。
     */
    List<PaymentRouteRuleReviewDTO> findRouteRuleReviews();

    /**
     * 加锁查询路由规则复核申请。
     */
    PaymentRouteRuleReviewEntity findRouteRuleReviewForUpdate(@Param("requestNo") String requestNo);

    /**
     * 统计规则待复核申请数。
     */
    int countPendingRouteRuleReviews(@Param("ruleCode") String ruleCode);

    /**
     * 查询支付协议配置。
     */
    List<PaymentProtocolConfigDTO> findProtocols();

    /**
     * 查询支付协议类型字典。
     */
    List<PaymentProtocolTypeOptionDTO> findProtocolTypeOptions();

    /**
     * 按协议编码查询支付协议配置。
     */
    PaymentProtocolConfigEntity findProtocolByCode(@Param("protocolCode") String protocolCode);

    /**
     * 查询渠道返回码映射配置。
     */
    List<PaymentChannelReturnCodeConfigDTO> findReturnCodeMappings();

    /**
     * 查询支付网关接入配置。
     */
    List<PaymentGatewayConfigDTO> findGateways();

    /**
     * 查询支付控制策略配置。
     */
    List<PaymentControlPolicyDTO> findControlPolicies();

    /**
     * 查询告警通知供应商配置。
     */
    List<PaymentAlertProviderConfigDTO> findAlertProviders();

    /**
     * 查询异常告警值班路由配置。
     */
    List<PaymentIssueDutyRosterDTO> findIssueDutyRosters();

    /**
     * 按路由编码查询异常告警值班路由配置。
     */
    PaymentIssueDutyRosterEntity findIssueDutyRosterByCode(@Param("rosterCode") String rosterCode);

    /**
     * 按来源应用标识查询支付控制策略配置。
     */
    PaymentControlPolicyDTO findControlPolicyBySourceAppId(@Param("sourceAppId") String sourceAppId);

    /**
     * 查询启用中的支付控制策略自检批次。
     */
    List<PaymentControlPolicySelfCheckItemDTO> findEnabledControlPolicySelfCheckItems();

    /**
     * 查询已启用的渠道配置，供支付路由执行使用。
     */
    List<PaymentChannelRoutingConfigDTO> findEnabledChannelsForRouting();

    /**
     * 按渠道编码查询路由执行所需配置。
     */
    PaymentChannelRoutingConfigDTO findChannelForRouting(@Param("channelCode") String channelCode);

    /**
     * 查询已启用的路由规则，供支付路由执行使用。
     */
    List<PaymentRouteRuleRuntimeDTO> findEnabledRouteRulesForRouting();

    /**
     * 更新渠道启停状态。
     */
    int updateChannelStatus(@Param("channelCode") String channelCode,
                            @Param("status") String status,
                            @Param("statusType") String statusType);

    /**
     * 新增支付路由规则。
     */
    int insertRouteRule(PaymentRouteRuleConfigEntity entity);

    /**
     * 更新支付路由规则。
     */
    int updateRouteRule(PaymentRouteRuleConfigEntity entity);

    /**
     * 新增支付路由规则不可变版本快照。
     */
    int insertRouteRuleVersion(PaymentRouteRuleVersionEntity entity);

    /**
     * 新增路由规则复核申请。
     */
    int insertRouteRuleReview(PaymentRouteRuleReviewEntity entity);

    /**
     * 完成路由规则复核申请。
     */
    int completeRouteRuleReview(@Param("requestNo") String requestNo,
                                @Param("reviewStatus") String reviewStatus,
                                @Param("reviewStatusType") String reviewStatusType,
                                @Param("reviewer") String reviewer,
                                @Param("reviewComment") String reviewComment);

    /**
     * 更新支付协议启停状态。
     */
    int updateProtocolStatus(@Param("protocolCode") String protocolCode,
                             @Param("status") String status,
                             @Param("statusType") String statusType);

    /**
     * 新增支付协议配置。
     */
    int insertProtocol(PaymentProtocolConfigEntity entity);

    /**
     * 更新支付协议配置。
     */
    int updateProtocol(PaymentProtocolConfigEntity entity);

    /**
     * 按协议编码加锁查询支付协议。
     */
    PaymentProtocolConfigEntity findProtocolByCodeForUpdate(@Param("protocolCode") String protocolCode);

    /**
     * 查询支付协议历史版本。
     */
    List<PaymentProtocolVersionDTO> findProtocolVersions(@Param("protocolCode") String protocolCode);

    /**
     * 查询支付协议指定历史版本。
     */
    PaymentProtocolVersionEntity findProtocolVersion(@Param("protocolCode") String protocolCode,
                                                      @Param("versionNo") Integer versionNo);

    /**
     * 查询支付协议当前最大版本号。
     */
    Integer findLatestProtocolVersionNo(@Param("protocolCode") String protocolCode);

    /**
     * 新增支付协议不可变版本快照。
     */
    int insertProtocolVersion(PaymentProtocolVersionEntity entity);

    /**
     * 新增异常告警值班路由配置。
     */
    int insertIssueDutyRoster(PaymentIssueDutyRosterEntity entity);

    /**
     * 更新异常告警值班路由配置。
     */
    int updateIssueDutyRoster(PaymentIssueDutyRosterEntity entity);

    /**
     * 更新渠道返回码映射启停状态。
     */
    int updateReturnCodeMappingStatus(@Param("channelCode") String channelCode,
                                      @Param("channelReturnCode") String channelReturnCode,
                                      @Param("status") String status,
                                      @Param("statusType") String statusType);

    /**
     * 更新支付网关启停状态。
     */
    int updateGatewayStatus(@Param("gatewayCode") String gatewayCode,
                            @Param("status") String status,
                            @Param("statusType") String statusType);

    /**
     * 更新支付控制策略启停状态。
     */
    int updateControlPolicyStatus(@Param("sourceAppId") String sourceAppId,
                                  @Param("status") String status,
                                  @Param("statusType") String statusType);

    /**
     * 更新告警通知供应商配置启停状态。
     */
    int updateAlertProviderStatus(@Param("providerCode") String providerCode,
                                  @Param("status") String status,
                                  @Param("statusType") String statusType);

    /**
     * 更新支付控制策略自检结果。
     */
    int updateControlPolicySelfCheck(@Param("sourceAppId") String sourceAppId,
                                     @Param("selfCheckStatus") String selfCheckStatus,
                                     @Param("selfCheckStatusType") String selfCheckStatusType,
                                     @Param("selfCheckMessage") String selfCheckMessage);

    /**
     * 更新异常告警值班路由启停状态。
     */
    int updateIssueDutyRosterStatus(@Param("rosterCode") String rosterCode,
                                    @Param("status") String status,
                                    @Param("statusType") String statusType);
}
