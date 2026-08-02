package com.abc123.walletaccount.controller;

import com.abc123.walletaccount.common.ApiResponse;
import com.abc123.walletaccount.dto.OpenWalletAccountRequestDTO;
import com.abc123.walletaccount.dto.PageResultDTO;
import com.abc123.walletaccount.dto.WalletAccountDTO;
import com.abc123.walletaccount.dto.WalletAccountDetailDTO;
import com.abc123.walletaccount.dto.WalletAccountQueryDTO;
import com.abc123.walletaccount.dto.WalletAccountStatusChangeRequestDTO;
import com.abc123.walletaccount.dto.WalletBalanceDTO;
import com.abc123.walletaccount.dto.WalletFlowDTO;
import com.abc123.walletaccount.dto.WalletFlowExportRequestDTO;
import com.abc123.walletaccount.dto.WalletFlowExportTaskDTO;
import com.abc123.walletaccount.dto.WalletFlowQueryDTO;
import com.abc123.walletaccount.service.WalletAccountService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletAccountController {

    private final WalletAccountService walletAccountService;

    public WalletAccountController(WalletAccountService walletAccountService) {
        this.walletAccountService = walletAccountService;
    }

    @GetMapping("/accounts")
    public ApiResponse<PageResultDTO<WalletAccountDTO>> pageAccounts(WalletAccountQueryDTO queryDTO) {
        return ApiResponse.success(walletAccountService.pageAccounts(queryDTO));
    }

    @GetMapping("/accounts/{walletAccountNo}")
    public ApiResponse<WalletAccountDetailDTO> getAccountDetail(@PathVariable String walletAccountNo) {
        return ApiResponse.success(walletAccountService.getAccountDetail(walletAccountNo));
    }

    @GetMapping("/accounts/{walletAccountNo}/balance")
    public ApiResponse<WalletBalanceDTO> getBalance(@PathVariable String walletAccountNo) {
        return ApiResponse.success(walletAccountService.getBalance(walletAccountNo));
    }

    @GetMapping("/accounts/balances")
    public ApiResponse<List<WalletBalanceDTO>> listBalances(@RequestParam(required = false) String walletAccountNos) {
        if (walletAccountNos == null || walletAccountNos.trim().isEmpty()) {
            return ApiResponse.success(Collections.<WalletBalanceDTO>emptyList());
        }
        List<String> accountNos = Arrays.stream(walletAccountNos.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
        return ApiResponse.success(walletAccountService.listBalances(accountNos));
    }

    @PostMapping("/accounts/balances/query")
    public ApiResponse<List<WalletBalanceDTO>> queryBalances(@RequestBody List<String> walletAccountNos) {
        return ApiResponse.success(walletAccountService.listBalances(walletAccountNos));
    }

    @GetMapping("/flows")
    public ApiResponse<List<WalletFlowDTO>> listFlows(WalletFlowQueryDTO queryDTO) {
        return ApiResponse.success(walletAccountService.listFlows(queryDTO));
    }

    @PostMapping("/flows/export")
    public ApiResponse<WalletFlowExportTaskDTO> exportFlows(@RequestBody WalletFlowExportRequestDTO requestDTO) {
        return ApiResponse.success(walletAccountService.exportFlows(requestDTO));
    }

    @PostMapping("/accounts")
    public ApiResponse<WalletAccountDTO> openAccount(@RequestBody OpenWalletAccountRequestDTO requestDTO) {
        return ApiResponse.success(walletAccountService.openAccount(requestDTO));
    }

    @PostMapping("/accounts/{walletAccountNo}/status-change")
    public ApiResponse<WalletAccountDTO> changeStatus(@PathVariable String walletAccountNo,
            @RequestBody WalletAccountStatusChangeRequestDTO requestDTO) {
        return ApiResponse.success(walletAccountService.changeStatus(walletAccountNo, requestDTO));
    }
}
