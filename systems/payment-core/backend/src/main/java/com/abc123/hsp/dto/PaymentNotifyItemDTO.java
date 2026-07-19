package com.abc123.hsp.dto;

public class PaymentNotifyItemDTO {

    private String notifyNo;
    private String channelCode;
    private String notifyType;
    private String notifyStatus;
    private String notifyStatusType;
    private String createdAt;

    public String getNotifyNo() {
        return notifyNo;
    }

    public void setNotifyNo(String notifyNo) {
        this.notifyNo = notifyNo;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getNotifyType() {
        return notifyType;
    }

    public void setNotifyType(String notifyType) {
        this.notifyType = notifyType;
    }

    public String getNotifyStatus() {
        return notifyStatus;
    }

    public void setNotifyStatus(String notifyStatus) {
        this.notifyStatus = notifyStatus;
    }

    public String getNotifyStatusType() {
        return notifyStatusType;
    }

    public void setNotifyStatusType(String notifyStatusType) {
        this.notifyStatusType = notifyStatusType;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
