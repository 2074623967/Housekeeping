package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.dto.GatewayAppDTO;
import com.abc123.gatewayaccess.dto.GatewayCertificateDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayPermissionDTO;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关接入内存数据源。
 */
public class GatewayAccessMemoryStore {

    private final List<GatewayAppDTO> apps = new ArrayList<>();
    private final List<GatewayChannelDTO> gateways = new ArrayList<>();
    private final List<GatewayCertificateDTO> certificates = new ArrayList<>();
    private final List<GatewayPermissionDTO> permissions = new ArrayList<>();

    public GatewayAccessMemoryStore() {
        apps.addAll(GatewayAccessSeedData.apps());
        gateways.addAll(GatewayAccessSeedData.gateways());
        certificates.addAll(GatewayAccessSeedData.certificates());
        permissions.addAll(GatewayAccessSeedData.permissions());
    }

    public List<GatewayAppDTO> apps() {
        return new ArrayList<>(apps);
    }

    public List<GatewayChannelDTO> gateways() {
        return new ArrayList<>(gateways);
    }

    public List<GatewayCertificateDTO> certificates() {
        return new ArrayList<>(certificates);
    }

    public List<GatewayPermissionDTO> permissions() {
        return new ArrayList<>(permissions);
    }

    public boolean toggleApp(String code, boolean enabled) {
        for (GatewayAppDTO app : apps) {
            if (app.getAppCode().equals(code)) {
                app.setStatus(enabled ? "ENABLED" : "DISABLED");
                app.setStatusType(enabled ? "success" : "danger");
                app.setUpdatedAt(GatewayAccessSeedData.now());
                return true;
            }
        }
        return false;
    }

    public boolean toggleGateway(String code, boolean enabled) {
        for (GatewayChannelDTO gateway : gateways) {
            if (gateway.getGatewayCode().equals(code)) {
                gateway.setStatus(enabled ? "ENABLED" : "DISABLED");
                gateway.setStatusType(enabled ? "success" : "danger");
                gateway.setUpdatedAt(GatewayAccessSeedData.now());
                return true;
            }
        }
        return false;
    }

    public boolean toggleCertificate(String code, boolean enabled) {
        for (GatewayCertificateDTO certificate : certificates) {
            if (certificate.getCertificateCode().equals(code)) {
                certificate.setStatus(enabled ? "ENABLED" : "DISABLED");
                certificate.setStatusType(enabled ? "success" : "danger");
                certificate.setUpdatedAt(GatewayAccessSeedData.now());
                return true;
            }
        }
        return false;
    }

    public boolean togglePermission(String code, boolean enabled) {
        for (GatewayPermissionDTO permission : permissions) {
            if (permission.getPermissionCode().equals(code)) {
                permission.setStatus(enabled ? "ENABLED" : "DISABLED");
                permission.setStatusType(enabled ? "success" : "danger");
                permission.setUpdatedAt(GatewayAccessSeedData.now());
                return true;
            }
        }
        return false;
    }
}
