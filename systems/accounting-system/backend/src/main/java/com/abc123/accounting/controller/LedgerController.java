package com.abc123.accounting.controller;

import com.abc123.accounting.common.ApiResponse;
import com.abc123.accounting.dto.LedgerItemDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.service.LedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流水控制器。
 */
@RestController
@RequestMapping("/api/accounting/ledgers")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<LedgerItemDTO>> list(
            @RequestParam(required = false) String accountNo,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(ledgerService.list(accountNo, bizNo, bizType, pageNo, pageSize));
    }
}
