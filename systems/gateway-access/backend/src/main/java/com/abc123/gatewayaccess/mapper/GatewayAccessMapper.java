package com.abc123.gatewayaccess.mapper;

import com.abc123.gatewayaccess.dto.GatewayAppDTO;
import com.abc123.gatewayaccess.dto.GatewayCertificateDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayPermissionDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 网关接入 Mapper。
 */
public interface GatewayAccessMapper {

    /**
     * 查询接入应用列表。
     */
    List<GatewayAppDTO> findApplications();

    /**
     * 查询网关渠道列表。
     */
    List<GatewayChannelDTO> findGateways();

    /**
     * 查询证书列表。
     */
    List<GatewayCertificateDTO> findCertificates();

    /**
     * 查询接入权限列表。
     */
    List<GatewayPermissionDTO> findPermissions();

    /**
     * 统计接入应用数。
     */
    long countApplications();

    /**
     * 统计启用网关数。
     */
    long countEnabledGateways();

    /**
     * 统计启用证书数。
     */
    long countEnabledCertificates();

    /**
     * 统计启用权限数。
     */
    long countEnabledPermissions();

    /**
     * 更新接入应用状态。
     */
    int updateApplicationStatus(@Param("appCode") String appCode,
                                @Param("status") String status,
                                @Param("statusType") String statusType);

    /**
     * 更新网关状态。
     */
    int updateGatewayStatus(@Param("gatewayCode") String gatewayCode,
                            @Param("status") String status,
                            @Param("statusType") String statusType);

    /**
     * 更新证书状态。
     */
    int updateCertificateStatus(@Param("certificateCode") String certificateCode,
                                @Param("status") String status,
                                @Param("statusType") String statusType);

    /**
     * 更新权限状态。
     */
    int updatePermissionStatus(@Param("permissionCode") String permissionCode,
                               @Param("status") String status,
                               @Param("statusType") String statusType);
}
