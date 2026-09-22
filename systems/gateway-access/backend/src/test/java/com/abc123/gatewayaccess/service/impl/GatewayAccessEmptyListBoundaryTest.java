package com.abc123.gatewayaccess.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import org.junit.jupiter.api.Test;

/**
 * 网关接入列表空结果边界测试。
 */
class GatewayAccessEmptyListBoundaryTest {

    @Test
    void shouldNormalizeNullMapperListsToEmptyPagesAndExports() {
        GatewayAccessMapper mapper = mock(GatewayAccessMapper.class);
        when(mapper.findApplications()).thenReturn(null);
        when(mapper.findGateways(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(mapper.findCertificates()).thenReturn(null);
        when(mapper.findPermissions()).thenReturn(null);
        when(mapper.findAuditLogs(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(mapper.findReleaseRoutes(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        GatewayAccessServiceImpl service = new GatewayAccessServiceImpl(mapper);

        assertTrue(service.applications().getRecords().isEmpty());
        assertTrue(service.gateways(null).getRecords().isEmpty());
        assertTrue(service.certificates(null).getRecords().isEmpty());
        assertTrue(service.permissions().getRecords().isEmpty());
        assertTrue(service.auditLogs(null).getRecords().isEmpty());
        assertTrue(service.releaseRoutes(null).getRecords().isEmpty());
        assertArrayEquals("\uFEFF请求流水号,应用编码,网关编码,操作类型,签名算法,客户端IP,结果状态,风险提示,发生时间\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                service.exportAuditLogs(null));
    }
}
