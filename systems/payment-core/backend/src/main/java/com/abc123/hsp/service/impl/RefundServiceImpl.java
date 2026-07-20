package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundPaymentSourceDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RefundServiceImpl implements RefundService {

    private static final String STATUS_REVIEWING = "REVIEWING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String PAYMENT_STATUS_SUCCESS = "SUCCESS";
    private static final DateTimeFormatter REFUND_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RefundMapper refundMapper;

    public RefundServiceImpl(RefundMapper refundMapper) {
        this.refundMapper = refundMapper;
    }

    @Override
    public PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                refundMapper.findAll(query),
                refundMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    @Transactional
    public RefundListItemDTO apply(RefundApplyRequestDTO request) {
        validateApplyRequest(request);
        RefundPaymentSourceDTO source = refundMapper.findPaymentSource(request.getPaymentOrderId().trim());
        if (source == null) {
            throw new IllegalArgumentException("原支付单不存在");
        }
        if (!PAYMENT_STATUS_SUCCESS.equals(source.getStatus())) {
            throw new IllegalArgumentException("只有支付成功的支付单允许发起退款");
        }
        BigDecimal activeRefundAmount = refundMapper.sumActiveRefundAmount(source.getPaymentOrderId());
        BigDecimal refundedAmount = activeRefundAmount == null ? BigDecimal.ZERO : activeRefundAmount;
        if (refundedAmount.add(request.getRefundAmount()).compareTo(source.getPaidAmount()) > 0) {
            throw new IllegalArgumentException("累计退款金额不能超过原支付金额");
        }
        String refundOrderId = "REF" + LocalDateTime.now().format(REFUND_NO_FORMATTER);
        refundMapper.insertRefund(refundOrderId, source, request, STATUS_REVIEWING, "warn");
        return refundMapper.findByRefundOrderId(refundOrderId);
    }

    @Override
    @Transactional
    public RefundListItemDTO approve(RefundActionRequestDTO request) {
        return changeStatus(request, STATUS_REVIEWING, STATUS_PROCESSING, "warn", false);
    }

    @Override
    @Transactional
    public RefundListItemDTO markSuccess(RefundActionRequestDTO request) {
        return changeStatus(request, STATUS_PROCESSING, STATUS_SUCCESS, "success", true);
    }

    @Override
    @Transactional
    public RefundListItemDTO markFail(RefundActionRequestDTO request) {
        return changeStatus(request, STATUS_PROCESSING, STATUS_FAIL, "danger", false);
    }

    @Override
    @Transactional
    public RefundListItemDTO retry(RefundActionRequestDTO request) {
        return changeStatus(request, STATUS_FAIL, STATUS_PROCESSING, "warn", false);
    }

    /**
     * 列表查询统一收敛分页边界，避免后台误传 pageSize 造成大查询。
     */
    private void normalizeQuery(RefundQueryDTO query) {
        query.setRefundOrderId(StringUtils.hasText(query.getRefundOrderId()) ? query.getRefundOrderId().trim() : null);
        query.setPaymentOrderId(StringUtils.hasText(query.getPaymentOrderId()) ? query.getPaymentOrderId().trim() : null);
        query.setRefundStatus(StringUtils.hasText(query.getRefundStatus()) ? query.getRefundStatus().trim() : "全部");
        query.setRefundMethod(StringUtils.hasText(query.getRefundMethod()) ? query.getRefundMethod().trim() : "全部");
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    /**
     * 校验退款申请的业务必填项和金额边界。
     */
    private void validateApplyRequest(RefundApplyRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())) {
            throw new IllegalArgumentException("原支付单号不能为空");
        }
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退款金额必须大于0");
        }
        if (!StringUtils.hasText(request.getRefundMethod())) {
            request.setRefundMethod("原路退款");
        }
    }

    /**
     * 退款状态流转使用 compare-and-set 更新，避免重复点击导致状态回退。
     */
    private RefundListItemDTO changeStatus(RefundActionRequestDTO request,
                                           String fromStatus,
                                           String status,
                                           String statusType,
                                           boolean success) {
        String refundOrderId = requireRefundOrderId(request);
        int affectedRows = refundMapper.updateRefundStatus(refundOrderId, fromStatus, status, statusType, success);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("退款单状态不允许执行当前操作");
        }
        return refundMapper.findByRefundOrderId(refundOrderId);
    }

    private String requireRefundOrderId(RefundActionRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRefundOrderId())) {
            throw new IllegalArgumentException("退款单号不能为空");
        }
        return request.getRefundOrderId().trim();
    }
}
