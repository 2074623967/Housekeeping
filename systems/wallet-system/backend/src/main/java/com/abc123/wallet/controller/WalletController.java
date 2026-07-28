package com.abc123.wallet.controller;

import com.abc123.wallet.common.ApiResponse;
import com.abc123.wallet.dto.WalletAccountDetailDTO;
import com.abc123.wallet.dto.WalletAccountDTO;
import com.abc123.wallet.dto.WalletRechargeOrderDTO;
import com.abc123.wallet.dto.WalletRechargeRequestDTO;
import com.abc123.wallet.dto.WalletTransferOrderDTO;
import com.abc123.wallet.dto.WalletTransferRequestDTO;
import com.abc123.wallet.dto.WalletWithdrawOrderDTO;
import com.abc123.wallet.dto.WalletWithdrawRequestDTO;
import com.abc123.wallet.service.WalletRechargeService;
import com.abc123.wallet.service.WalletService;
import com.abc123.wallet.service.WalletTransferService;
import com.abc123.wallet.service.WalletWithdrawService;
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
    private final WalletTransferService walletTransferService;
    private final WalletWithdrawService walletWithdrawService;

    public WalletController(WalletService walletService,
            WalletRechargeService walletRechargeService,
            WalletTransferService walletTransferService,
            WalletWithdrawService walletWithdrawService) {
        this.walletService = walletService;
        this.walletRechargeService = walletRechargeService;
        this.walletTransferService = walletTransferService;
        this.walletWithdrawService = walletWithdrawService;
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

    @PostMapping("/withdrawals")
    public ApiResponse<WalletWithdrawOrderDTO> withdraw(@RequestBody WalletWithdrawRequestDTO request) {
        return ApiResponse.success(walletWithdrawService.withdraw(request));
    }

    @PostMapping("/transfers")
    public ApiResponse<WalletTransferOrderDTO> transfer(@RequestBody WalletTransferRequestDTO request) {
        return ApiResponse.success(walletTransferService.transfer(request));
    }
}
