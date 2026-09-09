package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentChannelReviewDTO;
import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO;
import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckItemDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterDTO;
import com.abc123.hsp.dto.PaymentProtocolConfigDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewDTO;
import com.abc123.hsp.dto.PaymentProtocolTypeOptionDTO;
import com.abc123.hsp.dto.PaymentProtocolVersionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRuntimeDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewDTO;
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentGatewayConfigEntity;
import com.abc123.hsp.entity.PaymentGatewayReviewEntity;
import com.abc123.hsp.entity.PaymentChannelConfigEntity;
import com.abc123.hsp.entity.PaymentChannelReviewEntity;
import com.abc123.hsp.entity.PaymentControlPolicyEntity;
import com.abc123.hsp.entity.PaymentControlPolicyReviewEntity;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.entity.PaymentProtocolReviewEntity;
import com.abc123.hsp.entity.PaymentProtocolVersionEntity;
import com.abc123.hsp.entity.PaymentRouteRuleConfigEntity;
import com.abc123.hsp.entity.PaymentRouteRuleReviewEntity;
import com.abc123.hsp.entity.PaymentRouteRuleVersionEntity;
import com.abc123.hsp.entity.PaymentReturnCodeImportReviewEntity;
import com.abc123.hsp.entity.PaymentReturnCodeReviewEntity;
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

    /** 按渠道编码加锁查询完整运行配置。 */
    PaymentChannelConfigEntity findChannelByCodeForUpdate(@Param("channelCode") String channelCode);

    /** 新增支付渠道运行配置。 */
    int insertChannel(PaymentChannelConfigEntity entity);

    /** 更新支付渠道完整运行配置。 */
    int updateChannel(PaymentChannelConfigEntity entity);

    /** 查询支付渠道状态变更复核申请。 */
    List<PaymentChannelReviewDTO> findChannelReviews();

    /** 加锁查询支付渠道状态变更复核申请。 */
    PaymentChannelReviewEntity findChannelReviewForUpdate(@Param("requestNo") String requestNo);

    /** 按唯一占位加锁查询渠道待复核申请。 */
    PaymentChannelReviewEntity findPendingChannelReviewForUpdate(
            @Param("pendingChannelCode") String pendingChannelCode);

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
     * 加锁查询渠道返回码映射，供批量导入基线复验。
     */
    List<PaymentChannelReturnCodeConfigDTO> findReturnCodeMappingsForUpdate();

    /** 加锁查询单条渠道返回码映射。 */
    PaymentChannelReturnCodeConfigDTO findReturnCodeMappingForUpdate(
            @Param("channelCode") String channelCode,
            @Param("channelReturnCode") String channelReturnCode);

    /** 查询渠道返回码单条变更复核申请。 */
    List<PaymentReturnCodeReviewDTO> findReturnCodeReviews();

    /** 加锁查询渠道返回码单条变更复核申请。 */
    PaymentReturnCodeReviewEntity findReturnCodeReviewForUpdate(@Param("requestNo") String requestNo);

    /** 按唯一占位加锁查询待复核申请。 */
    PaymentReturnCodeReviewEntity findPendingReturnCodeReviewForUpdate(
            @Param("pendingMappingKey") String pendingMappingKey);

    /** 查询渠道返回码导入复核申请。 */
    List<PaymentReturnCodeImportReviewDTO> findReturnCodeImportReviews();

    /** 加锁查询渠道返回码导入复核申请。 */
    PaymentReturnCodeImportReviewEntity findReturnCodeImportReviewForUpdate(
            @Param("requestNo") String requestNo);

    /** 加锁查询当前待复核的渠道返回码导入申请。 */
    PaymentReturnCodeImportReviewEntity findPendingReturnCodeImportReviewForUpdate();

    /**
     * 查询启用且未归档的渠道返回码映射。
     */
    PaymentChannelReturnCodeConfigDTO findActiveReturnCodeMapping(
            @Param("channelCode") String channelCode,
            @Param("channelReturnCode") String channelReturnCode);

    /**
     * 查询支付网关接入配置。
     */
    List<PaymentGatewayConfigDTO> findGateways();

    /** 按网关编码加锁查询完整运行配置。 */
    PaymentGatewayConfigEntity findGatewayByCodeForUpdate(@Param("gatewayCode") String gatewayCode);

    /** 新增支付网关完整运行配置。 */
    int insertGateway(PaymentGatewayConfigEntity entity);

    /** 更新支付网关完整运行配置。 */
    int updateGateway(PaymentGatewayConfigEntity entity);

    /** 查询支付网关配置变更复核申请。 */
    List<PaymentGatewayReviewDTO> findGatewayReviews();

    /** 加锁查询支付网关配置变更复核申请。 */
    PaymentGatewayReviewEntity findGatewayReviewForUpdate(@Param("requestNo") String requestNo);

    /** 按唯一占位加锁查询网关待复核申请。 */
    PaymentGatewayReviewEntity findPendingGatewayReviewForUpdate(
            @Param("pendingGatewayCode") String pendingGatewayCode);

    /**
     * 查询支付控制策略配置。
     */
    List<PaymentControlPolicyDTO> findControlPolicies();

    /** 按来源应用标识加锁查询完整支付控制策略。 */
    PaymentControlPolicyEntity findControlPolicyBySourceAppIdForUpdate(
            @Param("sourceAppId") String sourceAppId);

    /** 查询支付控制策略配置变更复核申请。 */
    List<PaymentControlPolicyReviewDTO> findControlPolicyReviews();

    /** 加锁查询支付控制策略配置变更复核申请。 */
    PaymentControlPolicyReviewEntity findControlPolicyReviewForUpdate(
            @Param("requestNo") String requestNo);

    /** 按唯一占位加锁查询支付控制策略待复核申请。 */
    PaymentControlPolicyReviewEntity findPendingControlPolicyReviewForUpdate(
            @Param("pendingSourceAppId") String pendingSourceAppId);

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

    /** 新增支付渠道状态变更复核申请。 */
    int insertChannelReview(PaymentChannelReviewEntity entity);

    /** 完成支付渠道状态变更复核并释放唯一待办占位。 */
    int completeChannelReview(@Param("requestNo") String requestNo,
                              @Param("reviewStatus") String reviewStatus,
                              @Param("reviewStatusType") String reviewStatusType,
                              @Param("reviewer") String reviewer,
                              @Param("reviewComment") String reviewComment);

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
     * 查询支付协议复核申请。
     */
    List<PaymentProtocolReviewDTO> findProtocolReviews();

    /**
     * 加锁查询支付协议复核申请。
     */
    PaymentProtocolReviewEntity findProtocolReviewForUpdate(@Param("requestNo") String requestNo);

    /**
     * 统计协议待复核申请数。
     */
    int countPendingProtocolReviews(@Param("protocolCode") String protocolCode);

    /**
     * 新增支付协议不可变版本快照。
     */
    int insertProtocolVersion(PaymentProtocolVersionEntity entity);

    /**
     * 新增支付协议复核申请。
     */
    int insertProtocolReview(PaymentProtocolReviewEntity entity);

    /**
     * 完成支付协议复核申请。
     */
    int completeProtocolReview(@Param("requestNo") String requestNo,
                               @Param("reviewStatus") String reviewStatus,
                               @Param("reviewStatusType") String reviewStatusType,
                               @Param("reviewer") String reviewer,
                               @Param("reviewComment") String reviewComment);

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

    /** 新增渠道返回码单条变更复核申请。 */
    int insertReturnCodeReview(PaymentReturnCodeReviewEntity entity);

    /** 完成渠道返回码单条变更复核并释放唯一待办占位。 */
    int completeReturnCodeReview(@Param("requestNo") String requestNo,
                                 @Param("reviewStatus") String reviewStatus,
                                 @Param("reviewStatusType") String reviewStatusType,
                                 @Param("reviewer") String reviewer,
                                 @Param("reviewComment") String reviewComment);

    /**
     * 新增或更新启用中的渠道返回码映射；归档记录由服务层阻断。
     */
    int upsertReturnCodeMapping(PaymentChannelReturnCodeConfigDTO mapping);

    /**
     * 保存返回码批量导入审计批次。
     */
    int insertReturnCodeImportBatch(
            @Param("batchNo") String batchNo,
            @Param("operator") String operator,
            @Param("changeReason") String changeReason,
            @Param("sourceSha256") String sourceSha256,
            @Param("baselineFingerprint") String baselineFingerprint,
            @Param("resultFingerprint") String resultFingerprint,
            @Param("totalCount") Integer totalCount,
            @Param("createCount") Integer createCount,
            @Param("updateCount") Integer updateCount,
            @Param("unchangedCount") Integer unchangedCount,
            @Param("appliedCount") Integer appliedCount,
            @Param("batchStatus") String batchStatus,
            @Param("batchStatusType") String batchStatusType);

    /** 新增渠道返回码导入复核申请。 */
    int insertReturnCodeImportReview(PaymentReturnCodeImportReviewEntity entity);

    /** 完成渠道返回码导入复核申请并释放唯一待办占位。 */
    int completeReturnCodeImportReview(@Param("requestNo") String requestNo,
                                       @Param("reviewStatus") String reviewStatus,
                                       @Param("reviewStatusType") String reviewStatusType,
                                       @Param("reviewer") String reviewer,
                                       @Param("reviewComment") String reviewComment,
                                       @Param("appliedBatchNo") String appliedBatchNo);

    /** 新增支付网关配置变更复核申请。 */
    int insertGatewayReview(PaymentGatewayReviewEntity entity);

    /** 完成支付网关配置变更复核申请并释放唯一待办占位。 */
    int completeGatewayReview(@Param("requestNo") String requestNo,
                              @Param("reviewStatus") String reviewStatus,
                              @Param("reviewStatusType") String reviewStatusType,
                              @Param("reviewer") String reviewer,
                              @Param("reviewComment") String reviewComment);

    /** 新增支付控制策略完整运行配置。 */
    int insertControlPolicy(PaymentControlPolicyEntity entity);

    /** 更新支付控制策略完整运行配置。 */
    int updateControlPolicy(PaymentControlPolicyEntity entity);

    /** 新增支付控制策略配置变更复核申请。 */
    int insertControlPolicyReview(PaymentControlPolicyReviewEntity entity);

    /** 完成支付控制策略配置变更复核申请并释放唯一待办占位。 */
    int completeControlPolicyReview(@Param("requestNo") String requestNo,
                                    @Param("reviewStatus") String reviewStatus,
                                    @Param("reviewStatusType") String reviewStatusType,
                                    @Param("reviewer") String reviewer,
                                    @Param("reviewComment") String reviewComment);

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
