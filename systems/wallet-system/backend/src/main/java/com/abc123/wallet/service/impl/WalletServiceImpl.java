package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletAccountDTO;
import com.abc123.wallet.dto.WalletAccountDetailDTO;
import com.abc123.wallet.dto.WalletBalanceSnapshotDTO;
import com.abc123.wallet.dto.WalletLedgerDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletLedgerEntity;
import com.abc123.wallet.mapper.WalletMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.abc123.wallet.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WalletServiceImpl implements WalletService {

    private final WalletMapper walletMapper;

    public WalletServiceImpl(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    @Override
    public List<WalletAccountDTO> listAccounts() {
        return walletMapper.findAccounts().stream().map(this::toAccountDTO).collect(Collectors.toList());
    }

    @Override
    public List<WalletBalanceSnapshotDTO> listBalanceSnapshots() {
        return walletMapper.findAccounts().stream().map(this::toBalanceSnapshotDTO).collect(Collectors.toList());
    }

    @Override
    public WalletAccountDetailDTO getDetail(String accountNo) {
        WalletAccountEntity account = walletMapper.findAccountByNo(accountNo);
        if (account == null) {
            return null;
        }
        WalletAccountDetailDTO detail = new WalletAccountDetailDTO();
        detail.setAccount(toAccountDTO(account));
        detail.setLedgers(walletMapper.findLedgersByAccountNo(accountNo).stream().map(this::toLedgerDTO).collect(Collectors.toList()));
        return detail;
    }

    @Override
    public List<WalletLedgerDTO> listLedgers(String accountNo, String bizType, String direction) {
        return walletMapper.findAllLedgers(accountNo, bizType, direction)
                .stream()
                .map(this::toLedgerDTO)
                .collect(Collectors.toList());
    }

    @Override
    public byte[] exportLedgers(String accountNo, String bizType, String direction) {
        List<WalletLedgerDTO> records = listLedgers(accountNo, bizType, direction);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("流水号,钱包账户号,业务类型,业务单号,金额,方向,创建时间\n");
        for (WalletLedgerDTO item : records) {
            builder.append(csv(item.getLedgerNo())).append(',')
                    .append(csv(item.getAccountNo())).append(',')
                    .append(csv(item.getBizType())).append(',')
                    .append(csv(item.getBizNo())).append(',')
                    .append(csv(item.getAmount())).append(',')
                    .append(csv(item.getDirection())).append(',')
                    .append(csv(item.getCreatedAt())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private WalletAccountDTO toAccountDTO(WalletAccountEntity entity) {
        WalletAccountDTO dto = new WalletAccountDTO();
        dto.setAccountNo(entity.getAccountNo());
        dto.setOwnerName(entity.getOwnerName());
        dto.setWalletType(entity.getWalletType());
        dto.setStatus(entity.getStatus());
        dto.setAvailableAmount(entity.getAvailableAmount());
        dto.setFrozenAmount(entity.getFrozenAmount());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private WalletLedgerDTO toLedgerDTO(WalletLedgerEntity entity) {
        WalletLedgerDTO dto = new WalletLedgerDTO();
        dto.setLedgerNo(entity.getLedgerNo());
        dto.setAccountNo(entity.getAccountNo());
        dto.setBizType(entity.getBizType());
        dto.setBizNo(entity.getBizNo());
        dto.setAmount(entity.getAmount());
        dto.setDirection(entity.getDirection());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private WalletBalanceSnapshotDTO toBalanceSnapshotDTO(WalletAccountEntity entity) {
        BigDecimal availableAmount = decimal(entity.getAvailableAmount());
        BigDecimal frozenAmount = decimal(entity.getFrozenAmount());
        BigDecimal totalAmount = availableAmount.add(frozenAmount);
        WalletBalanceSnapshotDTO dto = new WalletBalanceSnapshotDTO();
        dto.setAccountNo(entity.getAccountNo());
        dto.setOwnerName(entity.getOwnerName());
        dto.setWalletType(entity.getWalletType());
        dto.setStatus(entity.getStatus());
        dto.setAvailableAmount(formatAmount(availableAmount));
        dto.setFrozenAmount(formatAmount(frozenAmount));
        dto.setTotalAmount(formatAmount(totalAmount));
        dto.setFrozenRate(formatRate(frozenAmount, totalAmount));
        dto.setFocusTag(resolveFocusTag(entity.getStatus(), frozenAmount, totalAmount));
        dto.setSnapshotAt(entity.getCreatedAt());
        return dto;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatRate(BigDecimal frozenAmount, BigDecimal totalAmount) {
        if (BigDecimal.ZERO.compareTo(totalAmount) == 0) {
            return "0.00%";
        }
        return frozenAmount.multiply(new BigDecimal("100"))
                .divide(totalAmount, 2, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }

    private String resolveFocusTag(String status, BigDecimal frozenAmount, BigDecimal totalAmount) {
        if ("FROZEN".equals(status)) {
            return "账户冻结";
        }
        if (BigDecimal.ZERO.compareTo(totalAmount) > 0) {
            return "资金异常";
        }
        if (BigDecimal.ZERO.compareTo(totalAmount) == 0) {
            return "零余额";
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0
                && frozenAmount.divide(totalAmount, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.50")) >= 0) {
            return "冻结占比高";
        }
        return "正常关注";
    }
}
