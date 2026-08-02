package com.abc123.walletaccount.service.impl;

import com.abc123.walletaccount.common.BusinessException;
import com.abc123.walletaccount.dao.WalletAccountDao;
import com.abc123.walletaccount.dto.OpenWalletAccountRequestDTO;
import com.abc123.walletaccount.dto.PageResultDTO;
import com.abc123.walletaccount.dto.WalletAccountDTO;
import com.abc123.walletaccount.dto.WalletAccountDetailDTO;
import com.abc123.walletaccount.dto.WalletAccountQueryDTO;
import com.abc123.walletaccount.dto.WalletAccountStatusLogDTO;
import com.abc123.walletaccount.dto.WalletAccountStatusChangeRequestDTO;
import com.abc123.walletaccount.dto.WalletBalanceDTO;
import com.abc123.walletaccount.dto.WalletFlowDTO;
import com.abc123.walletaccount.dto.WalletFlowExportRequestDTO;
import com.abc123.walletaccount.dto.WalletFlowExportTaskDTO;
import com.abc123.walletaccount.dto.WalletFlowQueryDTO;
import com.abc123.walletaccount.entity.WalletAccountEntity;
import com.abc123.walletaccount.entity.WalletAccountStatusLogEntity;
import com.abc123.walletaccount.entity.WalletFlowEntity;
import com.abc123.walletaccount.entity.WalletOwnerEntity;
import com.abc123.walletaccount.service.WalletAccountService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletAccountServiceImpl implements WalletAccountService {

    private final WalletAccountDao walletAccountDao;

    public WalletAccountServiceImpl(WalletAccountDao walletAccountDao) {
        this.walletAccountDao = walletAccountDao;
    }

    @Override
    public PageResultDTO<WalletAccountDTO> pageAccounts(WalletAccountQueryDTO queryDTO) {
        int pageNo = queryDTO.getPageNo() == null || queryDTO.getPageNo() < 1 ? 1 : queryDTO.getPageNo();
        int pageSize = queryDTO.getPageSize() == null || queryDTO.getPageSize() < 1 ? 20 : queryDTO.getPageSize();
        int offset = (pageNo - 1) * pageSize;
        PageResultDTO<WalletAccountDTO> pageResultDTO = new PageResultDTO<WalletAccountDTO>();
        pageResultDTO.setTotal(walletAccountDao.countAccounts(
                queryDTO.getKeyword(), queryDTO.getOwnerType(), queryDTO.getAccountStatus()));
        pageResultDTO.setRecords(walletAccountDao.listAccounts(
                queryDTO.getKeyword(), queryDTO.getOwnerType(), queryDTO.getAccountStatus(), offset, pageSize)
                .stream()
                .map(this::toAccountDTO)
                .collect(Collectors.toList()));
        return pageResultDTO;
    }

    @Override
    public WalletAccountDetailDTO getAccountDetail(String walletAccountNo) {
        WalletAccountEntity accountEntity = getRequiredAccount(walletAccountNo);
        WalletAccountDetailDTO detailDTO = new WalletAccountDetailDTO();
        detailDTO.setAccount(toAccountDTO(accountEntity));
        detailDTO.setBalance(toBalanceDTO(accountEntity));
        detailDTO.setRecentFlows(walletAccountDao.listRecentFlowsByAccountNo(walletAccountNo, 10)
                .stream()
                .map(this::toFlowDTO)
                .collect(Collectors.toList()));
        detailDTO.setStatusLogs(walletAccountDao.listStatusLogsByAccountNo(walletAccountNo, 10)
                .stream()
                .map(this::toStatusLogDTO)
                .collect(Collectors.toList()));
        return detailDTO;
    }

    @Override
    public WalletBalanceDTO getBalance(String walletAccountNo) {
        return toBalanceDTO(getRequiredAccount(walletAccountNo));
    }

    @Override
    public List<WalletBalanceDTO> listBalances(List<String> walletAccountNos) {
        if (walletAccountNos == null || walletAccountNos.isEmpty()) {
            return Collections.emptyList();
        }
        return walletAccountDao.listBalancesByAccountNos(walletAccountNos)
                .stream()
                .map(this::toBalanceDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<WalletFlowDTO> listFlows(WalletFlowQueryDTO queryDTO) {
        return walletAccountDao.listFlows(queryDTO.getWalletAccountNo(), queryDTO.getSourceSystem(), queryDTO.getSourceBizNo())
                .stream()
                .map(this::toFlowDTO)
                .collect(Collectors.toList());
    }

    @Override
    public WalletFlowExportTaskDTO exportFlows(WalletFlowExportRequestDTO requestDTO) {
        WalletFlowExportTaskDTO taskDTO = new WalletFlowExportTaskDTO();
        taskDTO.setExportTaskNo("WFE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        taskDTO.setTaskStatus("ACCEPTED");
        return taskDTO;
    }

    @Override
    @Transactional
    public WalletAccountDTO openAccount(OpenWalletAccountRequestDTO requestDTO) {
        validateOpenRequest(requestDTO);
        WalletAccountEntity existing = walletAccountDao.findAccountByOwnerAndTypeScene(
                requestDTO.getWalletOwnerId(), requestDTO.getAccountType(), requestDTO.getAccountScene());
        if (existing != null) {
            return toAccountDTO(existing);
        }
        WalletOwnerEntity ownerEntity = new WalletOwnerEntity();
        ownerEntity.setWalletOwnerId(requestDTO.getWalletOwnerId());
        ownerEntity.setOwnerType(requestDTO.getOwnerType());
        ownerEntity.setOwnerName(requestDTO.getOwnerName());
        ownerEntity.setOwnerStatus("ENABLED");
        ownerEntity.setBizLineCode(requestDTO.getBizLineCode());
        ownerEntity.setTenantCode(requestDTO.getTenantCode());
        ownerEntity.setExtRefNo(requestDTO.getExtRefNo());
        if (walletAccountDao.findOwnerById(requestDTO.getWalletOwnerId()) == null) {
            walletAccountDao.insertOwner(ownerEntity);
        }

        WalletAccountEntity accountEntity = new WalletAccountEntity();
        accountEntity.setWalletAccountNo("WA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        accountEntity.setWalletOwnerId(requestDTO.getWalletOwnerId());
        accountEntity.setOwnerType(requestDTO.getOwnerType());
        accountEntity.setOwnerName(requestDTO.getOwnerName());
        accountEntity.setAccountType(requestDTO.getAccountType());
        accountEntity.setAccountScene(requestDTO.getAccountScene());
        accountEntity.setCurrencyCode(requestDTO.getCurrencyCode() == null ? "CNY" : requestDTO.getCurrencyCode());
        accountEntity.setAccountStatus("INIT");
        accountEntity.setAllowCredit(Boolean.TRUE.equals(requestDTO.getAllowCredit()));
        accountEntity.setRiskLevel(requestDTO.getRiskLevel() == null ? "LOW" : requestDTO.getRiskLevel());
        accountEntity.setTotalBalance(BigDecimal.ZERO);
        accountEntity.setAvailableBalance(BigDecimal.ZERO);
        accountEntity.setFrozenBalance(BigDecimal.ZERO);
        accountEntity.setPendingInBalance(BigDecimal.ZERO);
        accountEntity.setPendingOutBalance(BigDecimal.ZERO);
        accountEntity.setOpenedAt(LocalDateTime.now());
        walletAccountDao.insertAccount(accountEntity);
        walletAccountDao.insertBalance(accountEntity);

        WalletFlowEntity flowEntity = new WalletFlowEntity();
        flowEntity.setFlowNo("WF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        flowEntity.setWalletAccountNo(accountEntity.getWalletAccountNo());
        flowEntity.setFlowType("OPEN_ACCOUNT");
        flowEntity.setSourceSystem("wallet-account");
        flowEntity.setSourceBizNo(accountEntity.getWalletAccountNo());
        flowEntity.setIdempotencyKey("OPEN-" + requestDTO.getWalletOwnerId() + "-" + requestDTO.getAccountType()
                + "-" + requestDTO.getAccountScene());
        flowEntity.setChangeAmount(BigDecimal.ZERO);
        flowEntity.setBeforeAvailableBalance(BigDecimal.ZERO);
        flowEntity.setAfterAvailableBalance(BigDecimal.ZERO);
        flowEntity.setOperatorName(requestDTO.getOperatorName() == null ? "system" : requestDTO.getOperatorName());
        flowEntity.setOperationReason("账户开户");
        walletAccountDao.insertFlow(flowEntity);
        recordStatusLog(accountEntity.getWalletAccountNo(), null, "INIT", "OPEN_ACCOUNT", "账户开户",
                requestDTO.getOperatorName(), requestDTO.getOperatorName());
        return toAccountDTO(walletAccountDao.findAccountByNo(accountEntity.getWalletAccountNo()));
    }

    @Override
    @Transactional
    public WalletAccountDTO changeStatus(String walletAccountNo, WalletAccountStatusChangeRequestDTO requestDTO) {
        WalletAccountEntity entity = getRequiredAccount(walletAccountNo);
        if (requestDTO == null || requestDTO.getTargetStatus() == null || requestDTO.getTargetStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("目标状态不能为空");
        }
        String currentStatus = entity.getAccountStatus();
        String targetStatus = requestDTO.getTargetStatus();
        validateStatusChange(entity, targetStatus);
        LocalDateTime closedAt = "CLOSED".equals(targetStatus) ? LocalDateTime.now() : null;
        int updatedRows = walletAccountDao.updateAccountStatus(
                walletAccountNo, currentStatus, targetStatus, closedAt);
        if (updatedRows != 1) {
            throw new BusinessException("WALLET_ACCOUNT_STATUS_CONFLICT", "账户状态已被其他请求更新，请刷新后重试");
        }

        WalletFlowEntity flowEntity = new WalletFlowEntity();
        flowEntity.setFlowNo("WF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        flowEntity.setWalletAccountNo(walletAccountNo);
        flowEntity.setFlowType("STATUS_CHANGE");
        flowEntity.setSourceSystem("wallet-account");
        flowEntity.setSourceBizNo(walletAccountNo);
        flowEntity.setIdempotencyKey("STATUS-" + walletAccountNo + "-" + currentStatus + "-" + targetStatus);
        flowEntity.setChangeAmount(BigDecimal.ZERO);
        flowEntity.setBeforeAvailableBalance(entity.getAvailableBalance());
        flowEntity.setAfterAvailableBalance(entity.getAvailableBalance());
        flowEntity.setOperatorName(requestDTO.getOperatorName() == null ? "system" : requestDTO.getOperatorName());
        flowEntity.setOperationReason(requestDTO.getOperationReason() == null ? "状态流转" : requestDTO.getOperationReason());
        walletAccountDao.insertFlow(flowEntity);
        recordStatusLog(walletAccountNo, currentStatus, targetStatus, "STATUS_CHANGE",
                requestDTO.getOperationReason() == null ? "状态流转" : requestDTO.getOperationReason(),
                requestDTO.getOperatorId(), requestDTO.getOperatorName());
        return toAccountDTO(walletAccountDao.findAccountByNo(walletAccountNo));
    }

    private void validateOpenRequest(OpenWalletAccountRequestDTO requestDTO) {
        if (requestDTO == null) {
            throw new IllegalArgumentException("开户请求不能为空");
        }
        if (isBlank(requestDTO.getRequestNo())) {
            throw new IllegalArgumentException("requestNo不能为空");
        }
        if (isBlank(requestDTO.getWalletOwnerId()) || isBlank(requestDTO.getOwnerType()) || isBlank(requestDTO.getOwnerName())) {
            throw new IllegalArgumentException("钱包主体信息不完整");
        }
        if (isBlank(requestDTO.getAccountType()) || isBlank(requestDTO.getAccountScene())) {
            throw new IllegalArgumentException("账户类型和账户场景不能为空");
        }
    }

    private void validateStatusChange(WalletAccountEntity entity, String targetStatus) {
        String currentStatus = entity.getAccountStatus();
        if ("INIT".equals(currentStatus) && "ACTIVE".equals(targetStatus)) {
            return;
        }
        if ("ACTIVE".equals(currentStatus) && "FROZEN".equals(targetStatus)) {
            return;
        }
        if ("FROZEN".equals(currentStatus) && "ACTIVE".equals(targetStatus)) {
            return;
        }
        if ("ACTIVE".equals(currentStatus) && "CLOSED".equals(targetStatus)) {
            if (hasNonZeroBalance(entity)) {
                throw new BusinessException("WALLET_ACCOUNT_CLOSE_REJECTED", "账户余额、冻结或在途金额未清零，不能销户");
            }
            return;
        }
        throw new BusinessException("WALLET_ACCOUNT_STATUS_INVALID", "非法状态流转: " + currentStatus + " -> " + targetStatus);
    }

    private boolean hasNonZeroBalance(WalletAccountEntity entity) {
        return isPositive(entity.getTotalBalance())
                || isPositive(entity.getFrozenBalance())
                || isPositive(entity.getPendingInBalance())
                || isPositive(entity.getPendingOutBalance());
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private WalletAccountEntity getRequiredAccount(String walletAccountNo) {
        WalletAccountEntity entity = walletAccountDao.findAccountByNo(walletAccountNo);
        if (entity == null) {
            throw new BusinessException("WALLET_ACCOUNT_NOT_FOUND", "钱包账户不存在");
        }
        return entity;
    }

    private void recordStatusLog(String walletAccountNo, String beforeStatus, String afterStatus, String reasonCode,
            String reasonDesc, String operatorId, String operatorName) {
        WalletAccountStatusLogEntity statusLogEntity = new WalletAccountStatusLogEntity();
        statusLogEntity.setWalletAccountNo(walletAccountNo);
        statusLogEntity.setBeforeStatus(beforeStatus);
        statusLogEntity.setAfterStatus(afterStatus);
        statusLogEntity.setReasonCode(reasonCode);
        statusLogEntity.setReasonDesc(reasonDesc);
        statusLogEntity.setOperatorId(operatorId == null ? "system" : operatorId);
        statusLogEntity.setOperatorName(operatorName == null ? "system" : operatorName);
        walletAccountDao.insertStatusLog(statusLogEntity);
    }

    private WalletAccountDTO toAccountDTO(WalletAccountEntity entity) {
        WalletAccountDTO dto = new WalletAccountDTO();
        dto.setWalletAccountNo(entity.getWalletAccountNo());
        dto.setWalletOwnerId(entity.getWalletOwnerId());
        dto.setOwnerType(entity.getOwnerType());
        dto.setOwnerName(entity.getOwnerName());
        dto.setAccountType(entity.getAccountType());
        dto.setAccountScene(entity.getAccountScene());
        dto.setCurrencyCode(entity.getCurrencyCode());
        dto.setAccountStatus(entity.getAccountStatus());
        dto.setAllowCredit(entity.getAllowCredit());
        dto.setRiskLevel(entity.getRiskLevel());
        dto.setTotalBalance(entity.getTotalBalance());
        dto.setAvailableBalance(entity.getAvailableBalance());
        dto.setFrozenBalance(entity.getFrozenBalance());
        dto.setPendingInBalance(entity.getPendingInBalance());
        dto.setPendingOutBalance(entity.getPendingOutBalance());
        dto.setOpenedAt(entity.getOpenedAt());
        return dto;
    }

    private WalletBalanceDTO toBalanceDTO(WalletAccountEntity entity) {
        WalletBalanceDTO dto = new WalletBalanceDTO();
        dto.setWalletAccountNo(entity.getWalletAccountNo());
        dto.setTotalBalance(entity.getTotalBalance());
        dto.setAvailableBalance(entity.getAvailableBalance());
        dto.setFrozenBalance(entity.getFrozenBalance());
        dto.setPendingInBalance(entity.getPendingInBalance());
        dto.setPendingOutBalance(entity.getPendingOutBalance());
        return dto;
    }

    private WalletFlowDTO toFlowDTO(WalletFlowEntity entity) {
        WalletFlowDTO dto = new WalletFlowDTO();
        dto.setFlowNo(entity.getFlowNo());
        dto.setWalletAccountNo(entity.getWalletAccountNo());
        dto.setFlowType(entity.getFlowType());
        dto.setSourceSystem(entity.getSourceSystem());
        dto.setSourceBizNo(entity.getSourceBizNo());
        dto.setChangeAmount(entity.getChangeAmount());
        dto.setBeforeAvailableBalance(entity.getBeforeAvailableBalance());
        dto.setAfterAvailableBalance(entity.getAfterAvailableBalance());
        dto.setOperatorName(entity.getOperatorName());
        dto.setOperationReason(entity.getOperationReason());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private WalletAccountStatusLogDTO toStatusLogDTO(WalletAccountStatusLogEntity entity) {
        WalletAccountStatusLogDTO dto = new WalletAccountStatusLogDTO();
        dto.setWalletAccountNo(entity.getWalletAccountNo());
        dto.setBeforeStatus(entity.getBeforeStatus());
        dto.setAfterStatus(entity.getAfterStatus());
        dto.setReasonCode(entity.getReasonCode());
        dto.setReasonDesc(entity.getReasonDesc());
        dto.setOperatorId(entity.getOperatorId());
        dto.setOperatorName(entity.getOperatorName());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
