package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletAccountDTO;
import com.abc123.wallet.dto.WalletLedgerDTO;
import com.abc123.wallet.dto.WalletMarketingFundDashboardDTO;
import com.abc123.wallet.dto.WalletRedPacketDTO;
import com.abc123.wallet.dto.WalletRiskEventDTO;
import com.abc123.wallet.service.WalletMarketingFundService;
import com.abc123.wallet.service.WalletRedPacketService;
import com.abc123.wallet.service.WalletRiskEventService;
import com.abc123.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WalletMarketingFundServiceImpl implements WalletMarketingFundService {

    private final WalletService walletService;
    private final WalletRedPacketService walletRedPacketService;
    private final WalletRiskEventService walletRiskEventService;

    public WalletMarketingFundServiceImpl(WalletService walletService,
            WalletRedPacketService walletRedPacketService,
            WalletRiskEventService walletRiskEventService) {
        this.walletService = walletService;
        this.walletRedPacketService = walletRedPacketService;
        this.walletRiskEventService = walletRiskEventService;
    }

    @Override
    public WalletMarketingFundDashboardDTO getDashboard() {
        WalletAccountDTO marketingAccount = findMarketingAccount();
        List<WalletRedPacketDTO> redPackets = walletRedPacketService.listRedPackets();
        List<WalletRiskEventDTO> riskEvents = walletRiskEventService.listRiskEvents();
        List<WalletLedgerDTO> allLedgers = walletService.listLedgers(marketingAccount.getAccountNo(), "", "OUT");

        WalletMarketingFundDashboardDTO dto = new WalletMarketingFundDashboardDTO();
        dto.setAccountNo(marketingAccount.getAccountNo());
        dto.setOwnerName(marketingAccount.getOwnerName());
        dto.setAvailableAmount(marketingAccount.getAvailableAmount());
        dto.setFrozenAmount(marketingAccount.getFrozenAmount());
        dto.setRedPackets(redPackets);
        dto.setRiskEvents(riskEvents);
        dto.setOutLedgers(filterMarketingLedgers(allLedgers));

        BigDecimal totalRedPacketAmount = BigDecimal.ZERO;
        BigDecimal pendingApprovalAmount = BigDecimal.ZERO;
        BigDecimal issuedAmount = BigDecimal.ZERO;
        BigDecimal rejectedAmount = BigDecimal.ZERO;
        int pendingRiskCount = 0;
        int approvedRiskCount = 0;
        int rejectedRiskCount = 0;

        for (WalletRedPacketDTO redPacket : redPackets) {
            BigDecimal amount = new BigDecimal(redPacket.getTotalAmount());
            totalRedPacketAmount = totalRedPacketAmount.add(amount);
            if ("PENDING_APPROVAL".equals(redPacket.getStatus())) {
                pendingApprovalAmount = pendingApprovalAmount.add(amount);
            } else if ("ISSUED".equals(redPacket.getStatus())) {
                issuedAmount = issuedAmount.add(amount);
            } else if ("REJECTED".equals(redPacket.getStatus())) {
                rejectedAmount = rejectedAmount.add(amount);
            }
        }

        for (WalletRiskEventDTO riskEvent : riskEvents) {
            if ("PENDING".equals(riskEvent.getStatus())) {
                pendingRiskCount++;
            } else if ("APPROVED".equals(riskEvent.getStatus())) {
                approvedRiskCount++;
            } else if ("REJECTED".equals(riskEvent.getStatus())) {
                rejectedRiskCount++;
            }
        }

        dto.setTotalRedPacketAmount(totalRedPacketAmount.toPlainString());
        dto.setPendingApprovalAmount(pendingApprovalAmount.toPlainString());
        dto.setIssuedAmount(issuedAmount.toPlainString());
        dto.setRejectedAmount(rejectedAmount.toPlainString());
        dto.setPendingRiskCount(pendingRiskCount);
        dto.setApprovedRiskCount(approvedRiskCount);
        dto.setRejectedRiskCount(rejectedRiskCount);
        return dto;
    }

    private WalletAccountDTO findMarketingAccount() {
        for (WalletAccountDTO account : walletService.listAccounts()) {
            if ("ENTERPRISE".equals(account.getWalletType())) {
                return account;
            }
        }
        throw new IllegalArgumentException("营销资金账户不存在");
    }

    private List<WalletLedgerDTO> filterMarketingLedgers(List<WalletLedgerDTO> ledgers) {
        List<WalletLedgerDTO> results = new ArrayList<>();
        for (WalletLedgerDTO ledger : ledgers) {
            if ("RED_PACKET_OUT".equals(ledger.getBizType()) || "TRANSFER_OUT".equals(ledger.getBizType())) {
                results.add(ledger);
            }
        }
        return results;
    }
}
