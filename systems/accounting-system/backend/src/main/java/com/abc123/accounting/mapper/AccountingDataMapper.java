package com.abc123.accounting.mapper;

import com.abc123.accounting.entity.AccountAdjustmentEntity;
import com.abc123.accounting.entity.AccountBalanceEntity;
import com.abc123.accounting.entity.AccountEntity;
import com.abc123.accounting.entity.AccountEventEntity;
import com.abc123.accounting.entity.AccountFreezeEntity;
import com.abc123.accounting.entity.AccountLedgerEntity;
import com.abc123.accounting.entity.AccountSubjectEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 账户账务持久化 Mapper。
 */
public interface AccountingDataMapper {

    /**
     * 统计主体数量。
     */
    long countSubjects();

    /**
     * 查询主体列表。
     */
    List<AccountSubjectEntity> findSubjects();

    /**
     * 查询单个主体。
     */
    AccountSubjectEntity findSubject(@Param("subjectId") String subjectId);

    /**
     * 新增主体。
     */
    int insertSubject(AccountSubjectEntity entity);

    /**
     * 查询账户列表。
     */
    List<AccountEntity> findAccounts();

    /**
     * 查询单个账户。
     */
    AccountEntity findAccount(@Param("accountNo") String accountNo);

    /**
     * 新增账户。
     */
    int insertAccount(AccountEntity entity);

    /**
     * 更新账户最近变更时间。
     */
    int updateAccountChangeTime(@Param("accountNo") String accountNo, @Param("lastChangeAt") String lastChangeAt);

    /**
     * 查询余额快照。
     */
    AccountBalanceEntity findBalance(@Param("accountNo") String accountNo);

    /**
     * 新增余额快照。
     */
    int insertBalance(AccountBalanceEntity entity);

    /**
     * 更新余额快照。
     */
    int updateBalance(@Param("accountNo") String accountNo,
                      @Param("availableAmount") BigDecimal availableAmount,
                      @Param("frozenAmount") BigDecimal frozenAmount,
                      @Param("inTransitAmount") BigDecimal inTransitAmount,
                      @Param("updatedAt") String updatedAt);

    /**
     * 查询流水列表。
     */
    List<AccountLedgerEntity> findLedgers();

    /**
     * 新增流水。
     */
    int insertLedger(AccountLedgerEntity entity);

    /**
     * 查询冻结单列表。
     */
    List<AccountFreezeEntity> findFreezes();

    /**
     * 查询冻结单。
     */
    AccountFreezeEntity findFreeze(@Param("freezeNo") String freezeNo);

    /**
     * 新增冻结单。
     */
    int insertFreeze(AccountFreezeEntity entity);

    /**
     * 更新冻结单状态。
     */
    int updateFreeze(@Param("freezeNo") String freezeNo,
                     @Param("freezeStatus") String freezeStatus,
                     @Param("freezeReason") String freezeReason,
                     @Param("unfrozenAt") String unfrozenAt);

    /**
     * 查询调账单列表。
     */
    List<AccountAdjustmentEntity> findAdjustments();

    /**
     * 查询调账单。
     */
    AccountAdjustmentEntity findAdjustment(@Param("adjustNo") String adjustNo);

    /**
     * 新增调账单。
     */
    int insertAdjustment(AccountAdjustmentEntity entity);

    /**
     * 更新调账审批结果。
     */
    int updateAdjustmentApproval(@Param("adjustNo") String adjustNo,
                                 @Param("adjustStatus") String adjustStatus,
                                 @Param("approvedBy") String approvedBy,
                                 @Param("approvedAt") String approvedAt);

    /**
     * 查询事件列表。
     */
    List<AccountEventEntity> findEvents();

    /**
     * 新增事件。
     */
    int insertEvent(AccountEventEntity entity);
}
