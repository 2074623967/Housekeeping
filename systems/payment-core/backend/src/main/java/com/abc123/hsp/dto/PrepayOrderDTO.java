package com.abc123.hsp.dto;

public class PrepayOrderDTO {

    private String prepayOrderNo;
    private String billNo;
    private String orderNo;
    private String customerName;
    private String amount;
    private String payScene;
    private String cashierTitle;
    private String cashierStatus;
    private String cashierStatusType;
    private String paymentOrderId;
    private String createdAt;
    private String expiresAt;

    public String getPrepayOrderNo() {
        return prepayOrderNo;
    }

    public void setPrepayOrderNo(String prepayOrderNo) {
        this.prepayOrderNo = prepayOrderNo;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

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

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getPayScene() {
        return payScene;
    }

    public void setPayScene(String payScene) {
        this.payScene = payScene;
    }

    public String getCashierTitle() {
        return cashierTitle;
    }

    public void setCashierTitle(String cashierTitle) {
        this.cashierTitle = cashierTitle;
    }

    public String getCashierStatus() {
        return cashierStatus;
    }

    public void setCashierStatus(String cashierStatus) {
        this.cashierStatus = cashierStatus;
    }

    public String getCashierStatusType() {
        return cashierStatusType;
    }

    public void setCashierStatusType(String cashierStatusType) {
        this.cashierStatusType = cashierStatusType;
    }

    public String getPaymentOrderId() {
        return paymentOrderId;
    }

    public void setPaymentOrderId(String paymentOrderId) {
        this.paymentOrderId = paymentOrderId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }
}
