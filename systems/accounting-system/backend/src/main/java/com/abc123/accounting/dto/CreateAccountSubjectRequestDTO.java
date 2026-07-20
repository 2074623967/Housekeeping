package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 创建账户主体请求。
 */
@Data
public class CreateAccountSubjectRequestDTO {

    private String subjectType;
    private String subjectName;
    private String ownerName;
}
