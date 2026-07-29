package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 开立账户请求。
 */
@Data
public class OpenAccountRequestDTO {

    private String subjectId;
    private String accountType;
    private String currency;
}
