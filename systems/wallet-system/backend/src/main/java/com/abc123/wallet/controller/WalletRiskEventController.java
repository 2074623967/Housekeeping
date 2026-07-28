package com.abc123.wallet.controller;

import com.abc123.wallet.common.ApiResponse;
import com.abc123.wallet.dto.WalletRiskApprovalRequestDTO;
import com.abc123.wallet.dto.WalletRiskEventDTO;
import com.abc123.wallet.service.WalletRiskEventService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/risk-events")
public class WalletRiskEventController {

    private final WalletRiskEventService walletRiskEventService;

    public WalletRiskEventController(WalletRiskEventService walletRiskEventService) {
        this.walletRiskEventService = walletRiskEventService;
    }

    @GetMapping
    public ApiResponse<List<WalletRiskEventDTO>> list() {
        return ApiResponse.success(walletRiskEventService.listRiskEvents());
    }

    @PostMapping("/approve")
    public ApiResponse<WalletRiskEventDTO> approve(@RequestBody WalletRiskApprovalRequestDTO request) {
        return ApiResponse.success(walletRiskEventService.approve(request));
    }
}
