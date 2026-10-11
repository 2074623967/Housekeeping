package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentEventDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventOverviewDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.dto.PaymentEventRepublishRequestDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.service.PaymentEventService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.abc123.hsp.service.support.QuerySortInputNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付事件出站服务实现。
 */
@Service
public class PaymentEventServiceImpl implements PaymentEventService {

    private static final int OPERATOR_MAX_LENGTH = 64;
    private static final int EVENT_NO_MAX_LENGTH = 64;
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int EVENT_TYPE_MAX_LENGTH = 64;
    private static final int PUBLISH_STATUS_MAX_LENGTH = 32;
    private static final int DOWNSTREAM_SYSTEM_MAX_LENGTH = 256;
    private static final int EVENT_TOPIC_MAX_LENGTH = 128;

    private final PaymentEventMapper paymentEventMapper;
    private final PaymentEventDispatchService paymentEventDispatchService;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public PaymentEventServiceImpl(PaymentEventMapper paymentEventMapper,
                                   PaymentEventDispatchService paymentEventDispatchService,
                                   PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.paymentEventMapper = paymentEventMapper;
        this.paymentEventDispatchService = paymentEventDispatchService;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    PaymentEventServiceImpl(PaymentEventMapper paymentEventMapper,
                            PaymentEventDispatchService paymentEventDispatchService) {
        this(paymentEventMapper, paymentEventDispatchService,
                new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    PaymentEventServiceImpl(PaymentEventMapper paymentEventMapper) {
        this(paymentEventMapper, new PaymentEventDispatchService() {
            @Override
            public void publishPaymentSuccess(String eventNo, String paymentOrderId) {
                // 单元测试兼容构造器默认不触发下游联动。
            }

            @Override
            public boolean republish(String eventNo, String operator) {
                return paymentEventMapper.markRepublished(eventNo) == 1;
            }
        }, new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PaymentEventDetailDTO detail(String eventNo) {
        String normalizedEventNo = normalizeRequiredField(eventNo, EVENT_NO_MAX_LENGTH, "事件号");
        PaymentEventDetailDTO detail = paymentEventMapper.findDetail(normalizedEventNo);
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_EVENT_NOT_FOUND, "支付事件不存在");
        }
        PaymentEventDetailDTO outwardDetail = new PaymentEventDetailDTO();
        BeanUtils.copyProperties(detail, outwardDetail);
        outwardDetail.setEventPayload(sensitiveDataMasker.maskPayload(detail.getEventPayload()));
        return outwardDetail;
    }

    @Override
    public PageResultDTO<PaymentEventListItemDTO> list(PaymentEventQueryDTO query) {
        query = normalizeQuery(query);
        return new PageResultDTO<>(
                maskEventItems(paymentEventMapper.findAll(query)),
                paymentEventMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public PaymentEventOverviewDTO overview(PaymentEventQueryDTO query) {
        query = normalizeQuery(query);
        PaymentEventOverviewDTO overview = paymentEventMapper.findOverview(query);
        if (overview == null) {
            overview = new PaymentEventOverviewDTO();
        }
        if (overview.getTotalEventCount() == null) {
            overview.setTotalEventCount(0L);
        }
        if (overview.getSuccessEventCount() == null) {
            overview.setSuccessEventCount(0L);
        }
        if (overview.getPendingEventCount() == null) {
            overview.setPendingEventCount(0L);
        }
        if (overview.getFailedEventCount() == null) {
            overview.setFailedEventCount(0L);
        }
        if (overview.getDeadLetterEventCount() == null) {
            overview.setDeadLetterEventCount(0L);
        }
        if (overview.getFailedOrDeadLetterCount() == null) {
            overview.setFailedOrDeadLetterCount(0L);
        }
        if (overview.getDistinctDownstreamCount() == null) {
            overview.setDistinctDownstreamCount(0);
        }
        if (overview.getDueRetryEventCount() == null) {
            overview.setDueRetryEventCount(0);
        }
        if (overview.getPaymentSuccessEventCount() == null) {
            overview.setPaymentSuccessEventCount(0);
        }
        return overview;
    }

    @Override
    public String exportCsv(PaymentEventQueryDTO query) {
        query = normalizeQuery(query);
        List<PaymentEventListItemDTO> items = maskEventItems(
                CsvExportRowLimiter.requireWithinLimit(paymentEventMapper.findAllForExport(query)));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("事件号,事件类型,支付单号,业务链路,业务单号,事件主题,下游系统,发布状态,发布状态样式,HTTP投递阶段,重试次数,最近发布时间,下次重试时间,最近重发操作人,最近重发时间,事件摘要,创建时间,支付单成功事实异常类型\n");
        for (PaymentEventListItemDTO item : items) {
            builder.append(csvCell(item.getEventNo())).append(',')
                    .append(csvCell(item.getEventType())).append(',')
                    .append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getBusinessChainId())).append(',')
                    .append(csvCell(item.getBizNo())).append(',')
                    .append(csvCell(item.getEventTopic())).append(',')
                    .append(csvCell(item.getDownstreamSystem())).append(',')
                    .append(csvCell(item.getPublishStatus())).append(',')
                    .append(csvCell(item.getPublishStatusType())).append(',')
                    .append(csvCell(item.getPaymentSuccessHttpStage())).append(',')
                    .append(csvCell(item.getRetryCount() == null ? null : String.valueOf(item.getRetryCount()))).append(',')
                    .append(csvCell(item.getLastPublishedAt())).append(',')
                    .append(csvCell(item.getNextRetryAt())).append(',')
                    .append(csvCell(item.getLastRepublishOperator())).append(',')
                    .append(csvCell(item.getLastRepublishedAt())).append(',')
                    .append(csvCell(item.getEventPayload())).append(',')
                    .append(csvCell(item.getCreatedAt())).append(',')
                    .append(csvCell(item.getSuccessFactIssue())).append('\n');
        }
        return builder.toString();
    }

    @Override
    @Transactional(noRollbackFor = RepublishDispatchFailedException.class)
    public PageResultDTO<PaymentEventListItemDTO> republish(
            PaymentEventRepublishRequestDTO request,
            PaymentEventQueryDTO query) {
        if (request == null) {
            throw new IllegalArgumentException("事件号不能为空");
        }
        String eventNo = normalizeRequiredField(
                request.getEventNo(), EVENT_NO_MAX_LENGTH, "事件号");
        String operator = normalizeOperator(request.getOperator());
        PaymentEventQueryDTO normalizedQuery = normalizeQuery(query);
        boolean published = paymentEventDispatchService.republish(eventNo, operator);
        if (!published) {
            throw new RepublishDispatchFailedException("支付事件不存在、不可重发或投递失败");
        }
        if (paymentEventMapper.updateLastRepublishAudit(eventNo, operator) != 1) {
            throw new IllegalStateException("支付事件重发审计写入失败");
        }
        return list(normalizedQuery);
    }

    private static final class RepublishDispatchFailedException extends IllegalArgumentException {
        private RepublishDispatchFailedException(String message) {
            super(message);
        }
    }

    private String normalizeOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            throw new IllegalArgumentException("事件重发操作人不能为空");
        }
        for (int index = 0; index < operator.length(); index++) {
            if (Character.isISOControl(operator.charAt(index))) {
                throw new IllegalArgumentException("事件重发操作人不能包含控制字符");
            }
        }
        String normalized = operator.trim();
        if (normalized.length() > OPERATOR_MAX_LENGTH) {
            throw new IllegalArgumentException("事件重发操作人不能超过64个字符");
        }
        return normalized;
    }

    private PaymentEventQueryDTO normalizeQuery(PaymentEventQueryDTO query) {
        if (query == null) {
            query = new PaymentEventQueryDTO();
        }
        query.setPaymentOrderId(normalizeOptionalQueryField(
                query.getPaymentOrderId(), PAYMENT_ORDER_ID_MAX_LENGTH, "支付单号", null));
        query.setEventType(normalizeOptionalQueryField(
                query.getEventType(), EVENT_TYPE_MAX_LENGTH, "事件类型", "全部"));
        query.setPublishStatus(normalizeOptionalQueryField(
                query.getPublishStatus(), PUBLISH_STATUS_MAX_LENGTH, "发布状态", "全部"));
        query.setDownstreamSystem(normalizeOptionalQueryField(
                query.getDownstreamSystem(), DOWNSTREAM_SYSTEM_MAX_LENGTH, "下游系统", "全部"));
        query.setEventTopic(normalizeOptionalQueryField(
                query.getEventTopic(), EVENT_TOPIC_MAX_LENGTH, "事件主题", null));
        query.setSortField(QuerySortInputNormalizer.normalizeSortField(query.getSortField(), "createdAt"));
        query.setSortOrder(QuerySortInputNormalizer.normalizeSortOrder(query.getSortOrder(), "desc"));
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return query;
    }

    private String normalizeOptionalQueryField(
            String value, int maxLength, String fieldName, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        validateNoIsoControl(value, fieldName);
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String normalizeRequiredField(String value, int maxLength, String fieldName) {
        if (value != null) {
            validateNoIsoControl(value, fieldName);
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private void validateNoIsoControl(String value, String fieldName) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + "不能包含控制字符");
            }
        }
    }

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private List<PaymentEventListItemDTO> maskEventItems(List<PaymentEventListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().filter(item -> item != null)
                .map(this::maskedEventCopy).collect(Collectors.toList());
    }

    private PaymentEventListItemDTO maskedEventCopy(PaymentEventListItemDTO item) {
        if (item == null) {
            return null;
        }
        PaymentEventListItemDTO outwardItem = new PaymentEventListItemDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setEventPayload(sensitiveDataMasker.maskPayload(item.getEventPayload()));
        return outwardItem;
    }
}
