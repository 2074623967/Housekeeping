package com.abc123.riskcontrol.service;

import com.abc123.riskcontrol.dto.RiskOpsConfigSnapshotDTO;

/**
 * 风控系统读取运营配置快照服务。
 */
public interface RiskOpsConfigService {

    /**
     * 读取指定业务线、支付类型和终端的有效配置快照。
     *
     * @param businessCode 业务线编码
     * @param payType 支付类型编码
     * @param terminalType 终端类型
     * @return 生效快照，未启用或读取失败时返回空
     */
    RiskOpsConfigSnapshotDTO loadEffectiveSnapshot(String businessCode, String payType, String terminalType);
}
