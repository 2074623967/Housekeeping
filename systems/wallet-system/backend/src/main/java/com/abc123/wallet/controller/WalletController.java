package com.abc123.wallet.controller;

import com.abc123.wallet.common.ApiResponse;
import com.abc123.wallet.dto.WalletAccountDetailDTO;
import com.abc123.wallet.dto.WalletAccountDTO;
import com.abc123.wallet.dto.WalletRechargeOrderDTO;
import com.abc123.wallet.dto.WalletRechargeRequestDTO;
import com.abc123.wallet.service.WalletRechargeService;
import com.abc123.wallet.service.WalletService;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/accounts")
public class WalletController {

    private final WalletService walletService;
    private final WalletRechargeService walletRechargeService;

    public WalletController(WalletService walletService, WalletRechargeService walletRechargeService) {
        this.walletService = walletService;
        this.walletRechargeService = walletRechargeService;
    }

    @GetMapping
    public ApiResponse<List<WalletAccountDTO>> list() {
        return ApiResponse.success(walletService.listAccounts());
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<WalletAccountDetailDTO> detail(@PathVariable String accountNo) {
        return ApiResponse.success(walletService.getDetail(accountNo));
    }

    @PostMapping("/recharges")
    public ApiResponse<WalletRechargeOrderDTO> recharge(@RequestBody WalletRechargeRequestDTO request) {
        return ApiResponse.success(walletRechargeService.recharge(request));
    }
}
