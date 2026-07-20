package com.abc123.hsp.dto;

import java.util.List;

public class PaymentDetailDTO {

    private String paymentOrderId;
    private String prepayOrderNo;
    private String billNo;
    private String orderNo;
    private String customerName;
    private String amount;
    private String paymentMethod;
    private String channel;
    private String channelTransactionNo;
    private String latestTerminal;
    private String latestClientIp;
    private String latestIdempotencyKey;
    private String latestAttemptStatus;
    private String latestAttemptStatusType;
    private String latestRequestPayload;
    private String latestResponsePayload;
    private String querySource;
    private String status;
    private String statusType;
    private String createdAt;
    private List<String> routeLogs;
    private List<String> notifyLogs;
    private List<String> eventLogs;

    public String getPaymentOrderId() {
        return paymentOrderId;
    }

    public void setPaymentOrderId(String paymentOrderId) {
        this.paymentOrderId = paymentOrderId;
    }

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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getChannelTransactionNo() {
        return channelTransactionNo;
    }

    public void setChannelTransactionNo(String channelTransactionNo) {
        this.channelTransactionNo = channelTransactionNo;
    }

    public String getLatestTerminal() {
        return latestTerminal;
    }

    public void setLatestTerminal(String latestTerminal) {
        this.latestTerminal = latestTerminal;
    }

    public String getLatestClientIp() {
        return latestClientIp;
    }

    public void setLatestClientIp(String latestClientIp) {
        this.latestClientIp = latestClientIp;
    }

    public String getLatestIdempotencyKey() {
        return latestIdempotencyKey;
    }

    public void setLatestIdempotencyKey(String latestIdempotencyKey) {
        this.latestIdempotencyKey = latestIdempotencyKey;
    }

    public String getLatestAttemptStatus() {
        return latestAttemptStatus;
    }

    public void setLatestAttemptStatus(String latestAttemptStatus) {
        this.latestAttemptStatus = latestAttemptStatus;
    }

    public String getLatestAttemptStatusType() {
        return latestAttemptStatusType;
    }

    public void setLatestAttemptStatusType(String latestAttemptStatusType) {
        this.latestAttemptStatusType = latestAttemptStatusType;
    }

    public String getLatestRequestPayload() {
        return latestRequestPayload;
    }

    public void setLatestRequestPayload(String latestRequestPayload) {
        this.latestRequestPayload = latestRequestPayload;
    }

    public String getLatestResponsePayload() {
        return latestResponsePayload;
    }

    public void setLatestResponsePayload(String latestResponsePayload) {
        this.latestResponsePayload = latestResponsePayload;
    }

    public String getQuerySource() {
        return querySource;
    }

    public void setQuerySource(String querySource) {
        this.querySource = querySource;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getNotifyLogs() {
        return notifyLogs;
    }

    public void setNotifyLogs(List<String> notifyLogs) {
        this.notifyLogs = notifyLogs;
    }

    public List<String> getRouteLogs() {
        return routeLogs;
    }

    public void setRouteLogs(List<String> routeLogs) {
        this.routeLogs = routeLogs;
    }

    public List<String> getEventLogs() {
        return eventLogs;
    }

    public void setEventLogs(List<String> eventLogs) {
        this.eventLogs = eventLogs;
    }
}
