package com.abc123.hsp.dto;

public class PaymentSubmitRequestDTO {

    private String prepayOrderNo;
    private String paymentMethod;
    private String channelCode;

    public String getPrepayOrderNo() {
        return prepayOrderNo;
    }

    public void setPrepayOrderNo(String prepayOrderNo) {
        this.prepayOrderNo = prepayOrderNo;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }
}
