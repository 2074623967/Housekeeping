package com.abc123.hsp.dto;

public class OrderListItemDTO {

    private String orderNo;
    private String billNo;
    private String customerName;
    private String serviceType;
    private String workerName;
    private String orderAmount;
    private String paidAmount;
    private String orderStatus;
    private String orderStatusType;
    private String fulfillmentStatus;
    private String fulfillmentStatusType;
    private String latestPaymentOrderId;
    private String latestPaymentStatus;
    private String latestPaymentStatusType;
    private String latestPrepayOrderNo;
    private String latestCashierStatus;
    private String latestCashierStatusType;
    private String createdAt;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public String getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(String orderAmount) {
        this.orderAmount = orderAmount;
    }

    public String getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(String paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getOrderStatusType() {
        return orderStatusType;
    }

    public void setOrderStatusType(String orderStatusType) {
        this.orderStatusType = orderStatusType;
    }

    public String getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    public void setFulfillmentStatus(String fulfillmentStatus) {
        this.fulfillmentStatus = fulfillmentStatus;
    }

    public String getFulfillmentStatusType() {
        return fulfillmentStatusType;
    }

    public void setFulfillmentStatusType(String fulfillmentStatusType) {
        this.fulfillmentStatusType = fulfillmentStatusType;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLatestPaymentOrderId() {
        return latestPaymentOrderId;
    }

    public void setLatestPaymentOrderId(String latestPaymentOrderId) {
        this.latestPaymentOrderId = latestPaymentOrderId;
    }

    public String getLatestPaymentStatus() {
        return latestPaymentStatus;
    }

    public void setLatestPaymentStatus(String latestPaymentStatus) {
        this.latestPaymentStatus = latestPaymentStatus;
    }

    public String getLatestPaymentStatusType() {
        return latestPaymentStatusType;
    }

    public void setLatestPaymentStatusType(String latestPaymentStatusType) {
        this.latestPaymentStatusType = latestPaymentStatusType;
    }

    public String getLatestPrepayOrderNo() {
        return latestPrepayOrderNo;
    }

    public void setLatestPrepayOrderNo(String latestPrepayOrderNo) {
        this.latestPrepayOrderNo = latestPrepayOrderNo;
    }

    public String getLatestCashierStatus() {
        return latestCashierStatus;
    }

    public void setLatestCashierStatus(String latestCashierStatus) {
        this.latestCashierStatus = latestCashierStatus;
    }

    public String getLatestCashierStatusType() {
        return latestCashierStatusType;
    }

    public void setLatestCashierStatusType(String latestCashierStatusType) {
        this.latestCashierStatusType = latestCashierStatusType;
    }
}
