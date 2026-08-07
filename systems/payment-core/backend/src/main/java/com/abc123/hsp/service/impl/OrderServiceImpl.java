package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.OrderMapper;
import com.abc123.hsp.service.OrderService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public PageResultDTO<OrderListItemDTO> list(OrderQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                orderMapper.findAll(query),
                orderMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(OrderQueryDTO query) {
        normalizeQuery(query);
        List<OrderListItemDTO> records = orderMapper.findForExport(query);
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

    /**
     * 后台运营列表统一在服务层收敛分页边界，避免前端传异常分页导致慢查询。
     */
    private void normalizeQuery(OrderQueryDTO query) {
        query.setOrderNo(StringUtils.hasText(query.getOrderNo()) ? query.getOrderNo().trim() : null);
        query.setServiceType(StringUtils.hasText(query.getServiceType()) ? query.getServiceType().trim() : "全部");
        query.setOrderStatus(StringUtils.hasText(query.getOrderStatus()) ? query.getOrderStatus().trim() : "全部");
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
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
