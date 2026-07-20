package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO;
import com.abc123.hsp.dto.PaymentProtocolConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRuntimeDTO;
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
     * 查询已启用的渠道配置，供支付路由执行使用。
     */
    List<PaymentChannelRoutingConfigDTO> findEnabledChannelsForRouting();

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
}
