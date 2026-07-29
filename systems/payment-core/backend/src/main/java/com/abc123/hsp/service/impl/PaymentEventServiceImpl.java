package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.dto.PaymentEventRepublishRequestDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.service.PaymentEventService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付事件出站服务实现。
 */
@Service
public class PaymentEventServiceImpl implements PaymentEventService {

    private final PaymentEventMapper paymentEventMapper;
    private final PaymentEventDispatchService paymentEventDispatchService;

    @Autowired
    public PaymentEventServiceImpl(PaymentEventMapper paymentEventMapper,
                                   PaymentEventDispatchService paymentEventDispatchService) {
        this.paymentEventMapper = paymentEventMapper;
        this.paymentEventDispatchService = paymentEventDispatchService;
    }

    PaymentEventServiceImpl(PaymentEventMapper paymentEventMapper) {
        this(paymentEventMapper, new PaymentEventDispatchService() {
            @Override
            public void publishPaymentSuccess(String eventNo, String paymentOrderId) {
                // 单元测试兼容构造器默认不触发下游联动。
            }

            @Override
            public boolean republish(String eventNo) {
                return paymentEventMapper.markRepublished(eventNo) > 0;
            }
        });
    }

    @Override
    public PageResultDTO<PaymentEventListItemDTO> list(PaymentEventQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                paymentEventMapper.findAll(query),
                paymentEventMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    @Transactional
    public PageResultDTO<PaymentEventListItemDTO> republish(
            PaymentEventRepublishRequestDTO request,
            PaymentEventQueryDTO query) {
        if (request == null || !StringUtils.hasText(request.getEventNo())) {
            throw new IllegalArgumentException("事件号不能为空");
        }
        boolean published = paymentEventDispatchService.republish(request.getEventNo().trim());
        if (!published) {
            throw new IllegalArgumentException("支付事件不存在");
        }
        return list(query == null ? new PaymentEventQueryDTO() : query);
    }

    private void normalizeQuery(PaymentEventQueryDTO query) {
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setEventType(query.getEventType() == null ? "全部" : query.getEventType().trim());
        query.setPublishStatus(query.getPublishStatus() == null ? "全部" : query.getPublishStatus().trim());
        query.setDownstreamSystem(query.getDownstreamSystem() == null ? "全部" : query.getDownstreamSystem().trim());
        query.setEventTopic(query.getEventTopic() == null ? null : query.getEventTopic().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }
}
