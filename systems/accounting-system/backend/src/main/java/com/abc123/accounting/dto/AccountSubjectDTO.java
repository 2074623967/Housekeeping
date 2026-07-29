package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 账户主体展示对象。
 */
@Data
public class AccountSubjectDTO {

    private String subjectId;
    private String subjectType;
    private String subjectName;
    private String ownerName;
    private String status;
    private String statusType;
    private String linkedAccountCount;
    private String createdAt;
}
