package com.abc123.hsp.entity;

import lombok.Data;

/** 异常告警值班路由配置变更复核实体。 */
@Data
public class PaymentIssueDutyRosterReviewEntity {

    private String requestNo;
    private String rosterCode;
    private String operationType;
    private String issueType;
    private String severity;
    private String responsibilityGroup;
    private String receiver;
    private String notifyChannels;
    private String escalationLevel;
    private String escalationReceiver;
    private String escalationPolicy;
    private Integer escalationTimeoutMinutes;
    private String scheduleTag;
    private Integer effectiveStartHour;
    private Integer effectiveEndHour;
    private String weekdayScope;
    private String holidayStrategy;
    private String pendingRosterCode;
    private String baseFingerprint;
    private String candidateStatus;
    private String candidateStatusType;
    private String requester;
    private String requestReason;
    private String reviewStatus;
    private String reviewStatusType;
    private String reviewer;
    private String reviewComment;
}
