package com.abc123.settlement.service.impl;

import com.abc123.settlement.common.BusinessException;
import com.abc123.settlement.common.ErrorCode;
import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import com.abc123.settlement.dto.CreateSettlementOrderRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementAuditLogDTO;
import com.abc123.settlement.dto.SettlementItemDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
import com.abc123.settlement.dto.SettlementOrderDetailDTO;
import com.abc123.settlement.entity.SettlementOrderEntity;
import com.abc123.settlement.service.SettlementOrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 结算单服务实现。
 */
@Service
public class SettlementOrderServiceImpl implements SettlementOrderService {

    private static final int MONEY_MAX_SCALE = 2;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");

    private final SettlementMemoryStore settlementMemoryStore;
    private final SettlementMapper settlementMapper;

    public SettlementOrderServiceImpl(SettlementMemoryStore settlementMemoryStore, SettlementMapper settlementMapper) {
        this.settlementMemoryStore = settlementMemoryStore;
        this.settlementMapper = settlementMapper;
    }

    @Override
    public PageResultDTO<SettlementOrderDTO> list(String batchNo, String targetType, String settlementStatus, String clearingNo, int pageNo, int pageSize) {
        List<SettlementOrderDTO> items = settlementMemoryStore.orders().stream()
                .filter(item -> batchNo == null || batchNo.isEmpty() || batchNo.equals(item.getBatchNo()))
                .filter(item -> targetType == null || targetType.isEmpty() || targetType.equals(item.getTargetType()))
                .filter(item -> settlementStatus == null || settlementStatus.isEmpty() || settlementStatus.equals(item.getSettlementStatus()))
                .filter(item -> clearingNo == null || clearingNo.isEmpty() || clearingNo.equals(item.getClearingNo()))
                .map(settlementMapper::toOrderDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public SettlementOrderDTO create(CreateSettlementOrderRequestDTO request) {
        validateCreateRequest(request);
        SettlementOrderEntity entity = settlementMemoryStore.createOrder(
                request.getBatchNo(),
                request.getTargetType(),
                request.getTargetNo(),
                request.getTargetName(),
                request.getShouldSettleAmount(),
                request.getDeductAmount(),
                "待审核",
                "MANUAL");
        settlementMemoryStore.createItem(entity.getSettlementNo(), "应结金额", "应结", request.getShouldSettleAmount());
        settlementMemoryStore.createItem(entity.getSettlementNo(), "扣减金额", "扣减", request.getDeductAmount());
        settlementMemoryStore.createAuditLog(entity.getSettlementNo(), "创建结算单", "待审核", "系统");
        return settlementMapper.toOrderDTO(entity);
    }

    private void validateCreateRequest(CreateSettlementOrderRequestDTO request) {
        if (request == null || request.getShouldSettleAmount() == null
                || request.getDeductAmount() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算金额字段不能为空");
        }
        validateMoney(request.getShouldSettleAmount(), "应结金额必须大于0且最多保留2位小数");
        if (request.getDeductAmount().compareTo(BigDecimal.ZERO) < 0
                || request.getDeductAmount().scale() > MONEY_MAX_SCALE
                || request.getDeductAmount().compareTo(MAX_MONEY_AMOUNT) > 0
                || request.getDeductAmount().compareTo(request.getShouldSettleAmount()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "扣减金额必须为非负数且不能超过应结金额");
        }
    }

    private void validateMoney(BigDecimal amount, String message) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > MONEY_MAX_SCALE
                || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    @Override
    public SettlementOrderDTO detail(String settlementNo) {
        SettlementOrderEntity orderEntity = settlementMemoryStore.findOrder(settlementNo);
        if (orderEntity == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ORDER_NOT_FOUND, "结算单不存在");
        }
        return settlementMapper.toOrderDTO(orderEntity);
    }

    @Override
    public SettlementOrderDTO audit(String settlementNo, AuditSettlementRequestDTO request) {
        SettlementOrderDTO audited = settlementMapper.toOrderDTO(
                settlementMemoryStore.audit(settlementNo, request.getOperatorName(), request.getAuditRemark(), true));
        settlementMemoryStore.ensurePendingPayoutBatchForSettlement(settlementNo, request.getOperatorName());
        return settlementMapper.toOrderDTO(settlementMemoryStore.findOrder(settlementNo));
    }

    @Override
    public SettlementOrderDTO reject(String settlementNo, AuditSettlementRequestDTO request) {
        return settlementMapper.toOrderDTO(settlementMemoryStore.audit(settlementNo, request.getOperatorName(), request.getAuditRemark(), false));
    }

    @Override
    public SettlementOrderDetailDTO fullDetail(String settlementNo) {
        SettlementOrderEntity orderEntity = settlementMemoryStore.findOrder(settlementNo);
        if (orderEntity == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ORDER_NOT_FOUND, "结算单不存在");
        }
        List<SettlementItemDTO> items = settlementMemoryStore.itemsBySettlementNo(settlementNo).stream().map(settlementMapper::toItemDTO).collect(Collectors.toList());
        List<SettlementAuditLogDTO> logs = settlementMemoryStore.auditLogsBySettlementNo(settlementNo).stream().map(settlementMapper::toAuditDTO).collect(Collectors.toList());
        return settlementMapper.toOrderDetailDTO(orderEntity, items, logs);
    }

    private PageResultDTO<SettlementOrderDTO> page(List<SettlementOrderDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
