package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletAccountDTO;
import com.abc123.wallet.dto.WalletAccountDetailDTO;
import com.abc123.wallet.dto.WalletLedgerDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletLedgerEntity;
import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.service.WalletService;
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
}
