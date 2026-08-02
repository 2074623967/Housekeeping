package com.abc123.walletaccount.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.walletaccount.dto.OpenWalletAccountRequestDTO;
import com.abc123.walletaccount.dto.PageResultDTO;
import com.abc123.walletaccount.dto.WalletAccountDTO;
import com.abc123.walletaccount.dto.WalletAccountDetailDTO;
import com.abc123.walletaccount.dto.WalletBalanceDTO;
import com.abc123.walletaccount.service.WalletAccountService;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletAccountController.class)
class WalletAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletAccountService walletAccountService;

    @Test
    void shouldReturnPagedAccounts() throws Exception {
        PageResultDTO<WalletAccountDTO> resultDTO = new PageResultDTO<WalletAccountDTO>();
        WalletAccountDTO accountDTO = new WalletAccountDTO();
        accountDTO.setWalletAccountNo("WA-USER-001");
        resultDTO.setTotal(1L);
        resultDTO.setRecords(Collections.singletonList(accountDTO));
        when(walletAccountService.pageAccounts(any())).thenReturn(resultDTO);

        mockMvc.perform(get("/api/wallet/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].walletAccountNo").value("WA-USER-001"));
    }

    @Test
    void shouldReturnBalanceByAccountNo() throws Exception {
        WalletBalanceDTO balanceDTO = new WalletBalanceDTO();
        balanceDTO.setWalletAccountNo("WA-USER-001");
        balanceDTO.setAvailableBalance(new BigDecimal("88.00"));
        when(walletAccountService.getBalance("WA-USER-001")).thenReturn(balanceDTO);

        mockMvc.perform(get("/api/wallet/accounts/WA-USER-001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.walletAccountNo").value("WA-USER-001"))
                .andExpect(jsonPath("$.data.availableBalance").value(88.00));
    }

    @Test
    void shouldOpenAccount() throws Exception {
        WalletAccountDTO accountDTO = new WalletAccountDTO();
        accountDTO.setWalletAccountNo("WA-NEW-001");
        when(walletAccountService.openAccount(any(OpenWalletAccountRequestDTO.class))).thenReturn(accountDTO);

        mockMvc.perform(post("/api/wallet/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"walletOwnerId\":\"WO-001\",\"ownerType\":\"USER\",\"ownerName\":\"测试用户\",\"accountType\":\"MAIN\",\"accountScene\":\"USER_STORE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.walletAccountNo").value("WA-NEW-001"));
    }
}
