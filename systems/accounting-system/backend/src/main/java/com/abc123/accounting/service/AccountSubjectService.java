package com.abc123.accounting.service;

import com.abc123.accounting.dto.AccountSubjectDTO;
import com.abc123.accounting.dto.CreateAccountSubjectRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;

/**
 * 账户主体服务。
 */
public interface AccountSubjectService {

    PageResultDTO<AccountSubjectDTO> list(String keyword, String subjectType, String status, int pageNo, int pageSize);

    AccountSubjectDTO create(CreateAccountSubjectRequestDTO request);

    AccountSubjectDTO detail(String subjectId);
}
