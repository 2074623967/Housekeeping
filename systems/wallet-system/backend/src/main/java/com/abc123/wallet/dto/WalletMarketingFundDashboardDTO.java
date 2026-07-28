package com.abc123.wallet.dto;

import java.util.List;
import lombok.Data;

/** 营销资金台账看板。 */
@Data
public class WalletMarketingFundDashboardDTO {
    /** 营销资金账户号。 */
    private String accountNo;
    /** 账户名称。 */
    private String ownerName;
    /** 可用余额。 */
    private String availableAmount;
    /** 冻结余额。 */
    private String frozenAmount;
    /** 红包累计申请金额。 */
    private String totalRedPacketAmount;
    /** 待审批红包金额。 */
    private String pendingApprovalAmount;
    /** 已发放红包金额。 */
    private String issuedAmount;
    /** 已驳回红包金额。 */
    private String rejectedAmount;
    /** 待审事件数。 */
    private Integer pendingRiskCount;
    /** 已通过事件数。 */
    private Integer approvedRiskCount;
    /** 已驳回事件数。 */
    private Integer rejectedRiskCount;
    /** 营销资金支出台账。 */
    private List<WalletLedgerDTO> outLedgers;
    /** 红包批次台账。 */
    private List<WalletRedPacketDTO> redPackets;
    /** 风控审批台账。 */
    private List<WalletRiskEventDTO> riskEvents;
}
