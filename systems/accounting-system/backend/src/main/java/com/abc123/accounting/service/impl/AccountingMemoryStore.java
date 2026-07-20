package com.abc123.accounting.service.impl;

import com.abc123.accounting.entity.AccountAdjustmentEntity;
import com.abc123.accounting.entity.AccountBalanceEntity;
import com.abc123.accounting.entity.AccountEntity;
import com.abc123.accounting.entity.AccountEventEntity;
import com.abc123.accounting.entity.AccountFreezeEntity;
import com.abc123.accounting.entity.AccountLedgerEntity;
import com.abc123.accounting.entity.AccountSubjectEntity;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 账务系统内存态数据仓，用于第二阶段第一版骨架联调。
 */
@Component
public class AccountingMemoryStore {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("0.00");

    private final AtomicLong subjectSeq = new AtomicLong(1000L);
    private final AtomicLong accountSeq = new AtomicLong(10000L);
    private final AtomicLong ledgerSeq = new AtomicLong(20000L);
    private final AtomicLong freezeSeq = new AtomicLong(30000L);
    private final AtomicLong adjustSeq = new AtomicLong(40000L);
    private final AtomicLong eventSeq = new AtomicLong(50000L);

    private final Map<String, AccountSubjectEntity> subjects = new LinkedHashMap<>();
    private final Map<String, AccountEntity> accounts = new LinkedHashMap<>();
    private final Map<String, AccountBalanceEntity> balances = new LinkedHashMap<>();
    private final Map<String, AccountFreezeEntity> freezes = new LinkedHashMap<>();
    private final Map<String, AccountAdjustmentEntity> adjustments = new LinkedHashMap<>();
    private final List<AccountLedgerEntity> ledgers = new ArrayList<>();
    private final List<AccountEventEntity> events = new ArrayList<>();

    @PostConstruct
    public void initDemoData() {
        AccountSubjectEntity customer = createSubject("CUSTOMER", "张女士", "张女士", "启用");
        AccountSubjectEntity worker = createSubject("WORKER", "李阿姨", "李阿姨", "启用");
        AccountSubjectEntity platform = createSubject("PLATFORM", "家政平台主体", "财务中心", "启用");

        AccountEntity customerAccount = createAccount(customer, "用户可用账户", "CNY", "正常");
        AccountEntity workerAccount = createAccount(worker, "服务者应收账户", "CNY", "正常");
        AccountEntity platformAccount = createAccount(platform, "平台手续费账户", "CNY", "正常");

        credit(customerAccount.getAccountNo(), "PAYMENT_SUCCESS", "PAY202607200001", new BigDecimal("168.00"), "系统入账");
        freeze(customerAccount.getAccountNo(), "ORD202607200001", "服务履约担保", new BigDecimal("68.00"), "运营冻结");
        credit(workerAccount.getAccountNo(), "CLEARING_RESULT", "CLR202607200001", new BigDecimal("120.00"), "清分入账");
        credit(platformAccount.getAccountNo(), "SERVICE_FEE", "FEE202607200001", new BigDecimal("12.00"), "平台服务费入账");

        AccountAdjustmentEntity adjustmentEntity = new AccountAdjustmentEntity();
        adjustmentEntity.setAdjustNo(nextNo("ADJ", adjustSeq));
        adjustmentEntity.setAccountNo(workerAccount.getAccountNo());
        adjustmentEntity.setAdjustDirection("贷方");
        adjustmentEntity.setAdjustAmount(new BigDecimal("8.00"));
        adjustmentEntity.setAdjustReason("履约奖励补贴");
        adjustmentEntity.setAdjustStatus("待审批");
        adjustmentEntity.setCreatedBy("财务小李");
        adjustmentEntity.setCreatedAt(now());
        adjustments.put(adjustmentEntity.getAdjustNo(), adjustmentEntity);

        AccountEventEntity eventEntity = new AccountEventEntity();
        eventEntity.setEventNo(nextNo("EVT", eventSeq));
        eventEntity.setEventType("PAYMENT_SUCCESS");
        eventEntity.setBizNo("PAY202607200001");
        eventEntity.setSummary("消费支付成功后用户入账");
        eventEntity.setPayload("{\"paymentOrderId\":\"PAY202607200001\",\"amount\":168.00}");
        eventEntity.setEventStatus("已消费");
        eventEntity.setCreatedAt(now());
        events.add(eventEntity);
    }

    public List<AccountSubjectEntity> subjects() {
        return new ArrayList<>(subjects.values());
    }

    public AccountSubjectEntity createSubject(String subjectType, String subjectName, String ownerName, String status) {
        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setSubjectId(nextNo("SUB", subjectSeq));
        entity.setSubjectType(subjectType);
        entity.setSubjectName(subjectName);
        entity.setOwnerName(ownerName);
        entity.setStatus(status);
        entity.setCreatedAt(now());
        subjects.put(entity.getSubjectId(), entity);
        return entity;
    }

    public AccountSubjectEntity findSubject(String subjectId) {
        return subjects.get(subjectId);
    }

    public List<AccountEntity> accounts() {
        return new ArrayList<>(accounts.values());
    }

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
        accounts.put(entity.getAccountNo(), entity);

        AccountBalanceEntity balanceEntity = new AccountBalanceEntity();
        balanceEntity.setAccountNo(entity.getAccountNo());
        balanceEntity.setAvailableAmount(BigDecimal.ZERO);
        balanceEntity.setFrozenAmount(BigDecimal.ZERO);
        balanceEntity.setInTransitAmount(BigDecimal.ZERO);
        balanceEntity.setUpdatedAt(now());
        balances.put(entity.getAccountNo(), balanceEntity);
        return entity;
    }

    public AccountEntity findAccount(String accountNo) {
        return accounts.get(accountNo);
    }

    public AccountBalanceEntity findBalance(String accountNo) {
        return balances.get(accountNo);
    }

    public List<AccountLedgerEntity> ledgers() {
        List<AccountLedgerEntity> copied = new ArrayList<>(ledgers);
        copied.sort(Comparator.comparing(AccountLedgerEntity::getCreatedAt).reversed());
        return copied;
    }

    public AccountLedgerEntity credit(String accountNo, String bizType, String bizNo, BigDecimal amount, String operatorName) {
        AccountBalanceEntity balance = findBalance(accountNo);
        BigDecimal before = balance.getAvailableAmount();
        BigDecimal after = before.add(amount);
        balance.setAvailableAmount(after);
        balance.setUpdatedAt(now());
        updateAccountChangeTime(accountNo);
        AccountLedgerEntity entity = buildLedger(accountNo, bizType, bizNo, "贷方", amount, before, after, operatorName);
        ledgers.add(entity);
        return entity;
    }

    public AccountLedgerEntity freeze(String accountNo, String bizNo, String freezeReason, BigDecimal amount, String operatorName) {
        return freezeBalanceOnly(accountNo, bizNo, freezeReason, amount, operatorName);
    }

    public List<AccountFreezeEntity> freezes() {
        List<AccountFreezeEntity> copied = new ArrayList<>(freezes.values());
        copied.sort(Comparator.comparing(AccountFreezeEntity::getCreatedAt).reversed());
        return copied;
    }

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
        freezes.put(entity.getFreezeNo(), entity);
        freezeBalanceOnly(accountNo, bizNo, freezeReason, freezeAmount, operatorName);
        return entity;
    }

    private AccountLedgerEntity freezeBalanceOnly(String accountNo, String bizNo, String freezeReason, BigDecimal amount,
            String operatorName) {
        AccountBalanceEntity balance = findBalance(accountNo);
        BigDecimal before = balance.getAvailableAmount();
        BigDecimal after = before.subtract(amount);
        balance.setAvailableAmount(after);
        balance.setFrozenAmount(balance.getFrozenAmount().add(amount));
        balance.setUpdatedAt(now());
        updateAccountChangeTime(accountNo);

        AccountLedgerEntity entity = buildLedger(accountNo, "BALANCE_FREEZE", bizNo, "借方", amount, before, after, operatorName);
        ledgers.add(entity);
        return entity;
    }

    public AccountFreezeEntity unfreeze(String freezeNo, String operatorName, String reason) {
        AccountFreezeEntity freezeEntity = freezes.get(freezeNo);
        if (freezeEntity == null || Objects.equals(freezeEntity.getFreezeStatus(), "已解冻")) {
            return freezeEntity;
        }
        unfreezeBalanceOnly(
                freezeEntity.getAccountNo(),
                freezeEntity.getBizNo(),
                freezeEntity.getFreezeAmount(),
                operatorName);

        freezeEntity.setFreezeStatus("已解冻");
        freezeEntity.setUnfrozenAt(now());
        freezeEntity.setFreezeReason(freezeEntity.getFreezeReason() + " / " + reason);
        return freezeEntity;
    }

    public AccountLedgerEntity unfreezeBalanceOnly(String accountNo, String bizNo, BigDecimal amount, String operatorName) {
        AccountBalanceEntity balance = findBalance(accountNo);
        BigDecimal before = balance.getAvailableAmount();
        BigDecimal after = before.add(amount);
        balance.setAvailableAmount(after);
        balance.setFrozenAmount(balance.getFrozenAmount().subtract(amount));
        balance.setUpdatedAt(now());
        updateAccountChangeTime(accountNo);

        AccountLedgerEntity entity = buildLedger(accountNo, "BALANCE_UNFREEZE", bizNo, "贷方", amount, before, after, operatorName);
        ledgers.add(entity);
        return entity;
    }

    public List<AccountAdjustmentEntity> adjustments() {
        List<AccountAdjustmentEntity> copied = new ArrayList<>(adjustments.values());
        copied.sort(Comparator.comparing(AccountAdjustmentEntity::getCreatedAt).reversed());
        return copied;
    }

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
        adjustments.put(entity.getAdjustNo(), entity);
        return entity;
    }

    public AccountAdjustmentEntity approveAdjustment(String adjustNo, String approvedBy) {
        AccountAdjustmentEntity entity = adjustments.get(adjustNo);
        if (entity == null || Objects.equals(entity.getAdjustStatus(), "已生效")) {
            return entity;
        }
        entity.setAdjustStatus("已生效");
        entity.setApprovedBy(approvedBy);
        entity.setApprovedAt(now());
        BigDecimal before = findBalance(entity.getAccountNo()).getAvailableAmount();
        BigDecimal amount = entity.getAdjustAmount();
        BigDecimal after = "贷方".equals(entity.getAdjustDirection()) ? before.add(amount) : before.subtract(amount);
        findBalance(entity.getAccountNo()).setAvailableAmount(after);
        findBalance(entity.getAccountNo()).setUpdatedAt(now());
        updateAccountChangeTime(entity.getAccountNo());
        ledgers.add(buildLedger(
                entity.getAccountNo(),
                "ACCOUNT_ADJUSTMENT",
                entity.getAdjustNo(),
                entity.getAdjustDirection(),
                amount,
                before,
                after,
                approvedBy));
        return entity;
    }

    public List<AccountEventEntity> events() {
        List<AccountEventEntity> copied = new ArrayList<>(events);
        copied.sort(Comparator.comparing(AccountEventEntity::getCreatedAt).reversed());
        return copied;
    }

    public AccountEventEntity recordEvent(String eventType, String bizNo, String summary, String payload) {
        AccountEventEntity entity = new AccountEventEntity();
        entity.setEventNo(nextNo("EVT", eventSeq));
        entity.setEventType(eventType);
        entity.setBizNo(bizNo);
        entity.setSummary(summary);
        entity.setPayload(payload);
        entity.setEventStatus("已消费");
        entity.setCreatedAt(now());
        events.add(entity);
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
        AccountEntity accountEntity = accounts.get(accountNo);
        if (accountEntity != null) {
            accountEntity.setLastChangeAt(now());
        }
    }

    private String nextNo(String prefix, AtomicLong sequence) {
        return prefix + sequence.incrementAndGet();
    }

    private String now() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}
