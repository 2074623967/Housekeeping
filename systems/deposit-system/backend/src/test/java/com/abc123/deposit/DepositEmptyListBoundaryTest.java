package com.abc123.deposit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.deposit.dao.DepositDao;
import com.abc123.deposit.dto.DepositAccountQueryDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
import com.abc123.deposit.service.impl.DepositServiceImpl;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 保证金查询为空时的稳定边界测试。 */
class DepositEmptyListBoundaryTest {

    @Test
    void shouldNormalizeNullAccountAndFlowLists() {
        DepositDao dao = mock(DepositDao.class);
        when(dao.findAccounts(org.mockito.ArgumentMatchers.any(DepositAccountQueryDTO.class))).thenReturn(null);
        when(dao.findFlows(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(DepositFlowQueryDTO.class)))
                .thenReturn(null);

        DepositServiceImpl service = new DepositServiceImpl(dao);

        assertTrue(service.accounts(new DepositAccountQueryDTO()).isEmpty());
        assertTrue(service.flows("DEP-EMPTY", new DepositFlowQueryDTO()).isEmpty());
        assertTrue(service.totalAccounts().isEmpty());
        assertTrue(service.migrationPreview().isEmpty());
        assertTrue(new String(service.exportAccounts(new DepositAccountQueryDTO()), StandardCharsets.UTF_8)
                .contains("账户号,主体编号"));
        assertTrue(new String(service.exportFlows("DEP-EMPTY", new DepositFlowQueryDTO()), StandardCharsets.UTF_8)
                .contains("流水号,账户号"));
    }
}
