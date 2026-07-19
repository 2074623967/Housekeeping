package com.abc123.hsp.dto;

public class PaymentRouteItemDTO {

    private String routeNo;
    private String channelCode;
    private String routeRule;
    private String routeResult;
    private String createdAt;

    public String getRouteNo() {
        return routeNo;
    }

    public void setRouteNo(String routeNo) {
        this.routeNo = routeNo;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getRouteRule() {
        return routeRule;
    }

    public void setRouteRule(String routeRule) {
        this.routeRule = routeRule;
    }

    public String getRouteResult() {
        return routeResult;
    }

    public void setRouteResult(String routeResult) {
        this.routeResult = routeResult;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
