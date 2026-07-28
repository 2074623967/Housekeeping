package com.abc123.wallet.mapper;

import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletBalancePaymentOrderEntity;
import com.abc123.wallet.entity.WalletLedgerEntity;
import com.abc123.wallet.entity.WalletRedPacketEntity;
import java.util.List;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

public interface WalletMapper {

    List<WalletAccountEntity> findAccounts();

    WalletAccountEntity findAccountByNo(@Param("accountNo") String accountNo);

    List<WalletLedgerEntity> findLedgersByAccountNo(@Param("accountNo") String accountNo);

    List<WalletLedgerEntity> findAllLedgers(@Param("accountNo") String accountNo,
            @Param("bizType") String bizType,
            @Param("direction") String direction);

    int updateAccountAmount(@Param("accountNo") String accountNo, @Param("amount") BigDecimal amount);

    int insertRechargeOrder(@Param("rechargeNo") String rechargeNo,
            @Param("accountNo") String accountNo,
            @Param("bizNo") String bizNo,
            @Param("amount") BigDecimal amount,
            @Param("status") String status);

    int insertLedger(@Param("ledgerNo") String ledgerNo,
            @Param("accountNo") String accountNo,
            @Param("bizType") String bizType,
            @Param("bizNo") String bizNo,
            @Param("amount") BigDecimal amount,
            @Param("direction") String direction);

    com.abc123.wallet.entity.WalletRechargeOrderEntity findRechargeOrderByNo(@Param("rechargeNo") String rechargeNo);

    int insertWithdrawOrder(@Param("withdrawNo") String withdrawNo,
            @Param("accountNo") String accountNo,
            @Param("bizNo") String bizNo,
            @Param("amount") BigDecimal amount,
            @Param("status") String status);

    com.abc123.wallet.entity.WalletWithdrawOrderEntity findWithdrawOrderByNo(@Param("withdrawNo") String withdrawNo);

    int insertTransferOrder(@Param("transferNo") String transferNo,
            @Param("sourceAccountNo") String sourceAccountNo,
            @Param("targetAccountNo") String targetAccountNo,
            @Param("bizNo") String bizNo,
            @Param("amount") BigDecimal amount,
            @Param("status") String status);

    com.abc123.wallet.entity.WalletTransferOrderEntity findTransferOrderByNo(@Param("transferNo") String transferNo);

    int insertBalancePaymentOrder(@Param("balancePaymentNo") String balancePaymentNo,
            @Param("accountNo") String accountNo,
            @Param("bizNo") String bizNo,
            @Param("amount") BigDecimal amount,
            @Param("status") String status);

    WalletBalancePaymentOrderEntity findBalancePaymentOrderByNo(@Param("balancePaymentNo") String balancePaymentNo);

    List<WalletRedPacketEntity> findRedPackets();

    int insertRedPacket(@Param("redPacketNo") String redPacketNo,
            @Param("accountNo") String accountNo,
            @Param("campaignName") String campaignName,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("packetCount") Integer packetCount,
            @Param("status") String status);
}
