package com.abc123.opsconfig.mapper;

import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.dto.BusinessLineDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.ChannelProfileDTO;
import com.abc123.opsconfig.dto.PaymentTypeDTO;
import com.abc123.opsconfig.dto.RoutingRuleDTO;
import com.abc123.opsconfig.dto.SystemControlDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 运营配置 Mapper。
 */
public interface OpsConfigMapper {

    List<AgreementTemplateDTO> findAgreementTemplates();

    List<BusinessLineDTO> findBusinessLines();

    List<PaymentTypeDTO> findPaymentTypes();

    List<CashierTemplateDTO> findCashierTemplates();

    List<ChannelProfileDTO> findChannelProfiles();

    List<RoutingRuleDTO> findRoutingRules();

    List<SystemControlDTO> findSystemControls();

    long countEnabledAgreementTemplates();

    long countEnabledBusinessLines();

    long countEnabledPaymentTypes();

    long countEnabledChannelProfiles();

    int updateAgreementTemplateStatus(@Param("templateCode") String templateCode,
                                      @Param("status") String status,
                                      @Param("statusType") String statusType);

    int updateBusinessLineStatus(@Param("businessCode") String businessCode,
                                 @Param("status") String status,
                                 @Param("statusType") String statusType);

    int updatePaymentTypeStatus(@Param("typeCode") String typeCode,
                                @Param("status") String status,
                                @Param("statusType") String statusType);

    int updateCashierTemplateStatus(@Param("templateCode") String templateCode,
                                    @Param("status") String status,
                                    @Param("statusType") String statusType);

    int updateChannelProfileStatus(@Param("channelCode") String channelCode,
                                   @Param("status") String status,
                                   @Param("statusType") String statusType);

    int updateRoutingRuleStatus(@Param("routeCode") String routeCode,
                                @Param("status") String status,
                                @Param("statusType") String statusType);

    int updateSystemControlStatus(@Param("controlCode") String controlCode,
                                  @Param("status") String status,
                                  @Param("statusType") String statusType);
}
