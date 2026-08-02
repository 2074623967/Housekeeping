package com.abc123.walletaccount.dao;

import com.abc123.walletaccount.entity.WalletAccountEntity;
import com.abc123.walletaccount.entity.WalletAccountStatusLogEntity;
import com.abc123.walletaccount.entity.WalletFlowEntity;
import com.abc123.walletaccount.entity.WalletFlowExportTaskEntity;
import com.abc123.walletaccount.entity.WalletOwnerEntity;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 钱包账户领域数据访问门面。
 *
 * <p>业务层只依赖该门面，具体 SQL 由 MyBatis mapper 承接，避免业务编排直接耦合 SQL 接口。</p>
 */
public interface WalletAccountDao {

    long countAccounts(String keyword, String ownerType, String accountStatus);

    List<WalletAccountEntity> listAccounts(String keyword, String ownerType, String accountStatus,
            int offset, int limit);

    WalletAccountEntity findAccountByNo(String walletAccountNo);

    WalletAccountEntity findAccountByOwnerAndTypeScene(String walletOwnerId, String accountType,
            String accountScene);

    WalletOwnerEntity findOwnerById(String walletOwnerId);

    List<WalletAccountEntity> listBalancesByAccountNos(List<String> walletAccountNos);

    long countFlows(String walletAccountNo, String sourceSystem, String sourceBizNo);

    List<WalletFlowEntity> listFlows(String walletAccountNo, String sourceSystem, String sourceBizNo,
            int offset, int limit);

    List<WalletFlowEntity> listRecentFlowsByAccountNo(String walletAccountNo, int limit);

    List<WalletAccountStatusLogEntity> listStatusLogsByAccountNo(String walletAccountNo, int limit);

    void insertOwner(WalletOwnerEntity ownerEntity);

    void insertAccount(WalletAccountEntity accountEntity);

    void insertBalance(WalletAccountEntity accountEntity);

    void insertFlow(WalletFlowEntity flowEntity);

    void insertStatusLog(WalletAccountStatusLogEntity statusLogEntity);

    void insertExportTask(WalletFlowExportTaskEntity exportTaskEntity);

    int updateAccountStatus(String walletAccountNo, String currentStatus, String accountStatus,
            LocalDateTime closedAt);
}
