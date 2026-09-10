package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundPaymentSourceDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.dto.RefundRetrySourceDTO;
import com.abc123.hsp.entity.RefundOperationLogEntity;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.RefundService;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefundServiceImpl.class);
    private static final String STATUS_REVIEWING = "REVIEWING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String PAYMENT_STATUS_SUCCESS = "SUCCESS";
    private static final DateTimeFormatter REFUND_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String DEFAULT_OPERATOR = "payment-core-admin";
    private static final String CALLBACK_OPERATOR = "channel-callback";

    private final RefundMapper refundMapper;
    private final PaymentEventDispatchService paymentEventDispatchService;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public RefundServiceImpl(RefundMapper refundMapper,
                             PaymentEventDispatchService paymentEventDispatchService,
                             PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.refundMapper = refundMapper;
        this.paymentEventDispatchService = paymentEventDispatchService;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    RefundServiceImpl(RefundMapper refundMapper,
                      PaymentEventDispatchService paymentEventDispatchService) {
        this(refundMapper, paymentEventDispatchService,
                new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(refundMapper.findAll(query)),
                refundMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(RefundQueryDTO query) {
        normalizeQuery(query);
        List<RefundListItemDTO> items = maskItems(refundMapper.findAllForExport(query));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("退款单号,支付单号,订单号,客户名称,退款金额,退款方式,退款状态,状态类型,申请时间,成功时间\n");
        for (RefundListItemDTO item : items) {
            builder.append(csvCell(item.getRefundOrderId())).append(',')
                    .append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getCustomerName())).append(',')
                    .append(csvCell(item.getRefundAmount())).append(',')
                    .append(csvCell(item.getRefundMethod())).append(',')
                    .append(csvCell(item.getStatus())).append(',')
                    .append(csvCell(item.getStatusType())).append(',')
                    .append(csvCell(item.getAppliedAt())).append(',')
                    .append(csvCell(item.getSuccessAt())).append('\n');
        }
        return builder.toString();
    }

    @Override
    public RefundDetailDTO detail(String refundOrderId) {
        String requiredRefundOrderId = requireRefundOrderId(refundOrderId);
        RefundDetailDTO detail = refundMapper.findDetailByRefundOrderId(requiredRefundOrderId);
        if (detail == null) {
            throw new IllegalArgumentException("退款单不存在");
        }
        RefundDetailDTO outwardDetail = new RefundDetailDTO();
        BeanUtils.copyProperties(detail, outwardDetail);
        outwardDetail.setCustomerName(sensitiveDataMasker.maskName(detail.getCustomerName()));
        outwardDetail.setOperationLogs(refundMapper.findOperationLogs(requiredRefundOrderId));
        return outwardDetail;
    }

    private List<RefundListItemDTO> maskItems(List<RefundListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private RefundListItemDTO maskedCopy(RefundListItemDTO item) {
        if (item == null) {
            return null;
        }
        RefundListItemDTO outwardItem = new RefundListItemDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setCustomerName(sensitiveDataMasker.maskName(item.getCustomerName()));
        return outwardItem;
    }

    @Override
    @Transactional
    public RefundListItemDTO apply(RefundApplyRequestDTO request) {
        validateApplyRequest(request);
        RefundPaymentSourceDTO source = refundMapper.findPaymentSourceForUpdate(request.getPaymentOrderId().trim());
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
        String refundOrderId = buildBusinessNo("REF");
        refundMapper.insertRefund(refundOrderId, source, request, STATUS_REVIEWING, "warn");
        refundMapper.insertOperationLog(buildOperationLog(
                refundOrderId,
                "APPLY",
                "发起退款申请",
                "INIT",
                STATUS_REVIEWING,
                DEFAULT_OPERATOR,
                StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason().trim() : "发起退款申请"
        ));
        return maskedCopy(refundMapper.findByRefundOrderId(refundOrderId));
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
        validateRetryCapacity(requireRefundOrderId(request));
        return changeStatus(request, STATUS_FAIL, STATUS_PROCESSING, "warn", false);
    }

    @Override
    @Transactional
    public void retryForTask(String refundOrderId, String triggeredBy) {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId(refundOrderId);
        request.setRemark("任务中心批量重试失败退款");
        validateRetryCapacity(requireRefundOrderId(refundOrderId));
        changeStatus(request,
                STATUS_FAIL,
                STATUS_PROCESSING,
                "warn",
                false,
                StringUtils.hasText(triggeredBy) ? triggeredBy.trim() : DEFAULT_OPERATOR,
                "TASK_RETRY",
                "任务中心失败退款重试");
    }

    private void validateRetryCapacity(String refundOrderId) {
        RefundRetrySourceDTO retrySource = refundMapper.findRetrySourceForUpdate(refundOrderId);
        if (retrySource == null || !STATUS_FAIL.equals(retrySource.getStatus())) {
            throw new IllegalArgumentException("退款单状态不允许执行当前操作");
        }
        if (!StringUtils.hasText(retrySource.getPaymentOrderId())
                || retrySource.getRefundAmount() == null
                || retrySource.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退款额度数据异常");
        }
        RefundPaymentSourceDTO paymentSource =
                refundMapper.findPaymentSourceForUpdate(retrySource.getPaymentOrderId());
        if (paymentSource == null) {
            throw new IllegalArgumentException("原支付单不存在");
        }
        if (paymentSource.getPaidAmount() == null
                || paymentSource.getPaidAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("退款额度数据异常");
        }
        BigDecimal activeRefundAmount = refundMapper.sumActiveRefundAmount(retrySource.getPaymentOrderId());
        BigDecimal reservedAmount = activeRefundAmount == null ? BigDecimal.ZERO : activeRefundAmount;
        if (reservedAmount.add(retrySource.getRefundAmount()).compareTo(paymentSource.getPaidAmount()) > 0) {
            throw new IllegalArgumentException("累计退款金额不能超过原支付金额");
        }
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
        return changeStatus(request, fromStatus, status, statusType, success,
                resolveOperator(status), resolveActionCode(fromStatus, status), resolveActionName(fromStatus, status));
    }

    private RefundListItemDTO changeStatus(RefundActionRequestDTO request,
                                           String fromStatus,
                                           String status,
                                           String statusType,
                                           boolean success,
                                           String operator,
                                           String actionCode,
                                           String actionName) {
        String refundOrderId = requireRefundOrderId(request);
        int affectedRows = refundMapper.updateRefundStatus(refundOrderId, fromStatus, status, statusType, success);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("退款单状态不允许执行当前操作");
        }
        refundMapper.insertOperationLog(buildOperationLog(
                refundOrderId,
                actionCode,
                actionName,
                fromStatus,
                status,
                operator,
                StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : resolveDefaultRemark(fromStatus, status)
        ));
        if (STATUS_PROCESSING.equals(status)) {
            enqueueChannelSubmit(refundOrderId);
        }
        return maskedCopy(refundMapper.findByRefundOrderId(refundOrderId));
    }

    private void enqueueChannelSubmit(String refundOrderId) {
        RefundChannelSubmitRequestDTO channelSubmitRequest = refundMapper.findChannelSubmitRequestByRefundOrderId(refundOrderId);
        if (channelSubmitRequest == null) {
            throw new IllegalArgumentException("退款单不存在或缺少渠道下发信息");
        }
        String eventNo = buildRefundSubmitEventNo(refundOrderId);
        refundMapper.upsertRefundSubmitEvent(eventNo, refundOrderId, channelSubmitRequest.getPaymentOrderId());
        dispatchAfterCommit(eventNo, refundOrderId);
    }

    private void dispatchAfterCommit(String eventNo, String refundOrderId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tryDispatch(eventNo, refundOrderId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tryDispatch(eventNo, refundOrderId);
            }
        });
    }

    private void tryDispatch(String eventNo, String refundOrderId) {
        try {
            paymentEventDispatchService.publishRefundSubmit(eventNo, refundOrderId);
        } catch (RuntimeException exception) {
            LOGGER.warn("退款渠道事件即时派发失败 eventNo={}, refundOrderId={}",
                    eventNo, refundOrderId, exception);
        }
    }

    private String buildRefundSubmitEventNo(String refundOrderId) {
        return "EVTR" + UUID.nameUUIDFromBytes(
                ("REFUND_SUBMIT:" + refundOrderId).getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    private String requireRefundOrderId(RefundActionRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRefundOrderId())) {
            throw new IllegalArgumentException("退款单号不能为空");
        }
        return request.getRefundOrderId().trim();
    }

    public static RefundOperationLogEntity buildOperationLog(String refundOrderId,
                                                             String actionCode,
                                                             String actionName,
                                                             String fromStatus,
                                                             String toStatus,
                                                             String operatorName,
                                                             String operationRemark) {
        RefundOperationLogEntity entity = new RefundOperationLogEntity();
        entity.setLogNo(buildBusinessNo("ROL"));
        entity.setRefundOrderId(refundOrderId);
        entity.setActionCode(actionCode);
        entity.setActionName(actionName);
        entity.setFromStatus(fromStatus);
        entity.setToStatus(toStatus);
        entity.setOperatorName(operatorName);
        entity.setOperationRemark(operationRemark);
        return entity;
    }

    private static String buildBusinessNo(String prefix) {
        return prefix
                + LocalDateTime.now().format(REFUND_NO_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String requireRefundOrderId(String refundOrderId) {
        if (!StringUtils.hasText(refundOrderId)) {
            throw new IllegalArgumentException("退款单号不能为空");
        }
        return refundOrderId.trim();
    }

    private String resolveActionCode(String fromStatus, String toStatus) {
        if (STATUS_REVIEWING.equals(fromStatus) && STATUS_PROCESSING.equals(toStatus)) {
            return "APPROVE";
        }
        if (STATUS_PROCESSING.equals(fromStatus) && STATUS_SUCCESS.equals(toStatus)) {
            return "SUCCESS";
        }
        if (STATUS_PROCESSING.equals(fromStatus) && STATUS_FAIL.equals(toStatus)) {
            return "FAIL";
        }
        if (STATUS_FAIL.equals(fromStatus) && STATUS_PROCESSING.equals(toStatus)) {
            return "RETRY";
        }
        return "UPDATE";
    }

    private String resolveActionName(String fromStatus, String toStatus) {
        if (STATUS_REVIEWING.equals(fromStatus) && STATUS_PROCESSING.equals(toStatus)) {
            return "审核通过";
        }
        if (STATUS_PROCESSING.equals(fromStatus) && STATUS_SUCCESS.equals(toStatus)) {
            return "退款成功回调";
        }
        if (STATUS_PROCESSING.equals(fromStatus) && STATUS_FAIL.equals(toStatus)) {
            return "退款失败回调";
        }
        if (STATUS_FAIL.equals(fromStatus) && STATUS_PROCESSING.equals(toStatus)) {
            return "退款失败重试";
        }
        return "退款状态更新";
    }

    private String resolveOperator(String toStatus) {
        if (STATUS_SUCCESS.equals(toStatus) || STATUS_FAIL.equals(toStatus)) {
            return CALLBACK_OPERATOR;
        }
        return DEFAULT_OPERATOR;
    }

    private String resolveDefaultRemark(String fromStatus, String toStatus) {
        return resolveActionName(fromStatus, toStatus);
    }

    private String csvCell(String value) {
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
