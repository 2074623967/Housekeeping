package com.abc123.refund.service.impl;

import com.abc123.refund.common.BusinessException;
import com.abc123.refund.dao.RefundDao;
import com.abc123.refund.dto.PageResultDTO;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundDetailDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOverviewDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.entity.PaymentSuccessProjectionEntity;
import com.abc123.refund.entity.RefundOrderEntity;
import com.abc123.refund.service.RefundService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 退款中心业务编排实现。
 *
 * <p>所有状态更新均使用期望状态条件，避免重复点击或重复回调造成状态回退。</p>
 */
@Service
public class RefundServiceImpl implements RefundService {

    private static final String REVIEWING = "REVIEWING";
    private static final String APPROVED = "APPROVED";
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAIL = "FAIL";
    private static final DateTimeFormatter ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RefundDao refundDao;
    private final String operator;

    public RefundServiceImpl(RefundDao refundDao,
                             @Value("${refund-center.operator:refund-center-admin}") String operator) {
        this.refundDao = refundDao;
        this.operator = operator;
    }

    @Override
    public PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query) {
        query = normalize(query);
        return new PageResultDTO<>(
                refundDao.findList(query),
                refundDao.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public RefundDetailDTO detail(String refundOrderId) {
        String id = required(refundOrderId, "退款单号不能为空");
        RefundListItemDTO item = refundDao.findByRefundOrderId(id);
        if (item == null) {
            throw new BusinessException("退款单不存在");
        }
        RefundDetailDTO detail = new RefundDetailDTO();
        detail.setRefundOrderId(item.getRefundOrderId());
        detail.setPaymentOrderId(item.getPaymentOrderId());
        detail.setOrderNo(item.getOrderNo());
        detail.setCustomerName(item.getCustomerName());
        detail.setPaidAmount(item.getPaidAmount());
        detail.setRefundAmount(item.getRefundAmount());
        detail.setRefundMethod(item.getRefundMethod());
        detail.setRefundReason(item.getRefundReason());
        detail.setStatus(item.getStatus());
        detail.setChannelRefundId(item.getChannelRefundId());
        detail.setAppliedAt(item.getAppliedAt());
        detail.setSuccessAt(item.getSuccessAt());
        detail.setOperationLogs(refundDao.findLogs(id));
        return detail;
    }

    @Override
    public RefundOverviewDTO overview() {
        return refundDao.overview();
    }

    @Override
    @Transactional
    public RefundListItemDTO apply(RefundApplyRequestDTO request) {
        if (request == null) {
            throw new BusinessException("退款申请不能为空");
        }
        String paymentOrderId = required(request.getPaymentOrderId(), "原支付单号不能为空");
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款金额必须大于0");
        }
        PaymentSuccessProjectionEntity source = refundDao.findPaymentSource(paymentOrderId);
        if (source == null) {
            throw new BusinessException("未找到支付成功事实，禁止发起退款");
        }
        BigDecimal refunded = refundDao.sumActiveRefundAmount(paymentOrderId);
        if (refunded == null) {
            refunded = BigDecimal.ZERO;
        }
        if (refunded.add(request.getRefundAmount()).compareTo(source.getPaidAmount()) > 0) {
            throw new BusinessException("累计退款金额不能超过原支付金额");
        }
        RefundOrderEntity entity = new RefundOrderEntity();
        entity.setRefundOrderId("REF" + LocalDateTime.now().format(ID_FORMATTER));
        entity.setPaymentOrderId(paymentOrderId);
        entity.setOrderNo(source.getOrderNo());
        entity.setCustomerName(source.getCustomerName());
        entity.setPaidAmount(source.getPaidAmount());
        entity.setRefundAmount(request.getRefundAmount());
        entity.setRefundMethod(StringUtils.hasText(request.getRefundMethod()) ? request.getRefundMethod().trim() : "ORIGINAL");
        entity.setRefundReason(StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason().trim() : "客户申请退款");
        entity.setStatus(REVIEWING);
        entity.setIdempotencyKey(StringUtils.hasText(request.getIdempotencyKey())
                ? request.getIdempotencyKey().trim() : UUID.randomUUID().toString());
        RefundListItemDTO existing = refundDao.findByIdempotencyKey(entity.getIdempotencyKey());
        if (existing != null) {
            return existing;
        }
        if (refundDao.insertRefund(entity) != 1) {
            throw new BusinessException("退款申请重复或写入失败");
        }
        refundDao.insertLog(entity.getRefundOrderId(), "APPLY", "发起退款申请",
                "INIT", REVIEWING, operator, entity.getRefundReason());
        return refundDao.findByRefundOrderId(entity.getRefundOrderId());
    }

    @Override
    @Transactional
    public RefundListItemDTO approve(RefundActionRequestDTO request) {
        return transition(request, REVIEWING, APPROVED, "APPROVE", "审核通过");
    }

    @Override
    @Transactional
    public RefundListItemDTO submit(RefundActionRequestDTO request) {
        return transition(request, APPROVED, PROCESSING, "SUBMIT", "提交退款渠道");
    }

    @Override
    @Transactional
    public RefundListItemDTO callback(RefundCallbackRequestDTO request) {
        if (request == null) {
            throw new BusinessException("退款回调不能为空");
        }
        String id = required(request.getRefundOrderId(), "退款单号不能为空");
        String target = "SUCCESS".equalsIgnoreCase(request.getResult()) ? SUCCESS : FAIL;
        int affected = refundDao.updateCallback(id, target, trim(request.getChannelRefundId()),
                trim(request.getFailureCode()));
        if (affected == 0) {
            RefundListItemDTO existing = refundDao.findByRefundOrderId(id);
            if (existing == null) {
                throw new BusinessException("退款单不存在");
            }
            if (SUCCESS.equals(target) && SUCCESS.equals(existing.getStatus())) {
                return existing;
            }
            if (FAIL.equals(target) && FAIL.equals(existing.getStatus())) {
                return existing;
            }
            throw new BusinessException("退款单不在可回调状态，或回调已处理");
        }
        refundDao.insertLog(id, "CALLBACK_" + target, "渠道退款回调",
                PROCESSING, target, "channel-callback",
                StringUtils.hasText(request.getRawMessage()) ? request.getRawMessage().trim() : target);
        if (SUCCESS.equals(target)) {
            RefundListItemDTO refund = refundDao.findByRefundOrderId(id);
            refundDao.insertSuccessOutbox(id, refund.getPaymentOrderId(), refund.getRefundAmount());
            return refund;
        }
        return refundDao.findByRefundOrderId(id);
    }

    @Override
    @Transactional
    public RefundListItemDTO retry(RefundActionRequestDTO request) {
        return transition(request, FAIL, PROCESSING, "RETRY", "失败退款重新提交");
    }

    @Override
    @Transactional
    public void projectPaymentSuccess(PaymentSuccessProjectionDTO request) {
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())
                || request.getPaidAmount() == null
                || request.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("支付成功事实缺少必要字段");
        }
        PaymentSuccessProjectionEntity entity = new PaymentSuccessProjectionEntity();
        entity.setPaymentOrderId(request.getPaymentOrderId().trim());
        entity.setOrderNo(trim(request.getOrderNo()));
        entity.setCustomerName(trim(request.getCustomerName()));
        entity.setPaidAmount(request.getPaidAmount());
        entity.setChannelCode(trim(request.getChannelCode()));
        entity.setPaidAt(LocalDateTime.now());
        refundDao.insertPaymentSource(entity);
    }

    private RefundListItemDTO transition(RefundActionRequestDTO request, String from, String to,
                                         String actionCode, String actionName) {
        if (request == null) {
            throw new BusinessException("退款操作不能为空");
        }
        String id = required(request.getRefundOrderId(), "退款单号不能为空");
        int affected = refundDao.updateStatus(id, from, to, null);
        if (affected == 0) {
            throw new BusinessException("退款单状态不允许执行当前操作");
        }
        refundDao.insertLog(id, actionCode, actionName, from, to, operator,
                StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : actionName);
        return refundDao.findByRefundOrderId(id);
    }

    private RefundQueryDTO normalize(RefundQueryDTO query) {
        if (query == null) {
            query = new RefundQueryDTO();
        }
        query.setRefundOrderId(trim(query.getRefundOrderId()));
        query.setPaymentOrderId(trim(query.getPaymentOrderId()));
        query.setStatus(StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : null);
        query.setRefundMethod(StringUtils.hasText(query.getRefundMethod()) ? query.getRefundMethod().trim() : null);
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return query;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
