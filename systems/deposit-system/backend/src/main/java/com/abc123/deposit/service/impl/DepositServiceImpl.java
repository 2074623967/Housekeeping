package com.abc123.deposit.service.impl;

import com.abc123.deposit.common.BusinessException;
import com.abc123.deposit.dao.DepositDao;
import com.abc123.deposit.dto.DebtOffsetRequestDTO;
import com.abc123.deposit.dto.DepositAccountCreateRequestDTO;
import com.abc123.deposit.dto.DepositAccountDetailDTO;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositAccountQueryDTO;
import com.abc123.deposit.dto.DepositActionRequestDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
import com.abc123.deposit.dto.DepositMigrationPreviewDTO;
import com.abc123.deposit.dto.DepositOverviewDTO;
import com.abc123.deposit.dto.DepositRuleBindingPreviewDTO;
import com.abc123.deposit.dto.DepositTotalAccountDTO;
import com.abc123.deposit.dto.DepositTotalAccountDetailDTO;
import com.abc123.deposit.dto.DepositV2OverviewDTO;
import com.abc123.deposit.service.DepositService;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
    public DepositOverviewDTO overview() {
        List<DepositAccountDTO> accounts = dao.findAccounts(new DepositAccountQueryDTO());
        DepositOverviewDTO overview = new DepositOverviewDTO();
        overview.setAccountCount(accounts.size());
        overview.setOwnerCount((int) accounts.stream()
                .map(item -> item.getOwnerType() + ":" + item.getOwnerId())
                .distinct()
                .count());
        overview.setTotalBalance(sum(accounts.stream().map(DepositAccountDTO::getBalance).collect(Collectors.toList())));
        overview.setTotalFrozenAmount(sum(accounts.stream().map(DepositAccountDTO::getFrozenAmount).collect(Collectors.toList())));
        overview.setTotalAvailableAmount(sum(accounts.stream().map(DepositAccountDTO::getAvailableAmount).collect(Collectors.toList())));
        overview.setFullyPaidAccountCount((int) accounts.stream()
                .filter(this::isFullyPaid)
                .count());
        return overview;
    }

    @Override
    public List<DepositAccountDTO> accounts(DepositAccountQueryDTO query) {
        return dao.findAccounts(trimQuery(query));
    }

    @Override
    public byte[] exportAccounts(DepositAccountQueryDTO query) {
        List<DepositAccountDTO> items = accounts(query);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("账户号,主体编号,主体类型,应缴金额,保证金余额,冻结金额,可用余额,账户状态\n");
        for (DepositAccountDTO item : items) {
            builder.append(csv(item.getAccountNo())).append(',')
                    .append(csv(item.getOwnerId())).append(',')
                    .append(csv(item.getOwnerType())).append(',')
                    .append(csv(amount(item.getRequiredAmount()))).append(',')
                    .append(csv(amount(item.getBalance()))).append(',')
                    .append(csv(amount(item.getFrozenAmount()))).append(',')
                    .append(csv(amount(item.getAvailableAmount()))).append(',')
                    .append(csv(item.getStatus())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public DepositAccountDetailDTO accountDetail(String accountNo) {
        DepositAccountDetailDTO detail = dao.findAccountDetail(required(accountNo, "保证金账户号不能为空"));
        if (detail == null) {
            throw new BusinessException("保证金账户不存在");
        }
        BigDecimal requiredAmount = defaultValue(detail.getRequiredAmount());
        BigDecimal balance = defaultValue(detail.getBalance());
        detail.setFullyPaid(balance.compareTo(requiredAmount) >= 0);
        detail.setOutstandingAmount(requiredAmount.subtract(balance).max(ZERO));
        return detail;
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
    public List<DepositFlowDTO> flows(String accountNo, DepositFlowQueryDTO query) {
        return dao.findFlows(required(accountNo, "保证金账户号不能为空"), trimQuery(query));
    }

    @Override
    public byte[] exportFlows(String accountNo, DepositFlowQueryDTO query) {
        List<DepositFlowDTO> items = flows(accountNo, query);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("流水号,账户号,动作类型,发生金额,变更前余额,变更后余额,变更前冻结金额,变更后冻结金额,业务关联号,备注,发生时间\n");
        for (DepositFlowDTO item : items) {
            builder.append(csv(item.getFlowNo())).append(',')
                    .append(csv(item.getAccountNo())).append(',')
                    .append(csv(item.getFlowType())).append(',')
                    .append(csv(amount(item.getAmount()))).append(',')
                    .append(csv(amount(item.getBeforeBalance()))).append(',')
                    .append(csv(amount(item.getAfterBalance()))).append(',')
                    .append(csv(amount(item.getBeforeFrozenAmount()))).append(',')
                    .append(csv(amount(item.getAfterFrozenAmount()))).append(',')
                    .append(csv(item.getReferenceNo())).append(',')
                    .append(csv(item.getRemark())).append(',')
                    .append(csv(item.getCreatedAt() == null ? null : item.getCreatedAt().toString())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public DepositV2OverviewDTO v2Overview() {
        List<DepositAccountDTO> accounts = dao.findAccounts(new DepositAccountQueryDTO());
        List<DepositTotalAccountDTO> totalAccounts = buildTotalAccounts(accounts);
        DepositV2OverviewDTO overview = new DepositV2OverviewDTO();
        overview.setTotalAccountCount(totalAccounts.size());
        overview.setBindingCount(totalAccounts.stream()
                .map(DepositTotalAccountDTO::getRuleCount)
                .reduce(0, Integer::sum));
        overview.setMigrationCandidateCount(totalAccounts.stream()
                .map(DepositTotalAccountDTO::getMigrationCount)
                .reduce(0, Integer::sum));
        overview.setTotalRequiredAmount(sum(accounts.stream().map(DepositAccountDTO::getRequiredAmount).collect(Collectors.toList())));
        overview.setTotalBalance(sum(accounts.stream().map(DepositAccountDTO::getBalance).collect(Collectors.toList())));
        overview.setTotalFrozenAmount(sum(accounts.stream().map(DepositAccountDTO::getFrozenAmount).collect(Collectors.toList())));
        overview.setTotalAvailableAmount(sum(accounts.stream().map(DepositAccountDTO::getAvailableAmount).collect(Collectors.toList())));
        return overview;
    }

    @Override
    public List<DepositTotalAccountDTO> totalAccounts() {
        return buildTotalAccounts(dao.findAccounts(new DepositAccountQueryDTO()));
    }

    @Override
    public DepositTotalAccountDetailDTO totalAccountDetail(String totalAccountNo) {
        String normalizedTotalAccountNo = required(totalAccountNo, "总保证金账户号不能为空");
        List<DepositAccountDTO> accounts = dao.findAccounts(new DepositAccountQueryDTO());
        String ownerType = ownerTypeFromTotalAccountNo(normalizedTotalAccountNo);
        List<DepositAccountDTO> scopedAccounts = accounts.stream()
                .filter(item -> ownerType.equals(item.getOwnerType()))
                .collect(Collectors.toList());
        if (scopedAccounts.isEmpty()) {
            throw new BusinessException("总保证金账户预览不存在");
        }
        DepositTotalAccountDetailDTO detail = new DepositTotalAccountDetailDTO();
        detail.setSummary(buildTotalAccount(ownerType, scopedAccounts));
        detail.setBindings(buildBindings(ownerType, scopedAccounts));
        detail.setMigrations(buildMigrations(ownerType, scopedAccounts));
        return detail;
    }

    @Override
    public List<DepositMigrationPreviewDTO> migrationPreview() {
        List<DepositMigrationPreviewDTO> items = new ArrayList<>();
        for (DepositTotalAccountDTO totalAccount : totalAccounts()) {
            items.addAll(buildMigrations(totalAccount.getOwnerType(), dao.findAccounts(buildOwnerTypeQuery(totalAccount.getOwnerType()))));
        }
        items.sort(Comparator.comparing(DepositMigrationPreviewDTO::getTargetTotalAccountNo)
                .thenComparing(DepositMigrationPreviewDTO::getSourceAccountNo));
        return items;
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

    private DepositAccountQueryDTO trimQuery(DepositAccountQueryDTO query) {
        DepositAccountQueryDTO safeQuery = query == null ? new DepositAccountQueryDTO() : query;
        safeQuery.setOwnerType(trimToNull(safeQuery.getOwnerType()));
        safeQuery.setStatus(trimToNull(safeQuery.getStatus()));
        safeQuery.setKeyword(trimToNull(safeQuery.getKeyword()));
        return safeQuery;
    }

    private DepositFlowQueryDTO trimQuery(DepositFlowQueryDTO query) {
        DepositFlowQueryDTO safeQuery = query == null ? new DepositFlowQueryDTO() : query;
        safeQuery.setFlowType(trimToNull(safeQuery.getFlowType()));
        safeQuery.setReferenceNo(trimToNull(safeQuery.getReferenceNo()));
        return safeQuery;
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal defaultValue(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().map(this::defaultValue).reduce(ZERO, BigDecimal::add);
    }

    private boolean isFullyPaid(DepositAccountDTO account) {
        return defaultValue(account.getBalance()).compareTo(defaultValue(account.getRequiredAmount())) >= 0;
    }

    private List<DepositTotalAccountDTO> buildTotalAccounts(List<DepositAccountDTO> accounts) {
        Map<String, List<DepositAccountDTO>> grouped = new LinkedHashMap<>();
        for (DepositAccountDTO account : accounts.stream()
                .sorted(Comparator.comparing(DepositAccountDTO::getOwnerType).thenComparing(DepositAccountDTO::getAccountNo))
                .collect(Collectors.toList())) {
            grouped.computeIfAbsent(account.getOwnerType(), key -> new ArrayList<>()).add(account);
        }
        List<DepositTotalAccountDTO> items = new ArrayList<>();
        for (Map.Entry<String, List<DepositAccountDTO>> entry : grouped.entrySet()) {
            items.add(buildTotalAccount(entry.getKey(), entry.getValue()));
        }
        return items;
    }

    private DepositTotalAccountDTO buildTotalAccount(String ownerType, List<DepositAccountDTO> accounts) {
        DepositTotalAccountDTO dto = new DepositTotalAccountDTO();
        dto.setTotalAccountNo(totalAccountNo(ownerType));
        dto.setOwnerType(ownerType);
        dto.setAccountCount(accounts.size());
        dto.setRuleCount(accounts.size());
        dto.setMigrationCount((int) accounts.stream()
                .filter(item -> defaultValue(item.getBalance()).compareTo(ZERO) > 0)
                .count());
        dto.setTotalRequiredAmount(sum(accounts.stream().map(DepositAccountDTO::getRequiredAmount).collect(Collectors.toList())));
        dto.setTotalBalance(sum(accounts.stream().map(DepositAccountDTO::getBalance).collect(Collectors.toList())));
        dto.setTotalFrozenAmount(sum(accounts.stream().map(DepositAccountDTO::getFrozenAmount).collect(Collectors.toList())));
        dto.setTotalAvailableAmount(sum(accounts.stream().map(DepositAccountDTO::getAvailableAmount).collect(Collectors.toList())));
        dto.setStatus(accounts.stream().allMatch(item -> "OPEN".equals(item.getStatus())) ? "PREVIEW_READY" : "CHECK_REQUIRED");
        return dto;
    }

    private List<DepositRuleBindingPreviewDTO> buildBindings(String ownerType, List<DepositAccountDTO> accounts) {
        List<DepositRuleBindingPreviewDTO> items = new ArrayList<>();
        int index = 1;
        for (DepositAccountDTO account : accounts.stream()
                .sorted(Comparator.comparing(DepositAccountDTO::getAccountNo))
                .collect(Collectors.toList())) {
            DepositRuleBindingPreviewDTO item = new DepositRuleBindingPreviewDTO();
            item.setRuleCode(ownerType + "-RULE-" + String.format("%02d", index));
            item.setRuleName(ownerType + "保证金规则-" + index);
            item.setBusinessLine("HOME_SERVICE");
            item.setCategoryCode(ownerType);
            item.setSourceAccountNo(account.getAccountNo());
            item.setSourceOwnerId(account.getOwnerId());
            item.setRuleAmount(defaultValue(account.getRequiredAmount()));
            item.setCurrentBalance(defaultValue(account.getBalance()));
            item.setFrozenAmount(defaultValue(account.getFrozenAmount()));
            item.setBindingStatus(defaultValue(account.getBalance()).compareTo(defaultValue(account.getRequiredAmount())) >= 0
                    ? "READY_TO_BIND" : "WAIT_BALANCE_FILL");
            items.add(item);
            index++;
        }
        return items;
    }

    private List<DepositMigrationPreviewDTO> buildMigrations(String ownerType, List<DepositAccountDTO> accounts) {
        List<DepositMigrationPreviewDTO> items = new ArrayList<>();
        String totalAccountNo = totalAccountNo(ownerType);
        for (DepositAccountDTO account : accounts.stream()
                .sorted(Comparator.comparing(DepositAccountDTO::getAccountNo))
                .collect(Collectors.toList())) {
            DepositMigrationPreviewDTO item = new DepositMigrationPreviewDTO();
            item.setMigrationNo("MIG-" + account.getAccountNo());
            item.setSourceAccountNo(account.getAccountNo());
            item.setSourceOwnerId(account.getOwnerId());
            item.setSourceOwnerType(account.getOwnerType());
            item.setTargetTotalAccountNo(totalAccountNo);
            item.setSourceBalance(defaultValue(account.getBalance()));
            item.setMigratedBalance(defaultValue(account.getBalance()));
            item.setMigrationStatus(defaultValue(account.getBalance()).compareTo(ZERO) > 0 ? "PENDING_MIGRATION" : "NO_BALANCE");
            item.setMigrationRemark(defaultValue(account.getBalance()).compareTo(ZERO) > 0
                    ? "V1 单账户余额需结转到总保证金账户"
                    : "当前账户无可迁移余额，保留历史追溯即可");
            items.add(item);
        }
        return items;
    }

    private String totalAccountNo(String ownerType) {
        return "TDEP-" + ownerType;
    }

    private String ownerTypeFromTotalAccountNo(String totalAccountNo) {
        if (!totalAccountNo.startsWith("TDEP-")) {
            throw new BusinessException("总保证金账户号格式不正确");
        }
        return totalAccountNo.substring("TDEP-".length());
    }

    private DepositAccountQueryDTO buildOwnerTypeQuery(String ownerType) {
        DepositAccountQueryDTO query = new DepositAccountQueryDTO();
        query.setOwnerType(ownerType);
        return query;
    }

    private void ensureNotDuplicated(String accountNo, String flowType, String referenceNo) {
        if (!StringUtils.hasText(referenceNo)) {
            return;
        }
        if (dao.existsFlowReference(accountNo, flowType, referenceNo.trim())) {
            throw new BusinessException("该业务关联号已处理，请勿重复提交");
        }
    }

    private String amount(BigDecimal value) {
        return defaultValue(value).toPlainString();
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
}
