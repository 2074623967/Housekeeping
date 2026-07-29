package com.abc123.wallet.controller;

import com.abc123.wallet.common.ApiResponse;
import com.abc123.wallet.dto.WalletRedPacketDTO;
import com.abc123.wallet.dto.WalletRedPacketRequestDTO;
import com.abc123.wallet.service.WalletRedPacketService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/red-packets")
public class WalletRedPacketController {

    private final WalletRedPacketService walletRedPacketService;

    public WalletRedPacketController(WalletRedPacketService walletRedPacketService) {
        this.walletRedPacketService = walletRedPacketService;
    }

    @GetMapping
    public ApiResponse<List<WalletRedPacketDTO>> list() {
        return ApiResponse.success(walletRedPacketService.listRedPackets());
    }

    @PostMapping
    public ApiResponse<WalletRedPacketDTO> issue(@RequestBody WalletRedPacketRequestDTO request) {
        return ApiResponse.success(walletRedPacketService.issue(request));
    }
}
