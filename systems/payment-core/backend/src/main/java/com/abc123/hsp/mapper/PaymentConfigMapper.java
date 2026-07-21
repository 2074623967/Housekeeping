package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentProtocolConfigDTO;
import com.abc123.hsp.dto.PaymentProtocolTypeOptionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRuntimeDTO;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
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
     * 按来源应用标识查询支付控制策略配置。
     */
    PaymentControlPolicyDTO findControlPolicyBySourceAppId(@Param("sourceAppId") String sourceAppId);

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
     * 更新路由规则启停状态。
     */
    int updateRouteRuleStatus(@Param("ruleCode") String ruleCode,
                              @Param("status") String status,
                              @Param("statusType") String statusType);

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
     * 更新支付控制策略自检结果。
     */
    int updateControlPolicySelfCheck(@Param("sourceAppId") String sourceAppId,
                                     @Param("selfCheckStatus") String selfCheckStatus,
                                     @Param("selfCheckStatusType") String selfCheckStatusType,
                                     @Param("selfCheckMessage") String selfCheckMessage);
}
