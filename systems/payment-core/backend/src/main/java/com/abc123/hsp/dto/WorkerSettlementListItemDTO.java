package com.abc123.hsp.dto;

public class WorkerSettlementListItemDTO {

    private String settlementOrderId;
    private String workerName;
    private String period;
    private String amountShouldSettle;
    private String deductAmount;
    private String amountNetSettle;
    private String depositImpactAmount;
    private String status;
    private String statusType;
    private String payoutStatus;
    private String payoutStatusType;

    public String getSettlementOrderId() {
        return settlementOrderId;
    }

    public void setSettlementOrderId(String settlementOrderId) {
        this.settlementOrderId = settlementOrderId;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getAmountShouldSettle() {
        return amountShouldSettle;
    }

    public void setAmountShouldSettle(String amountShouldSettle) {
        this.amountShouldSettle = amountShouldSettle;
    }

    public String getDeductAmount() {
        return deductAmount;
    }

    public void setDeductAmount(String deductAmount) {
        this.deductAmount = deductAmount;
    }

    public String getAmountNetSettle() {
        return amountNetSettle;
    }

    public void setAmountNetSettle(String amountNetSettle) {
        this.amountNetSettle = amountNetSettle;
    }

    public String getDepositImpactAmount() {
        return depositImpactAmount;
    }

    public void setDepositImpactAmount(String depositImpactAmount) {
        this.depositImpactAmount = depositImpactAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusType() {
        return statusType;
    }

    public void setStatusType(String statusType) {
        this.statusType = statusType;
    }

    public String getPayoutStatus() {
        return payoutStatus;
    }

    public void setPayoutStatus(String payoutStatus) {
        this.payoutStatus = payoutStatus;
    }

    public String getPayoutStatusType() {
        return payoutStatusType;
    }

    public void setPayoutStatusType(String payoutStatusType) {
        this.payoutStatusType = payoutStatusType;
    }
}
