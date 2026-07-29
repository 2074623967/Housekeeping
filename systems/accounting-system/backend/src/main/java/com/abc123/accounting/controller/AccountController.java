package com.abc123.accounting.controller;

import com.abc123.accounting.common.ApiResponse;
import com.abc123.accounting.dto.AccountDetailDTO;
import com.abc123.accounting.dto.AccountListItemDTO;
import com.abc123.accounting.dto.OpenAccountRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户控制器。
 */
@RestController
@RequestMapping("/api/accounting/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<AccountListItemDTO>> list(
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(accountService.list(subjectId, accountType, status, pageNo, pageSize));
    }

    @PostMapping("/open")
    public ApiResponse<AccountListItemDTO> open(@RequestBody OpenAccountRequestDTO request) {
        return ApiResponse.success(accountService.open(request));
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<AccountDetailDTO> detail(@PathVariable String accountNo) {
        return ApiResponse.success(accountService.detail(accountNo));
    }
}
