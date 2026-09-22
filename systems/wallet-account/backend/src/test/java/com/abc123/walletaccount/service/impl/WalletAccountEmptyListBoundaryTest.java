package com.abc123.walletaccount.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.walletaccount.dao.WalletAccountDao;
import com.abc123.walletaccount.dto.WalletAccountQueryDTO;
import com.abc123.walletaccount.dto.WalletFlowExportTaskQueryDTO;
import com.abc123.walletaccount.dto.WalletFlowQueryDTO;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 钱包账户查询为空时的稳定边界测试。 */
class WalletAccountEmptyListBoundaryTest {

    @Test
    void shouldNormalizeNullDaoListsAndMissingQueries() {
        WalletAccountDao dao = mock(WalletAccountDao.class);
        when(dao.listAccounts(null, null, null, null, null, 0, 20)).thenReturn(null);
        when(dao.listFlows(null, null, null, 0, 20)).thenReturn(null);
        when(dao.listBalancesByAccountNos(Arrays.asList("WA-EMPTY"))).thenReturn(null);
        when(dao.listExportTasks(null, null, 0, 10)).thenReturn(null);

        WalletAccountServiceImpl service = new WalletAccountServiceImpl(dao);

        assertNotNull(service.pageAccounts(null).getRecords());
        assertTrue(service.pageAccounts(null).getRecords().isEmpty());
        assertTrue(service.listFlows((WalletFlowQueryDTO) null).getRecords().isEmpty());
        assertTrue(service.listBalances(Arrays.asList("WA-EMPTY")).isEmpty());

        WalletFlowExportTaskQueryDTO exportQuery = new WalletFlowExportTaskQueryDTO();
        exportQuery.setOperatorRole("FINANCE");
        assertTrue(service.listFlowExportTasks(exportQuery).getRecords().isEmpty());
    }
}
