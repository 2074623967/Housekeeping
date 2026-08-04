package com.abc123.deposit.controller;

import com.abc123.deposit.common.ApiResponse;
import com.abc123.deposit.dto.DebtOffsetRequestDTO;
import com.abc123.deposit.dto.DepositAccountCreateRequestDTO;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositActionRequestDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.service.DepositService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保证金后台接口。
 */
@RestController
@RequestMapping("/api/deposits")
public class DepositController {

    private final DepositService service;

    public DepositController(DepositService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public ApiResponse<List<DepositAccountDTO>> accounts() {
        return ApiResponse.success(service.accounts());
    }

    @PostMapping("/accounts")
    public ApiResponse<DepositAccountDTO> openAccount(@RequestBody DepositAccountCreateRequestDTO request) {
        return ApiResponse.success(service.openAccount(request));
    }

    @PostMapping("/accounts/{accountNo}/collect")
    public ApiResponse<DepositAccountDTO> collect(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.collect(request));
    }

    @PostMapping("/accounts/{accountNo}/freeze")
    public ApiResponse<DepositAccountDTO> freeze(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.freeze(request));
    }

    @PostMapping("/accounts/{accountNo}/unfreeze")
    public ApiResponse<DepositAccountDTO> unfreeze(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.unfreeze(request));
    }

    @PostMapping("/accounts/{accountNo}/deduct")
    public ApiResponse<DepositAccountDTO> deduct(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.deduct(request));
    }

    @PostMapping("/accounts/{accountNo}/refund")
    public ApiResponse<DepositAccountDTO> refund(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.refund(request));
    }

    @PostMapping("/offset-debt")
    public ApiResponse<DepositAccountDTO> offsetDebt(@RequestBody DebtOffsetRequestDTO request) {
        return ApiResponse.success(service.offsetDebt(request));
    }

    @GetMapping("/accounts/{accountNo}/flows")
    public ApiResponse<List<DepositFlowDTO>> flows(@PathVariable String accountNo) {
        return ApiResponse.success(service.flows(accountNo));
    }
}
