package com.abc123.walletaccount.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.walletaccount.common.BusinessException;
import com.abc123.walletaccount.dto.OpenWalletAccountRequestDTO;
import com.abc123.walletaccount.dto.WalletAccountStatusChangeRequestDTO;
import com.abc123.walletaccount.entity.WalletAccountEntity;
import com.abc123.walletaccount.mapper.WalletAccountMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WalletAccountServiceImplTest {

    @Test
    void shouldReturnExistingAccountWhenOpenRequestIsIdempotent() {
        WalletAccountMapper mapper = mock(WalletAccountMapper.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(mapper);
        OpenWalletAccountRequestDTO requestDTO = createOpenRequest();
        WalletAccountEntity existing = createAccount("WA-EXISTING-001", "ACTIVE", "10.00", "0.00", "0.00", "0.00");
        when(mapper.findAccountByOwnerAndTypeScene("WO-NEW-001", "MAIN", "USER_STORE")).thenReturn(existing);

        assertEquals("WA-EXISTING-001", service.openAccount(requestDTO).getWalletAccountNo());
    }

    @Test
    void shouldRejectClosingAccountWhenBalancesRemain() {
        WalletAccountMapper mapper = mock(WalletAccountMapper.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(mapper);
        WalletAccountStatusChangeRequestDTO requestDTO = new WalletAccountStatusChangeRequestDTO();
        requestDTO.setTargetStatus("CLOSED");
        when(mapper.findAccountByNo("WA-USER-001"))
                .thenReturn(createAccount("WA-USER-001", "ACTIVE", "20.00", "5.00", "0.00", "0.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeStatus("WA-USER-001", requestDTO));

        assertEquals("WALLET_ACCOUNT_CLOSE_REJECTED", exception.getCode());
    }

    @Test
    void shouldAllowFreezeFromActiveStatus() {
        WalletAccountMapper mapper = mock(WalletAccountMapper.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(mapper);
        WalletAccountStatusChangeRequestDTO requestDTO = new WalletAccountStatusChangeRequestDTO();
        requestDTO.setTargetStatus("FROZEN");
        WalletAccountEntity active = createAccount("WA-USER-001", "ACTIVE", "0.00", "0.00", "0.00", "0.00");
        WalletAccountEntity frozen = createAccount("WA-USER-001", "FROZEN", "0.00", "0.00", "0.00", "0.00");
        when(mapper.findAccountByNo("WA-USER-001")).thenReturn(active, frozen);

        assertEquals("FROZEN", service.changeStatus("WA-USER-001", requestDTO).getAccountStatus());
        verify(mapper).updateAccountStatus(eq("WA-USER-001"), eq("FROZEN"), eq(null));
        verify(mapper).insertFlow(any());
    }

    private OpenWalletAccountRequestDTO createOpenRequest() {
        OpenWalletAccountRequestDTO requestDTO = new OpenWalletAccountRequestDTO();
        requestDTO.setWalletOwnerId("WO-NEW-001");
        requestDTO.setOwnerType("USER");
        requestDTO.setOwnerName("新用户");
        requestDTO.setAccountType("MAIN");
        requestDTO.setAccountScene("USER_STORE");
        return requestDTO;
    }

    private WalletAccountEntity createAccount(String accountNo, String status, String total, String frozen,
            String pendingIn, String pendingOut) {
        WalletAccountEntity entity = new WalletAccountEntity();
        entity.setWalletAccountNo(accountNo);
        entity.setWalletOwnerId("WO-001");
        entity.setOwnerType("USER");
        entity.setOwnerName("测试用户");
        entity.setAccountType("MAIN");
        entity.setAccountScene("USER_STORE");
        entity.setCurrencyCode("CNY");
        entity.setAccountStatus(status);
        entity.setAllowCredit(false);
        entity.setRiskLevel("LOW");
        entity.setTotalBalance(new BigDecimal(total));
        entity.setAvailableBalance(new BigDecimal(total));
        entity.setFrozenBalance(new BigDecimal(frozen));
        entity.setPendingInBalance(new BigDecimal(pendingIn));
        entity.setPendingOutBalance(new BigDecimal(pendingOut));
        return entity;
    }
}
