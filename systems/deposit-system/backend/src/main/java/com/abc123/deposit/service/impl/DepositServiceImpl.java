package com.abc123.deposit.service.impl;

import com.abc123.deposit.common.BusinessException;
import com.abc123.deposit.dao.DepositDao;
import com.abc123.deposit.dto.DebtOffsetRequestDTO;
import com.abc123.deposit.dto.DepositAccountCreateRequestDTO;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositActionRequestDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.service.DepositService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 保证金业务编排实现。
 *
 * <p>余额、冻结和可用余额在同一事务内变更，所有动作均追加不可变流水。</p>
 */
@Service
public class DepositServiceImpl implements DepositService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter ACCOUNT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final DepositDao dao;

    public DepositServiceImpl(DepositDao dao) {
        this.dao = dao;
    }

    @Override
    @Transactional
    public DepositAccountDTO openAccount(DepositAccountCreateRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getOwnerId())
                || !StringUtils.hasText(request.getOwnerType())) {
            throw new BusinessException("主体编号和主体类型不能为空");
        }
        String ownerId = request.getOwnerId().trim();
        String ownerType = request.getOwnerType().trim();
        if (dao.findAccountByOwner(ownerId, ownerType) != null) {
            throw new BusinessException("该主体已存在保证金账户");
        }
        BigDecimal required = positiveOrZero(request.getRequiredAmount());
        String accountNo = "DEP" + LocalDateTime.now().format(ACCOUNT_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        dao.insertAccount(accountNo, ownerId, ownerType, required);
        return requiredAccount(accountNo);
    }

    @Override
    public List<DepositAccountDTO> accounts() {
        return dao.findAccounts();
    }

    @Override
    @Transactional
    public DepositAccountDTO collect(DepositActionRequestDTO request) {
        return change(request, "COLLECT", true, false);
    }

    @Override
    @Transactional
    public DepositAccountDTO freeze(DepositActionRequestDTO request) {
        DepositAccountDTO account = account(request);
        BigDecimal amount = positive(request.getAmount());
        if (account.getAvailableAmount().compareTo(amount) < 0) {
            throw new BusinessException("可用保证金不足，不能冻结");
        }
        BigDecimal beforeFrozen = account.getFrozenAmount();
        BigDecimal afterFrozen = beforeFrozen.add(amount);
        update(account, account.getBalance(), beforeFrozen, account.getBalance(), afterFrozen);
        return record(account, "FREEZE", amount, account.getBalance(), account.getBalance(),
                beforeFrozen, afterFrozen, request.getReferenceNo(), request.getRemark());
    }

    @Override
    @Transactional
    public DepositAccountDTO unfreeze(DepositActionRequestDTO request) {
        DepositAccountDTO account = account(request);
        BigDecimal amount = positive(request.getAmount());
        if (account.getFrozenAmount().compareTo(amount) < 0) {
            throw new BusinessException("冻结保证金不足，不能解冻");
        }
        BigDecimal beforeFrozen = account.getFrozenAmount();
        BigDecimal afterFrozen = beforeFrozen.subtract(amount);
        update(account, account.getBalance(), beforeFrozen, account.getBalance(), afterFrozen);
        return record(account, "UNFREEZE", amount, account.getBalance(), account.getBalance(),
                beforeFrozen, afterFrozen, request.getReferenceNo(), request.getRemark());
    }

    @Override
    @Transactional
    public DepositAccountDTO deduct(DepositActionRequestDTO request) {
        DepositAccountDTO account = account(request);
        BigDecimal amount = positive(request.getAmount());
        if (account.getAvailableAmount().compareTo(amount) < 0) {
            throw new BusinessException("可用保证金不足，不能扣罚");
        }
        BigDecimal after = account.getBalance().subtract(amount);
        update(account, account.getBalance(), account.getFrozenAmount(), after, account.getFrozenAmount());
        return record(account, "DEDUCT", amount, account.getBalance(), after,
                account.getFrozenAmount(), account.getFrozenAmount(),
                request.getReferenceNo(), request.getRemark());
    }

    @Override
    @Transactional
    public DepositAccountDTO refund(DepositActionRequestDTO request) {
        return change(request, "REFUND", true, false);
    }

    @Override
    @Transactional
    public DepositAccountDTO offsetDebt(DebtOffsetRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getDebtNo())) {
            throw new BusinessException("欠款单号不能为空");
        }
        DepositActionRequestDTO action = new DepositActionRequestDTO();
        action.setAccountNo(request.getAccountNo());
        action.setAmount(request.getDebtAmount());
        action.setReferenceNo(request.getDebtNo());
        action.setRemark(request.getRemark());
        return deductWithFlowType(action, "OFFSET_DEBT");
    }

    @Override
    public List<DepositFlowDTO> flows(String accountNo) {
        return dao.findFlows(required(accountNo, "保证金账户号不能为空"));
    }

    private DepositAccountDTO change(DepositActionRequestDTO request, String flowType,
                                     boolean addBalance, boolean useFrozen) {
        DepositAccountDTO account = account(request);
        ensureNotDuplicated(account.getAccountNo(), flowType, request.getReferenceNo());
        BigDecimal amount = positive(request.getAmount());
        BigDecimal before = account.getBalance();
        BigDecimal after = addBalance ? before.add(amount) : before.subtract(amount);
        if (!addBalance && account.getAvailableAmount().compareTo(amount) < 0) {
            throw new BusinessException("可用保证金不足");
        }
        update(account, before, account.getFrozenAmount(), after, account.getFrozenAmount());
        return record(account, flowType, amount, before, after,
                account.getFrozenAmount(), account.getFrozenAmount(),
                request.getReferenceNo(), request.getRemark());
    }

    private DepositAccountDTO deductWithFlowType(DepositActionRequestDTO request, String flowType) {
        DepositAccountDTO account = account(request);
        ensureNotDuplicated(account.getAccountNo(), flowType, request.getReferenceNo());
        BigDecimal amount = positive(request.getAmount());
        if (account.getAvailableAmount().compareTo(amount) < 0) {
            throw new BusinessException("可用保证金不足，不能抵扣欠款");
        }
        BigDecimal before = account.getBalance();
        BigDecimal after = before.subtract(amount);
        update(account, before, account.getFrozenAmount(), after, account.getFrozenAmount());
        return record(account, flowType, amount, before, after,
                account.getFrozenAmount(), account.getFrozenAmount(),
                request.getReferenceNo(), request.getRemark());
    }

    private DepositAccountDTO record(DepositAccountDTO account, String flowType, BigDecimal amount,
                                     BigDecimal before, BigDecimal after, BigDecimal beforeFrozenAmount,
                                     BigDecimal afterFrozenAmount, String referenceNo, String remark) {
        dao.insertFlow(account.getAccountNo(), flowType, amount, before, after,
                beforeFrozenAmount, afterFrozenAmount, referenceNo, remark);
        return requiredAccount(account.getAccountNo());
    }

    private void update(DepositAccountDTO account, BigDecimal expectedBalance, BigDecimal expectedFrozen,
                        BigDecimal balance, BigDecimal frozen) {
        if (dao.updateBalance(account.getAccountNo(), expectedBalance, expectedFrozen, balance, frozen) != 1) {
            throw new BusinessException("保证金账户状态已变化，请重试");
        }
    }

    private DepositAccountDTO account(DepositActionRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getAccountNo())) {
            throw new BusinessException("保证金账户号不能为空");
        }
        return requiredAccount(request.getAccountNo());
    }

    private DepositAccountDTO requiredAccount(String accountNo) {
        DepositAccountDTO account = dao.findAccount(required(accountNo, "保证金账户号不能为空"));
        if (account == null) throw new BusinessException("保证金账户不存在");
        return account;
    }

    private BigDecimal positive(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) <= 0) throw new BusinessException("操作金额必须大于0");
        return value;
    }

    private BigDecimal positiveOrZero(BigDecimal value) {
        return value == null ? ZERO : value.max(ZERO);
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) throw new BusinessException(message);
        return value.trim();
    }

    private void ensureNotDuplicated(String accountNo, String flowType, String referenceNo) {
        if (!StringUtils.hasText(referenceNo)) {
            return;
        }
        if (dao.existsFlowReference(accountNo, flowType, referenceNo.trim())) {
            throw new BusinessException("该业务关联号已处理，请勿重复提交");
        }
    }
}
