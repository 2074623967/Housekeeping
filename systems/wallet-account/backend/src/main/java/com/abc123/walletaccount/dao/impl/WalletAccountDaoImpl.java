package com.abc123.walletaccount.dao.impl;

import com.abc123.walletaccount.dao.WalletAccountDao;
import com.abc123.walletaccount.entity.WalletAccountEntity;
import com.abc123.walletaccount.entity.WalletAccountStatusLogEntity;
import com.abc123.walletaccount.entity.WalletFlowEntity;
import com.abc123.walletaccount.entity.WalletFlowExportTaskEntity;
import com.abc123.walletaccount.entity.WalletIdempotentRecordEntity;
import com.abc123.walletaccount.entity.WalletOwnerEntity;
import com.abc123.walletaccount.mapper.WalletAccountMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class WalletAccountDaoImpl implements WalletAccountDao {

    private final WalletAccountMapper walletAccountMapper;

    public WalletAccountDaoImpl(WalletAccountMapper walletAccountMapper) {
        this.walletAccountMapper = walletAccountMapper;
    }

    @Override
    public long countAccounts(String keyword, String ownerType, String accountStatus) {
        return walletAccountMapper.countAccounts(keyword, ownerType, accountStatus);
    }

    @Override
    public List<WalletAccountEntity> listAccounts(String keyword, String ownerType, String accountStatus,
            int offset, int limit) {
        return walletAccountMapper.listAccounts(keyword, ownerType, accountStatus, offset, limit);
    }

    @Override
    public WalletAccountEntity findAccountByNo(String walletAccountNo) {
        return walletAccountMapper.findAccountByNo(walletAccountNo);
    }

    @Override
    public WalletAccountEntity findAccountByOwnerAndTypeScene(String walletOwnerId, String accountType,
            String accountScene) {
        return walletAccountMapper.findAccountByOwnerAndTypeScene(walletOwnerId, accountType, accountScene);
    }

    @Override
    public WalletOwnerEntity findOwnerById(String walletOwnerId) {
        return walletAccountMapper.findOwnerById(walletOwnerId);
    }

    @Override
    public WalletIdempotentRecordEntity findIdempotentRecordByRequestNo(String requestNo) {
        return walletAccountMapper.findIdempotentRecordByRequestNo(requestNo);
    }

    @Override
    public List<WalletAccountEntity> listBalancesByAccountNos(List<String> walletAccountNos) {
        return walletAccountMapper.listBalancesByAccountNos(walletAccountNos);
    }

    @Override
    public long countFlows(String walletAccountNo, String sourceSystem, String sourceBizNo) {
        return walletAccountMapper.countFlows(walletAccountNo, sourceSystem, sourceBizNo);
    }

    @Override
    public List<WalletFlowEntity> listFlows(String walletAccountNo, String sourceSystem, String sourceBizNo,
            int offset, int limit) {
        return walletAccountMapper.listFlows(walletAccountNo, sourceSystem, sourceBizNo, offset, limit);
    }

    @Override
    public long countExportTasks(String operatorId, String taskStatus) {
        return walletAccountMapper.countExportTasks(operatorId, taskStatus);
    }

    @Override
    public List<WalletFlowExportTaskEntity> listExportTasks(String operatorId, String taskStatus, int offset, int limit) {
        return walletAccountMapper.listExportTasks(operatorId, taskStatus, offset, limit);
    }

    @Override
    public WalletFlowExportTaskEntity findExportTaskByNo(String exportTaskNo) {
        return walletAccountMapper.findExportTaskByNo(exportTaskNo);
    }

    @Override
    public List<WalletFlowEntity> listRecentFlowsByAccountNo(String walletAccountNo, int limit) {
        return walletAccountMapper.listRecentFlowsByAccountNo(walletAccountNo, limit);
    }

    @Override
    public List<WalletAccountStatusLogEntity> listStatusLogsByAccountNo(String walletAccountNo, int limit) {
        return walletAccountMapper.listStatusLogsByAccountNo(walletAccountNo, limit);
    }

    @Override
    public void insertOwner(WalletOwnerEntity ownerEntity) {
        walletAccountMapper.insertOwner(ownerEntity);
    }

    @Override
    public void insertAccount(WalletAccountEntity accountEntity) {
        walletAccountMapper.insertAccount(accountEntity);
    }

    @Override
    public void insertBalance(WalletAccountEntity accountEntity) {
        walletAccountMapper.insertBalance(accountEntity);
    }

    @Override
    public void insertFlow(WalletFlowEntity flowEntity) {
        walletAccountMapper.insertFlow(flowEntity);
    }

    @Override
    public void insertStatusLog(WalletAccountStatusLogEntity statusLogEntity) {
        walletAccountMapper.insertStatusLog(statusLogEntity);
    }

    @Override
    public void insertExportTask(WalletFlowExportTaskEntity exportTaskEntity) {
        walletAccountMapper.insertExportTask(exportTaskEntity);
    }

    @Override
    public void insertIdempotentRecord(WalletIdempotentRecordEntity idempotentRecordEntity) {
        walletAccountMapper.insertIdempotentRecord(idempotentRecordEntity);
    }

    @Override
    public int updateIdempotentRecordSuccess(String requestNo, String resultRefNo) {
        return walletAccountMapper.updateIdempotentRecordSuccess(requestNo, resultRefNo);
    }

    @Override
    public int updateAccountStatus(String walletAccountNo, String currentStatus, String accountStatus,
            LocalDateTime closedAt) {
        return walletAccountMapper.updateAccountStatus(walletAccountNo, currentStatus, accountStatus, closedAt);
    }
}
