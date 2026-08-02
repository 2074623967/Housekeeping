package com.abc123.walletaccount.mapper;

import com.abc123.walletaccount.entity.WalletAccountEntity;
import com.abc123.walletaccount.entity.WalletAccountStatusLogEntity;
import com.abc123.walletaccount.entity.WalletFlowEntity;
import com.abc123.walletaccount.entity.WalletFlowExportTaskEntity;
import com.abc123.walletaccount.entity.WalletOwnerEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletAccountMapper {

    long countAccounts(@Param("keyword") String keyword,
            @Param("ownerType") String ownerType,
            @Param("accountStatus") String accountStatus);

    List<WalletAccountEntity> listAccounts(@Param("keyword") String keyword,
            @Param("ownerType") String ownerType,
            @Param("accountStatus") String accountStatus,
            @Param("offset") int offset,
            @Param("limit") int limit);

    WalletAccountEntity findAccountByNo(@Param("walletAccountNo") String walletAccountNo);

    WalletAccountEntity findAccountByOwnerAndTypeScene(@Param("walletOwnerId") String walletOwnerId,
            @Param("accountType") String accountType,
            @Param("accountScene") String accountScene);

    WalletOwnerEntity findOwnerById(@Param("walletOwnerId") String walletOwnerId);

    List<WalletAccountEntity> listBalancesByAccountNos(@Param("walletAccountNos") List<String> walletAccountNos);

    List<WalletFlowEntity> listFlows(@Param("walletAccountNo") String walletAccountNo,
            @Param("sourceSystem") String sourceSystem,
            @Param("sourceBizNo") String sourceBizNo);

    List<WalletFlowEntity> listRecentFlowsByAccountNo(@Param("walletAccountNo") String walletAccountNo,
            @Param("limit") int limit);

    List<WalletAccountStatusLogEntity> listStatusLogsByAccountNo(@Param("walletAccountNo") String walletAccountNo,
            @Param("limit") int limit);

    void insertOwner(WalletOwnerEntity ownerEntity);

    void insertAccount(WalletAccountEntity accountEntity);

    void insertBalance(WalletAccountEntity accountEntity);

    void insertFlow(WalletFlowEntity flowEntity);

    void insertStatusLog(WalletAccountStatusLogEntity statusLogEntity);

    void insertExportTask(WalletFlowExportTaskEntity exportTaskEntity);

    int updateAccountStatus(@Param("walletAccountNo") String walletAccountNo,
            @Param("currentStatus") String currentStatus,
            @Param("accountStatus") String accountStatus,
            @Param("closedAt") LocalDateTime closedAt);
}
