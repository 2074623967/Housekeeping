package com.abc123.wallet.controller;

import static org.mockito.Mockito.verify;

import com.abc123.wallet.service.WalletMarketingFundService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 营销资金看板控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class WalletMarketingFundControllerTest {

    @Mock
    private WalletMarketingFundService walletMarketingFundService;

    @Test
    void shouldReturnMarketingFundDashboard() {
        WalletMarketingFundController controller = new WalletMarketingFundController(walletMarketingFundService);

        controller.dashboard();

        verify(walletMarketingFundService).getDashboard();
    }
}
