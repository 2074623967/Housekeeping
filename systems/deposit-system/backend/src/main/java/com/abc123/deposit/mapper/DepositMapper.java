package com.abc123.deposit.mapper;

import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 保证金 MyBatis Mapper。
 */
@Mapper
public interface DepositMapper {

    int insertAccount(@Param("accountNo") String accountNo, @Param("ownerId") String ownerId,
                      @Param("ownerType") String ownerType, @Param("requiredAmount") BigDecimal requiredAmount);

    DepositAccountDTO findAccount(@Param("accountNo") String accountNo);

    DepositAccountDTO findAccountByOwner(@Param("ownerId") String ownerId, @Param("ownerType") String ownerType);

    List<DepositAccountDTO> findAccounts();

    int updateBalance(@Param("accountNo") String accountNo,
                      @Param("expectedBalance") BigDecimal expectedBalance,
                      @Param("expectedFrozenAmount") BigDecimal expectedFrozenAmount,
                      @Param("balance") BigDecimal balance,
                      @Param("frozenAmount") BigDecimal frozenAmount);

    int insertFlow(@Param("accountNo") String accountNo, @Param("flowType") String flowType,
                   @Param("amount") BigDecimal amount, @Param("beforeBalance") BigDecimal beforeBalance,
                   @Param("afterBalance") BigDecimal afterBalance,
                   @Param("beforeFrozenAmount") BigDecimal beforeFrozenAmount,
                   @Param("afterFrozenAmount") BigDecimal afterFrozenAmount,
                   @Param("referenceNo") String referenceNo, @Param("remark") String remark);

    long countFlowReference(@Param("accountNo") String accountNo, @Param("flowType") String flowType,
                            @Param("referenceNo") String referenceNo);

    List<DepositFlowDTO> findFlows(@Param("accountNo") String accountNo);
}
