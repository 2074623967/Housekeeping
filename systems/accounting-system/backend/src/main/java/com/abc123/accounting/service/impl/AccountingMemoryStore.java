package com.abc123.accounting.service.impl;

import com.abc123.accounting.entity.AccountAdjustmentEntity;
import com.abc123.accounting.entity.AccountBalanceEntity;
import com.abc123.accounting.entity.AccountEntity;
import com.abc123.accounting.entity.AccountEventEntity;
import com.abc123.accounting.entity.AccountFreezeEntity;
import com.abc123.accounting.entity.AccountLedgerEntity;
import com.abc123.accounting.entity.AccountSubjectEntity;
import com.abc123.accounting.mapper.AccountingDataMapper;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账务系统持久化数据仓，用于第二阶段第一版正式版联调。
 */
@Component
public class AccountingMemoryStore {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("0.00");

    private final AtomicLong subjectSeq = new AtomicLong(1003L);
    private final AtomicLong accountSeq = new AtomicLong(10003L);
    private final AtomicLong ledgerSeq = new AtomicLong(20004L);
    private final AtomicLong freezeSeq = new AtomicLong(30001L);
    private final AtomicLong adjustSeq = new AtomicLong(40001L);
    private final AtomicLong eventSeq = new AtomicLong(50001L);
    private final AccountingDataMapper accountingDataMapper;

    public AccountingMemoryStore(AccountingDataMapper accountingDataMapper) {
        this.accountingDataMapper = accountingDataMapper;
    }

    @PostConstruct
    @Transactional
    public void initDemoData() {
        if (accountingDataMapper.countSubjects() > 0) {
            return;
        }
        AccountSubjectEntity customer = createSubject("CUSTOMER", "张女士", "张女士", "启用");
        AccountSubjectEntity worker = createSubject("WORKER", "李阿姨", "李阿姨", "启用");
        AccountSubjectEntity platform = createSubject("PLATFORM", "家政平台主体", "财务中心", "启用");

        AccountEntity customerAccount = createAccount(customer, "用户可用账户", "CNY", "正常");
        AccountEntity workerAccount = createAccount(worker, "服务者应收账户", "CNY", "正常");
        AccountEntity platformAccount = createAccount(platform, "平台手续费账户", "CNY", "正常");

        credit(customerAccount.getAccountNo(), "PAYMENT_SUCCESS", "PAY202607200001", new BigDecimal("168.00"), "系统入账");
        createFreeze(customerAccount.getAccountNo(), "ORD202607200001", "履约担保", "服务履约担保", new BigDecimal("68.00"), "运营冻结");
        credit(workerAccount.getAccountNo(), "CLEARING_RESULT", "CLR202607200001", new BigDecimal("120.00"), "清分入账");
        credit(platformAccount.getAccountNo(), "SERVICE_FEE", "FEE202607200001", new BigDecimal("12.00"), "平台服务费入账");

        createAdjustment(workerAccount.getAccountNo(), "贷方", new BigDecimal("8.00"), "履约奖励补贴", "财务小李");
        recordEvent("PAYMENT_SUCCESS", "PAY202607200001", "消费支付成功后用户入账",
                "{\"paymentOrderId\":\"PAY202607200001\",\"amount\":168.00}");
    }

    public List<AccountSubjectEntity> subjects() {
        return accountingDataMapper.findSubjects();
    }

    @Transactional
    public AccountSubjectEntity createSubject(String subjectType, String subjectName, String ownerName, String status) {
        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setSubjectId(nextNo("SUB", subjectSeq));
        entity.setSubjectType(subjectType);
        entity.setSubjectName(subjectName);
        entity.setOwnerName(ownerName);
        entity.setStatus(status);
        entity.setCreatedAt(now());
        accountingDataMapper.insertSubject(entity);
        return entity;
    }

    public AccountSubjectEntity findSubject(String subjectId) {
        return accountingDataMapper.findSubject(subjectId);
    }

    public List<AccountEntity> accounts() {
        return accountingDataMapper.findAccounts();
    }

    @Transactional
    public AccountEntity createAccount(AccountSubjectEntity subject, String accountType, String currency, String status) {
        AccountEntity entity = new AccountEntity();
        entity.setAccountNo(nextNo("ACT", accountSeq));
        entity.setSubjectId(subject.getSubjectId());
        entity.setSubjectName(subject.getSubjectName());
        entity.setAccountType(accountType);
        entity.setCurrency(currency);
        entity.setAccountStatus(status);
        entity.setCreatedAt(now());
        entity.setLastChangeAt(entity.getCreatedAt());
        accountingDataMapper.insertAccount(entity);

        AccountBalanceEntity balanceEntity = new AccountBalanceEntity();
        balanceEntity.setAccountNo(entity.getAccountNo());
        balanceEntity.setAvailableAmount(BigDecimal.ZERO);
        balanceEntity.setFrozenAmount(BigDecimal.ZERO);
        balanceEntity.setInTransitAmount(BigDecimal.ZERO);
        balanceEntity.setUpdatedAt(now());
        accountingDataMapper.insertBalance(balanceEntity);
        return entity;
    }

    public AccountEntity findAccount(String accountNo) {
        return accountingDataMapper.findAccount(accountNo);
    }

    public AccountBalanceEntity findBalance(String accountNo) {
        return accountingDataMapper.findBalance(accountNo);
    }

    public List<AccountLedgerEntity> ledgers() {
        List<AccountLedgerEntity> copied = new ArrayList<>(accountingDataMapper.findLedgers());
        copied.sort(Comparator.comparing(AccountLedgerEntity::getCreatedAt).reversed());
        return copied;
    }

    @Transactional
    public AccountLedgerEntity credit(String accountNo, String bizType, String bizNo, BigDecimal amount, String operatorName) {
        AccountBalanceEntity balance = findBalance(accountNo);
        BigDecimal before = balance.getAvailableAmount();
        BigDecimal after = before.add(amount);
        accountingDataMapper.updateBalance(accountNo, after, balance.getFrozenAmount(), balance.getInTransitAmount(), now());
        updateAccountChangeTime(accountNo);
        AccountLedgerEntity entity = buildLedger(accountNo, bizType, bizNo, "贷方", amount, before, after, operatorName);
        accountingDataMapper.insertLedger(entity);
        return entity;
    }

    @Transactional
    public AccountLedgerEntity freeze(String accountNo, String bizNo, String freezeReason, BigDecimal amount, String operatorName) {
        return freezeBalanceOnly(accountNo, bizNo, freezeReason, amount, operatorName);
    }

    public List<AccountFreezeEntity> freezes() {
        List<AccountFreezeEntity> copied = new ArrayList<>(accountingDataMapper.findFreezes());
        copied.sort(Comparator.comparing(AccountFreezeEntity::getCreatedAt).reversed());
        return copied;
    }

    @Transactional
    public AccountFreezeEntity createFreeze(String accountNo, String bizNo, String freezeType, String freezeReason,
            BigDecimal freezeAmount, String operatorName) {
        AccountFreezeEntity entity = new AccountFreezeEntity();
        entity.setFreezeNo(nextNo("FRZ", freezeSeq));
        entity.setAccountNo(accountNo);
        entity.setBizNo(bizNo);
        entity.setFreezeType(freezeType);
        entity.setFreezeReason(freezeReason);
        entity.setFreezeAmount(freezeAmount);
        entity.setFreezeStatus("冻结中");
        entity.setOperatorName(operatorName);
        entity.setCreatedAt(now());
        accountingDataMapper.insertFreeze(entity);
        freezeBalanceOnly(accountNo, bizNo, freezeReason, freezeAmount, operatorName);
        return entity;
    }

    private AccountLedgerEntity freezeBalanceOnly(String accountNo, String bizNo, String freezeReason, BigDecimal amount,
            String operatorName) {
        AccountBalanceEntity balance = findBalance(accountNo);
        BigDecimal before = balance.getAvailableAmount();
        BigDecimal after = before.subtract(amount);
        accountingDataMapper.updateBalance(accountNo, after, balance.getFrozenAmount().add(amount), balance.getInTransitAmount(), now());
        updateAccountChangeTime(accountNo);

        AccountLedgerEntity entity = buildLedger(accountNo, "BALANCE_FREEZE", bizNo, "借方", amount, before, after, operatorName);
        accountingDataMapper.insertLedger(entity);
        return entity;
    }

    @Transactional
    public AccountFreezeEntity unfreeze(String freezeNo, String operatorName, String reason) {
        AccountFreezeEntity freezeEntity = accountingDataMapper.findFreeze(freezeNo);
        if (freezeEntity == null || "已解冻".equals(freezeEntity.getFreezeStatus())) {
            return freezeEntity;
        }
        unfreezeBalanceOnly(
                freezeEntity.getAccountNo(),
                freezeEntity.getBizNo(),
                freezeEntity.getFreezeAmount(),
                operatorName);

        accountingDataMapper.updateFreeze(
                freezeNo,
                "已解冻",
                freezeEntity.getFreezeReason() + " / " + reason,
                now());
        freezeEntity.setFreezeStatus("已解冻");
        freezeEntity.setUnfrozenAt(now());
        freezeEntity.setFreezeReason(freezeEntity.getFreezeReason() + " / " + reason);
        return freezeEntity;
    }

    @Transactional
    public AccountLedgerEntity unfreezeBalanceOnly(String accountNo, String bizNo, BigDecimal amount, String operatorName) {
        AccountBalanceEntity balance = findBalance(accountNo);
        BigDecimal before = balance.getAvailableAmount();
        BigDecimal after = before.add(amount);
        accountingDataMapper.updateBalance(accountNo, after, balance.getFrozenAmount().subtract(amount), balance.getInTransitAmount(), now());
        updateAccountChangeTime(accountNo);

        AccountLedgerEntity entity = buildLedger(accountNo, "BALANCE_UNFREEZE", bizNo, "贷方", amount, before, after, operatorName);
        accountingDataMapper.insertLedger(entity);
        return entity;
    }

    public List<AccountAdjustmentEntity> adjustments() {
        List<AccountAdjustmentEntity> copied = new ArrayList<>(accountingDataMapper.findAdjustments());
        copied.sort(Comparator.comparing(AccountAdjustmentEntity::getCreatedAt).reversed());
        return copied;
    }

    @Transactional
    public AccountAdjustmentEntity createAdjustment(String accountNo, String adjustDirection, BigDecimal adjustAmount,
            String adjustReason, String createdBy) {
        AccountAdjustmentEntity entity = new AccountAdjustmentEntity();
        entity.setAdjustNo(nextNo("ADJ", adjustSeq));
        entity.setAccountNo(accountNo);
        entity.setAdjustDirection(adjustDirection);
        entity.setAdjustAmount(adjustAmount);
        entity.setAdjustReason(adjustReason);
        entity.setAdjustStatus("待审批");
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(now());
        accountingDataMapper.insertAdjustment(entity);
        return entity;
    }

    @Transactional
    public AccountAdjustmentEntity approveAdjustment(String adjustNo, String approvedBy) {
        AccountAdjustmentEntity entity = accountingDataMapper.findAdjustment(adjustNo);
        if (entity == null || "已生效".equals(entity.getAdjustStatus())) {
            return entity;
        }
        accountingDataMapper.updateAdjustmentApproval(adjustNo, "已生效", approvedBy, now());
        BigDecimal before = findBalance(entity.getAccountNo()).getAvailableAmount();
        BigDecimal amount = entity.getAdjustAmount();
        BigDecimal after = "贷方".equals(entity.getAdjustDirection()) ? before.add(amount) : before.subtract(amount);
        AccountBalanceEntity balance = findBalance(entity.getAccountNo());
        accountingDataMapper.updateBalance(entity.getAccountNo(), after, balance.getFrozenAmount(), balance.getInTransitAmount(), now());
        updateAccountChangeTime(entity.getAccountNo());
        accountingDataMapper.insertLedger(buildLedger(
                entity.getAccountNo(),
                "ACCOUNT_ADJUSTMENT",
                entity.getAdjustNo(),
                entity.getAdjustDirection(),
                amount,
                before,
                after,
                approvedBy));
        entity.setAdjustStatus("已生效");
        entity.setApprovedBy(approvedBy);
        entity.setApprovedAt(now());
        return entity;
    }

    public List<AccountEventEntity> events() {
        return accountingDataMapper.findEvents();
    }

    @Transactional
    public AccountEventEntity recordEvent(String eventType, String bizNo, String summary, String payload) {
        AccountEventEntity entity = new AccountEventEntity();
        entity.setEventNo(nextNo("EVT", eventSeq));
        entity.setEventType(eventType);
        entity.setBizNo(bizNo);
        entity.setSummary(summary);
        entity.setPayload(payload);
        entity.setEventStatus("已消费");
        entity.setCreatedAt(now());
        accountingDataMapper.insertEvent(entity);
        return entity;
    }

    public static String formatAmount(BigDecimal amount) {
        return "¥" + AMOUNT_FORMAT.format(amount);
    }

    private AccountLedgerEntity buildLedger(String accountNo, String bizType, String bizNo, String direction,
            BigDecimal amount, BigDecimal beforeBalance, BigDecimal afterBalance, String operatorName) {
        AccountLedgerEntity entity = new AccountLedgerEntity();
        entity.setLedgerNo(nextNo("LDG", ledgerSeq));
        entity.setAccountNo(accountNo);
        entity.setBizType(bizType);
        entity.setBizNo(bizNo);
        entity.setDirection(direction);
        entity.setAmount(amount);
        entity.setBeforeBalance(beforeBalance);
        entity.setAfterBalance(afterBalance);
        entity.setLedgerStatus("已记账");
        entity.setOperatorName(operatorName);
        entity.setCreatedAt(now());
        return entity;
    }

    private void updateAccountChangeTime(String accountNo) {
        accountingDataMapper.updateAccountChangeTime(accountNo, now());
    }

    private String nextNo(String prefix, AtomicLong sequence) {
        return prefix + sequence.incrementAndGet();
    }

    private String now() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}
