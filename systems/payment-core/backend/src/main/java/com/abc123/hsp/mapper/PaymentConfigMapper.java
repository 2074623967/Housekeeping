package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConfigDTO;
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
}
