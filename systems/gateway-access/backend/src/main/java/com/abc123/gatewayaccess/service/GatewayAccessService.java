package com.abc123.gatewayaccess.service;

import com.abc123.gatewayaccess.dto.GatewayAccessSummaryDTO;
import com.abc123.gatewayaccess.dto.GatewayAppDTO;
import com.abc123.gatewayaccess.dto.GatewayCertificateDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayPermissionDTO;
import com.abc123.gatewayaccess.dto.PageResultDTO;
import com.abc123.gatewayaccess.dto.ToggleRequestDTO;

/**
 * 网关接入服务。
 */
public interface GatewayAccessService {

    GatewayAccessSummaryDTO summary();

    PageResultDTO<GatewayAppDTO> applications();

    PageResultDTO<GatewayChannelDTO> gateways();

    PageResultDTO<GatewayCertificateDTO> certificates();

    PageResultDTO<GatewayPermissionDTO> permissions();

    GatewayAccessSummaryDTO toggleApplication(ToggleRequestDTO request);

    GatewayAccessSummaryDTO toggleGateway(ToggleRequestDTO request);

    GatewayAccessSummaryDTO toggleCertificate(ToggleRequestDTO request);

    GatewayAccessSummaryDTO togglePermission(ToggleRequestDTO request);
}
