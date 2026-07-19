package com.abc123.hsp.dto;

public class OrderListItemDTO {

    private String orderNo;
    private String customerName;
    private String serviceType;
    private String workerName;
    private String orderAmount;
    private String paidAmount;
    private String orderStatus;
    private String orderStatusType;
    private String fulfillmentStatus;
    private String fulfillmentStatusType;
    private String createdAt;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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
}
