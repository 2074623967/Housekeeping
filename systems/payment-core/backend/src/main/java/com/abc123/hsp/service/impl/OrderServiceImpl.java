package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.OrderDetailDTO;
import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.OrderMapper;
import com.abc123.hsp.service.OrderService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int ORDER_NO_MAX_LENGTH = 64;
    private static final int SERVICE_TYPE_MAX_LENGTH = 64;
    private static final int ORDER_STATUS_MAX_LENGTH = 32;

    private final OrderMapper orderMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public OrderServiceImpl(OrderMapper orderMapper, PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.orderMapper = orderMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    OrderServiceImpl(OrderMapper orderMapper) {
        this(orderMapper, new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public OrderDetailDTO detail(String orderNo) {
        String normalizedOrderNo = normalizeRequiredOrderNo(orderNo);
        OrderDetailDTO detail = orderMapper.findDetail(normalizedOrderNo);
        if (detail == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        OrderDetailDTO outwardDetail = new OrderDetailDTO();
        BeanUtils.copyProperties(detail, outwardDetail);
        outwardDetail.setCustomerName(sensitiveDataMasker.maskName(detail.getCustomerName()));
        outwardDetail.setWorkerName(sensitiveDataMasker.maskName(detail.getWorkerName()));
        return outwardDetail;
    }

    @Override
    public PageResultDTO<OrderListItemDTO> list(OrderQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(orderMapper.findAll(query)),
                orderMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(OrderQueryDTO query) {
        normalizeQuery(query);
        List<OrderListItemDTO> records = maskItems(
                CsvExportRowLimiter.requireWithinLimit(orderMapper.findForExport(query)));
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("订单号,账单号,客户名称,服务品类,服务者,订单金额,已付金额,订单状态,履约状态,最近支付状态,最近收银台状态,创建时间\n");
        for (OrderListItemDTO item : records) {
            builder.append(csv(item.getOrderNo())).append(',')
                    .append(csv(item.getBillNo())).append(',')
                    .append(csv(item.getCustomerName())).append(',')
                    .append(csv(item.getServiceType())).append(',')
                    .append(csv(item.getWorkerName())).append(',')
                    .append(csv(item.getOrderAmount())).append(',')
                    .append(csv(item.getPaidAmount())).append(',')
                    .append(csv(item.getOrderStatus())).append(',')
                    .append(csv(item.getFulfillmentStatus())).append(',')
                    .append(csv(item.getLatestPaymentStatus())).append(',')
                    .append(csv(item.getLatestCashierStatus())).append(',')
                    .append(csv(item.getCreatedAt())).append('\n');
        }
        return builder.toString();
    }

    private List<OrderListItemDTO> maskItems(List<OrderListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private OrderListItemDTO maskedCopy(OrderListItemDTO item) {
        if (item == null) {
            return null;
        }
        OrderListItemDTO outwardItem = new OrderListItemDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setCustomerName(sensitiveDataMasker.maskName(item.getCustomerName()));
        outwardItem.setWorkerName(sensitiveDataMasker.maskName(item.getWorkerName()));
        return outwardItem;
    }

    /**
     * 后台运营列表统一在服务层收敛分页边界，避免前端传异常分页导致慢查询。
     */
    private void normalizeQuery(OrderQueryDTO query) {
        query.setOrderNo(normalizeOptionalQueryField(
                query.getOrderNo(), ORDER_NO_MAX_LENGTH, "订单号", null));
        query.setServiceType(normalizeOptionalQueryField(
                query.getServiceType(), SERVICE_TYPE_MAX_LENGTH, "服务品类", "全部"));
        query.setOrderStatus(normalizeOptionalQueryField(
                query.getOrderStatus(), ORDER_STATUS_MAX_LENGTH, "订单状态", "全部"));
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    private String normalizeOptionalQueryField(
            String value, int maxLength, String fieldName, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + "不能包含控制字符");
            }
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return defaultValue;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String normalizeRequiredOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException(ErrorCode.PAYMENT_REQUEST_INVALID, "订单号不能为空");
        }
        for (int index = 0; index < orderNo.length(); index++) {
            if (Character.isISOControl(orderNo.charAt(index))) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_REQUEST_INVALID, "订单号不能包含控制字符");
            }
        }
        String normalized = orderNo.trim();
        if (normalized.length() > ORDER_NO_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_REQUEST_INVALID, "订单号长度不能超过 64 个字符");
        }
        return normalized;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
