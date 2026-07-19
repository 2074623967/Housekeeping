package com.abc123.hsp.dto;

/**
 * 支付收款记录原型行模型。
 *
 * <p>字段按支付系统原型的收款管理列表定义，避免后台页面只展示支付单号和金额
 * 这类过度简化的信息。</p>
 */
public class PaymentRecordRowDTO {

    private Long serialNo;
    private String paymentOrderId;
    private String businessOrderNo;
    private String paymentRequestNo;
    private String applicationSourceId;
    private String businessLineId;
    private String externalTransactionNo;
    private String paymentGateway;
    private String paymentChannel;
    private String paymentType;
    private String paymentStatus;
    private String bankName;
    private String cardNo;
    private String channelReturnCode;
    private String returnParameterType;
    private String parameterValue;
    private String validityPeriod;
    private String paymentAmount;
    private Integer refundCount;
    private String refundedAmount;
    private String productName;
    private String userPaymentChannelId;
    private String receivingAccount;
    private String notifyUrl;
    private String callbackMqTopic;
    private String expireTime;
    private String createdAt;
    private String updatedAt;
    private String paidAt;
    private String userId;
    private String statusType;

    public Long getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(Long serialNo) {
        this.serialNo = serialNo;
    }

    public String getPaymentOrderId() {
        return paymentOrderId;
    }

    public void setPaymentOrderId(String paymentOrderId) {
        this.paymentOrderId = paymentOrderId;
    }

    public String getBusinessOrderNo() {
        return businessOrderNo;
    }

    public void setBusinessOrderNo(String businessOrderNo) {
        this.businessOrderNo = businessOrderNo;
    }

    public String getPaymentRequestNo() {
        return paymentRequestNo;
    }

    public void setPaymentRequestNo(String paymentRequestNo) {
        this.paymentRequestNo = paymentRequestNo;
    }

    public String getApplicationSourceId() {
        return applicationSourceId;
    }

    public void setApplicationSourceId(String applicationSourceId) {
        this.applicationSourceId = applicationSourceId;
    }

    public String getBusinessLineId() {
        return businessLineId;
    }

    public void setBusinessLineId(String businessLineId) {
        this.businessLineId = businessLineId;
    }

    public String getExternalTransactionNo() {
        return externalTransactionNo;
    }

    public void setExternalTransactionNo(String externalTransactionNo) {
        this.externalTransactionNo = externalTransactionNo;
    }

    public String getPaymentGateway() {
        return paymentGateway;
    }

    public void setPaymentGateway(String paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getChannelReturnCode() {
        return channelReturnCode;
    }

    public void setChannelReturnCode(String channelReturnCode) {
        this.channelReturnCode = channelReturnCode;
    }

    public String getReturnParameterType() {
        return returnParameterType;
    }

    public void setReturnParameterType(String returnParameterType) {
        this.returnParameterType = returnParameterType;
    }

    public String getParameterValue() {
        return parameterValue;
    }

    public void setParameterValue(String parameterValue) {
        this.parameterValue = parameterValue;
    }

    public String getValidityPeriod() {
        return validityPeriod;
    }

    public void setValidityPeriod(String validityPeriod) {
        this.validityPeriod = validityPeriod;
    }

    public String getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(String paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public Integer getRefundCount() {
        return refundCount;
    }

    public void setRefundCount(Integer refundCount) {
        this.refundCount = refundCount;
    }

    public String getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(String refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getUserPaymentChannelId() {
        return userPaymentChannelId;
    }

    public void setUserPaymentChannelId(String userPaymentChannelId) {
        this.userPaymentChannelId = userPaymentChannelId;
    }

    public String getReceivingAccount() {
        return receivingAccount;
    }

    public void setReceivingAccount(String receivingAccount) {
        this.receivingAccount = receivingAccount;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getCallbackMqTopic() {
        return callbackMqTopic;
    }

    public void setCallbackMqTopic(String callbackMqTopic) {
        this.callbackMqTopic = callbackMqTopic;
    }

    public String getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(String paidAt) {
        this.paidAt = paidAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatusType() {
        return statusType;
    }

    public void setStatusType(String statusType) {
        this.statusType = statusType;
    }
}
