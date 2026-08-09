package com.abc123.opsconfig.mapper;

import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.dto.BusinessLineDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.ChannelProfileDTO;
import com.abc123.opsconfig.dto.PaymentTypeDTO;
import com.abc123.opsconfig.dto.RoutingRuleDTO;
import com.abc123.opsconfig.dto.SystemControlDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeDTO;
import com.abc123.opsconfig.dto.OpsConfigRefreshEventDTO;
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

    BusinessLineDTO findEnabledBusinessLineByCode(@Param("businessCode") String businessCode);

    PaymentTypeDTO findEnabledPaymentTypeByCode(@Param("typeCode") String typeCode);

    CashierTemplateDTO findEnabledCashierTemplateByTerminal(@Param("terminalType") String terminalType);

    RoutingRuleDTO findEnabledRoutingRule(@Param("businessCode") String businessCode,
                                          @Param("payType") String payType);

    ChannelProfileDTO findEnabledChannelProfileByCode(@Param("channelCode") String channelCode);

    List<SystemControlDTO> findEnabledSystemControls();

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

    List<OpsConfigChangeDTO> findConfigChanges();

    OpsConfigChangeDTO findConfigChangeByRequestNo(@Param("requestNo") String requestNo);

    int insertConfigChange(@Param("requestNo") String requestNo,
                           @Param("configType") String configType,
                           @Param("configCode") String configCode,
                           @Param("targetStatus") String targetStatus,
                           @Param("reason") String reason,
                           @Param("grayScope") String grayScope,
                           @Param("grayStartAt") String grayStartAt,
                           @Param("grayEndAt") String grayEndAt,
                           @Param("applicant") String applicant);

    int approveConfigChange(@Param("requestNo") String requestNo,
                            @Param("status") String status,
                            @Param("statusType") String statusType,
                            @Param("approver") String approver,
                            @Param("approvalComment") String approvalComment);

    int publishConfigChange(@Param("requestNo") String requestNo);

    int expireConfigChange(@Param("requestNo") String requestNo);

    int insertSnapshotVersion(@Param("grayScope") String grayScope,
                              @Param("sourceRequestNo") String sourceRequestNo);

    Long findSnapshotVersionByRequestNo(@Param("sourceRequestNo") String sourceRequestNo);

    Long findLatestSnapshotVersion();

    int insertRefreshEvent(@Param("eventNo") String eventNo,
                           @Param("snapshotVersion") Long snapshotVersion,
                           @Param("grayScope") String grayScope,
                           @Param("sourceRequestNo") String sourceRequestNo);

    List<OpsConfigRefreshEventDTO> findRefreshEvents();

    int acknowledgeRefreshEvent(@Param("eventNo") String eventNo,
                                @Param("acknowledgedBy") String acknowledgedBy);
}
