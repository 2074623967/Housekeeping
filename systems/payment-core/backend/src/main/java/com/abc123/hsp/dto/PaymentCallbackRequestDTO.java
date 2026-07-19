package com.abc123.hsp.dto;

public class PaymentCallbackRequestDTO {

    private String paymentOrderId;
    private String channelTransactionNo;
    private String tradeStatus;

    public String getPaymentOrderId() {
        return paymentOrderId;
    }

    public void setPaymentOrderId(String paymentOrderId) {
        this.paymentOrderId = paymentOrderId;
    }

    public String getChannelTransactionNo() {
        return channelTransactionNo;
    }

    public void setChannelTransactionNo(String channelTransactionNo) {
        this.channelTransactionNo = channelTransactionNo;
    }

    public String getTradeStatus() {
        return tradeStatus;
    }

    public void setTradeStatus(String tradeStatus) {
        this.tradeStatus = tradeStatus;
    }
}
