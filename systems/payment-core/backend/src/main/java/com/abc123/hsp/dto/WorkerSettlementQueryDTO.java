package com.abc123.hsp.dto;

/**
 * 服务者结算单列表查询条件。
 */
public class WorkerSettlementQueryDTO {

    private String settlementOrderId;
    private String workerKeyword;
    private String settlementStatus;
    private String payoutStatus;
    private int pageNo = 1;
    private int pageSize = 20;

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

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getOffset() {
        return (Math.max(pageNo, 1) - 1) * Math.min(Math.max(pageSize, 1), 100);
    }

    public int getLimit() {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}
