package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentOpsConfigSnapshotDTO;

/**
 * 支付核心到运营配置域的快照读取服务。
 */
public interface PaymentOpsConfigService {

    /**
     * 读取当前业务场景下已经发布生效的运营配置快照。
     *
     * @param businessCode 业务线编码
     * @param payType 支付类型编码
     * @param terminalType 终端类型
     * @return 运营配置快照，未启用或调用失败时返回空
     */
    PaymentOpsConfigSnapshotDTO loadEffectiveSnapshot(String businessCode, String payType, String terminalType);
}
