package com.abc123.riskcontrol.dto;

import java.util.List;
import lombok.Data;

/**
 * 风控系统消费的运营配置快照。
 */
@Data
public class RiskOpsConfigSnapshotDTO {

    /** 业务线编码。 */
    private String businessCode;
    /** 支付类型编码。 */
    private String payType;
    /** 终端类型。 */
    private String terminalType;
    /** 默认支付方式。 */
    private String defaultPayMethod;
    /** 优先渠道档案编码。 */
    private String primaryChannelProfileCode;
    /** 备选渠道档案编码。 */
    private String backupChannelProfileCode;
    /** 路由命中策略。 */
    private String routeMatchPolicy;
    /** 已启用系统控制项。 */
    private List<RiskOpsSystemControlDTO> enabledSystemControls;
}
