package com.abc123.accounting.controller;

import com.abc123.accounting.common.ApiResponse;
import com.abc123.accounting.dto.BalanceOperationRequestDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import com.abc123.accounting.service.BalanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 余额控制器。
 */
@RestController
@RequestMapping("/api/accounting/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<BalanceSnapshotDTO> detail(@PathVariable String accountNo) {
        return ApiResponse.success(balanceService.detail(accountNo));
    }

    @PostMapping("/credit")
    public ApiResponse<BalanceSnapshotDTO> credit(@RequestBody BalanceOperationRequestDTO request) {
        return ApiResponse.success(balanceService.credit(request));
    }

    @PostMapping("/freeze")
    public ApiResponse<BalanceSnapshotDTO> freeze(@RequestBody BalanceOperationRequestDTO request) {
        return ApiResponse.success(balanceService.freeze(request));
    }

    @PostMapping("/unfreeze")
    public ApiResponse<BalanceSnapshotDTO> unfreeze(@RequestBody BalanceOperationRequestDTO request) {
        return ApiResponse.success(balanceService.unfreeze(request));
    }
}
