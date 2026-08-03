package com.abc123.walletaccount.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.walletaccount.common.BusinessException;
import com.abc123.walletaccount.dao.WalletAccountDao;
import com.abc123.walletaccount.dto.OpenWalletAccountRequestDTO;
import com.abc123.walletaccount.dto.WalletAccountStatusChangeRequestDTO;
import com.abc123.walletaccount.dto.WalletFlowExportRequestDTO;
import com.abc123.walletaccount.entity.WalletAccountEntity;
import com.abc123.walletaccount.entity.WalletIdempotentRecordEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class WalletAccountServiceImplTest {

    @Test
    void shouldReturnExistingAccountWhenOpenRequestIsIdempotent() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        OpenWalletAccountRequestDTO requestDTO = createOpenRequest();
        WalletAccountEntity existing = createAccount("WA-EXISTING-001", "ACTIVE", "10.00", "0.00", "0.00", "0.00");
        when(dao.findAccountByOwnerAndTypeScene("WO-NEW-001", "MAIN", "USER_STORE")).thenReturn(existing);

        assertEquals("WA-EXISTING-001", service.openAccount(requestDTO).getWalletAccountNo());
    }

    @Test
    void shouldRejectClosingAccountWhenBalancesRemain() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        WalletAccountStatusChangeRequestDTO requestDTO = new WalletAccountStatusChangeRequestDTO();
        requestDTO.setTargetStatus("CLOSED");
        requestDTO.setOperatorRole("FUNDS");
        requestDTO.setOperationReason("主体注销且余额未清零");
        when(dao.findAccountByNo("WA-USER-001"))
                .thenReturn(createAccount("WA-USER-001", "ACTIVE", "20.00", "5.00", "0.00", "0.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeStatus("WA-USER-001", requestDTO));

        assertEquals("WALLET_ACCOUNT_CLOSE_REJECTED", exception.getCode());
    }

    @Test
    void shouldAllowFreezeFromActiveStatus() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        WalletAccountStatusChangeRequestDTO requestDTO = new WalletAccountStatusChangeRequestDTO();
        requestDTO.setTargetStatus("FROZEN");
        requestDTO.setOperatorRole("FUNDS");
        requestDTO.setOperationReason("命中风控预警，先冻结账户");
        WalletAccountEntity active = createAccount("WA-USER-001", "ACTIVE", "0.00", "0.00", "0.00", "0.00");
        WalletAccountEntity frozen = createAccount("WA-USER-001", "FROZEN", "0.00", "0.00", "0.00", "0.00");
        when(dao.findAccountByNo("WA-USER-001")).thenReturn(active, frozen);
        when(dao.updateAccountStatus("WA-USER-001", "ACTIVE", "FROZEN", null)).thenReturn(1);

        assertEquals("FROZEN", service.changeStatus("WA-USER-001", requestDTO).getAccountStatus());
        verify(dao).updateAccountStatus(eq("WA-USER-001"), eq("ACTIVE"), eq("FROZEN"), eq(null));
        verify(dao).insertFlow(any());
        verify(dao).insertStatusLog(any());
    }

    @Test
    void shouldReturnExistingAccountWhenRequestNoAlreadySucceeded() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        WalletIdempotentRecordEntity recordEntity = new WalletIdempotentRecordEntity();
        recordEntity.setRequestNo("REQ-OPEN-001");
        recordEntity.setResultRefNo("WA-EXISTING-001");
        WalletAccountEntity existing = createAccount("WA-EXISTING-001", "ACTIVE", "10.00", "0.00", "0.00", "0.00");
        when(dao.findIdempotentRecordByRequestNo("REQ-OPEN-001")).thenReturn(recordEntity);
        when(dao.findAccountByNo("WA-EXISTING-001")).thenReturn(existing);

        assertEquals("WA-EXISTING-001", service.openAccount(createOpenRequest()).getWalletAccountNo());
    }

    @Test
    void shouldReturnExistingAccountWhenConcurrentInsertHitsUniqueConstraint() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        OpenWalletAccountRequestDTO requestDTO = createOpenRequest();
        WalletAccountEntity existing = createAccount("WA-EXISTING-002", "INIT", "0.00", "0.00", "0.00", "0.00");
        when(dao.findIdempotentRecordByRequestNo("REQ-OPEN-001")).thenReturn(null);
        when(dao.findAccountByOwnerAndTypeScene("WO-NEW-001", "MAIN", "USER_STORE"))
                .thenReturn(null, existing);
        when(dao.findOwnerById("WO-NEW-001")).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate")).when(dao).insertAccount(any());

        assertEquals("WA-EXISTING-002", service.openAccount(requestDTO).getWalletAccountNo());
        verify(dao).updateIdempotentRecordSuccess("REQ-OPEN-001", "WA-EXISTING-002");
    }

    @Test
    void shouldReturnExistingAccountWhenIdempotentKeyConflictWaitsForBusinessAccount() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        OpenWalletAccountRequestDTO requestDTO = createOpenRequest();
        requestDTO.setRequestNo("REQ-OPEN-002");
        requestDTO.setWalletOwnerId("WO-NEW-002");
        WalletAccountEntity existing = createAccount("WA-EXISTING-003", "INIT", "0.00", "0.00", "0.00", "0.00");
        when(dao.findIdempotentRecordByRequestNo("REQ-OPEN-002")).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate")).when(dao).insertIdempotentRecord(any());
        when(dao.findAccountByOwnerAndTypeScene("WO-NEW-002", "MAIN", "USER_STORE"))
                .thenReturn(null, null, existing);

        assertEquals("WA-EXISTING-003", service.openAccount(requestDTO).getWalletAccountNo());
    }

    @Test
    void shouldPersistExportTaskWhenExportRequested() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        WalletFlowExportRequestDTO requestDTO = new WalletFlowExportRequestDTO();
        requestDTO.setWalletAccountNo("WA-USER-001");
        requestDTO.setOperatorId("operator-001");
        requestDTO.setOperatorRole("FINANCE");
        requestDTO.setOperatorName("测试人员");

        assertEquals("ACCEPTED", service.exportFlows(requestDTO).getTaskStatus());
        verify(dao).insertExportTask(any());
    }

    @Test
    void shouldRejectOpenAccountWhenOperatorRoleIsNotFunds() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        OpenWalletAccountRequestDTO requestDTO = createOpenRequest();
        requestDTO.setOperatorRole("OPERATIONS");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.openAccount(requestDTO));

        assertEquals("WALLET_ACCOUNT_PERMISSION_DENIED", exception.getCode());
    }

    @Test
    void shouldRejectFlowExportWhenOperatorRoleIsNotFinanceOrFunds() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        WalletFlowExportRequestDTO requestDTO = new WalletFlowExportRequestDTO();
        requestDTO.setOperatorRole("PRODUCT");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.exportFlows(requestDTO));

        assertEquals("WALLET_ACCOUNT_EXPORT_FORBIDDEN", exception.getCode());
    }

    @Test
    void shouldRejectClosingAccountWhenReasonTooShort() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);
        WalletAccountStatusChangeRequestDTO requestDTO = new WalletAccountStatusChangeRequestDTO();
        requestDTO.setTargetStatus("CLOSED");
        requestDTO.setOperatorRole("FUNDS");
        requestDTO.setOperationReason("余额清零");
        when(dao.findAccountByNo("WA-USER-001"))
                .thenReturn(createAccount("WA-USER-001", "ACTIVE", "0.00", "0.00", "0.00", "0.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeStatus("WA-USER-001", requestDTO));

        assertEquals("WALLET_ACCOUNT_CLOSE_REASON_INVALID", exception.getCode());
    }

    private OpenWalletAccountRequestDTO createOpenRequest() {
        OpenWalletAccountRequestDTO requestDTO = new OpenWalletAccountRequestDTO();
        requestDTO.setRequestNo("REQ-OPEN-001");
        requestDTO.setWalletOwnerId("WO-NEW-001");
        requestDTO.setOwnerType("USER");
        requestDTO.setOwnerName("新用户");
        requestDTO.setAccountType("MAIN");
        requestDTO.setAccountScene("USER_STORE");
        requestDTO.setOperatorId("tester");
        requestDTO.setOperatorRole("FUNDS");
        requestDTO.setOperatorName("测试人员");
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
