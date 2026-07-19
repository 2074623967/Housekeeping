package com.abc123.hsp.dto;

/**
 * 服务者结算单列表查询条件。
 */
public class WorkerSettlementQueryDTO {

    private String settlementOrderId;
    private String workerKeyword;
    private String settlementStatus;
    private String payoutStatus;

    public String getSettlementOrderId() {
        return settlementOrderId;
    }

    public void setSettlementOrderId(String settlementOrderId) {
        this.settlementOrderId = settlementOrderId;
    }

    public String getWorkerKeyword() {
        return workerKeyword;
    }

    public void setWorkerKeyword(String workerKeyword) {
        this.workerKeyword = workerKeyword;
    }

    public String getSettlementStatus() {
        return settlementStatus;
    }

    public void setSettlementStatus(String settlementStatus) {
        this.settlementStatus = settlementStatus;
    }

    public String getPayoutStatus() {
        return payoutStatus;
    }

    public void setPayoutStatus(String payoutStatus) {
        this.payoutStatus = payoutStatus;
    }
}
