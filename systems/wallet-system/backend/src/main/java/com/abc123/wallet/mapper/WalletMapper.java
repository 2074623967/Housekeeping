package com.abc123.wallet.mapper;

import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletLedgerEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WalletMapper {

    List<WalletAccountEntity> findAccounts();

    WalletAccountEntity findAccountByNo(@Param("accountNo") String accountNo);

    List<WalletLedgerEntity> findLedgersByAccountNo(@Param("accountNo") String accountNo);
}
