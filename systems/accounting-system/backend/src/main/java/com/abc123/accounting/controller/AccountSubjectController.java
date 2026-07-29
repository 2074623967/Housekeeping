package com.abc123.accounting.controller;

import com.abc123.accounting.common.ApiResponse;
import com.abc123.accounting.dto.AccountSubjectDTO;
import com.abc123.accounting.dto.CreateAccountSubjectRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.service.AccountSubjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户主体控制器。
 */
@RestController
@RequestMapping("/api/accounting/subjects")
public class AccountSubjectController {

    private final AccountSubjectService accountSubjectService;

    public AccountSubjectController(AccountSubjectService accountSubjectService) {
        this.accountSubjectService = accountSubjectService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<AccountSubjectDTO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(accountSubjectService.list(keyword, subjectType, status, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<AccountSubjectDTO> create(@RequestBody CreateAccountSubjectRequestDTO request) {
        return ApiResponse.success(accountSubjectService.create(request));
    }

    @GetMapping("/{subjectId}")
    public ApiResponse<AccountSubjectDTO> detail(@PathVariable String subjectId) {
        return ApiResponse.success(accountSubjectService.detail(subjectId));
    }
}
