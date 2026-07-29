package com.abc123.wallet.controller;

import com.abc123.wallet.common.ApiResponse;
import com.abc123.wallet.dto.WalletMarketingFundDashboardDTO;
import com.abc123.wallet.service.WalletMarketingFundService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/marketing-funds")
public class WalletMarketingFundController {

    private final WalletMarketingFundService walletMarketingFundService;

    public WalletMarketingFundController(WalletMarketingFundService walletMarketingFundService) {
        this.walletMarketingFundService = walletMarketingFundService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<WalletMarketingFundDashboardDTO> dashboard() {
        return ApiResponse.success(walletMarketingFundService.getDashboard());
    }
}
