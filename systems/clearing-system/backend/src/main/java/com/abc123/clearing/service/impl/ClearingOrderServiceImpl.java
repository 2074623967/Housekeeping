package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.ClearingOrderDTO;
import com.abc123.clearing.dto.ClearingOrderDetailDTO;
import com.abc123.clearing.dto.FeeRuleDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.ShareItemDTO;
import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.service.ClearingOrderService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 清分结果服务实现。
 */
@Service
public class ClearingOrderServiceImpl implements ClearingOrderService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingMapper clearingMapper;

    public ClearingOrderServiceImpl(ClearingMemoryStore clearingMemoryStore, ClearingMapper clearingMapper) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingMapper = clearingMapper;
    }

    @Override
    public PageResultDTO<ClearingOrderDTO> list(String batchNo, String orderNo, String paymentOrderId, String clearingStatus, int pageNo, int pageSize) {
        List<ClearingOrderDTO> items = clearingMemoryStore.orders().stream()
                .filter(item -> batchNo == null || batchNo.isEmpty() || batchNo.equals(item.getBatchNo()))
                .filter(item -> orderNo == null || orderNo.isEmpty() || orderNo.equals(item.getOrderNo()))
                .filter(item -> paymentOrderId == null || paymentOrderId.isEmpty() || paymentOrderId.equals(item.getPaymentOrderId()))
                .filter(item -> clearingStatus == null || clearingStatus.isEmpty() || clearingStatus.equals(item.getClearingStatus()))
                .map(clearingMapper::toOrderDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public byte[] exportList(String batchNo, String orderNo, String paymentOrderId, String clearingStatus) {
        List<ClearingOrderDTO> items = list(batchNo, orderNo, paymentOrderId, clearingStatus, 1, Integer.MAX_VALUE).getItems();
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("清分单号,清分批次号,支付单号,订单号,订单金额,商家分账,服务者分账,平台分账,渠道费用,补贴金额,清分状态,状态样式,规则版本,创建时间\n");
        for (ClearingOrderDTO item : items) {
            builder.append(csv(item.getClearingNo())).append(',')
                    .append(csv(item.getBatchNo())).append(',')
                    .append(csv(item.getPaymentOrderId())).append(',')
                    .append(csv(item.getOrderNo())).append(',')
                    .append(csv(item.getOrderAmount())).append(',')
                    .append(csv(item.getMerchantAmount())).append(',')
                    .append(csv(item.getWorkerAmount())).append(',')
                    .append(csv(item.getPlatformAmount())).append(',')
                    .append(csv(item.getChannelFeeAmount())).append(',')
                    .append(csv(item.getSubsidyAmount())).append(',')
                    .append(csv(item.getClearingStatus())).append(',')
                    .append(csv(item.getClearingStatusType())).append(',')
                    .append(csv(item.getRuleNo())).append(',')
                    .append(csv(item.getCreatedAt())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ClearingOrderDetailDTO detail(String clearingNo) {
        ClearingOrderEntity order = clearingMemoryStore.findOrder(clearingNo);
        List<ShareItemDTO> shareItems = clearingMemoryStore.sharesByClearingNo(clearingNo).stream()
                .map(clearingMapper::toShareItemDTO)
                .collect(Collectors.toList());
        List<FeeRuleDTO> feeRules = clearingMemoryStore.feeRules().stream()
                .map(clearingMapper::toFeeRuleDTO)
                .collect(Collectors.toList());
        return clearingMapper.toOrderDetailDTO(order, shareItems, feeRules);
    }

    @Override
    public byte[] exportDetail(String clearingNo) {
        ClearingOrderDetailDTO detail = detail(clearingNo);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("清分结果详情快照\n");
        builder.append("清分单号,清分批次号,支付单号,订单号,订单金额,商家分账,服务者分账,平台分账,渠道费用,补贴金额,清分状态,状态样式,规则版本,创建时间\n");
        ClearingOrderDTO order = detail.getOrder();
        builder.append(csv(order.getClearingNo())).append(',')
                .append(csv(order.getBatchNo())).append(',')
                .append(csv(order.getPaymentOrderId())).append(',')
                .append(csv(order.getOrderNo())).append(',')
                .append(csv(order.getOrderAmount())).append(',')
                .append(csv(order.getMerchantAmount())).append(',')
                .append(csv(order.getWorkerAmount())).append(',')
                .append(csv(order.getPlatformAmount())).append(',')
                .append(csv(order.getChannelFeeAmount())).append(',')
                .append(csv(order.getSubsidyAmount())).append(',')
                .append(csv(order.getClearingStatus())).append(',')
                .append(csv(order.getClearingStatusType())).append(',')
                .append(csv(order.getRuleNo())).append(',')
                .append(csv(order.getCreatedAt())).append('\n');
        builder.append('\n');
        builder.append("费用规则快照\n");
        builder.append("费用规则号,费用名称,费用类型,费用模式,费率,固定金额,承担方,状态,状态样式,创建时间\n");
        for (FeeRuleDTO feeRule : detail.getFeeRules()) {
            builder.append(csv(feeRule.getFeeRuleNo())).append(',')
                    .append(csv(feeRule.getFeeName())).append(',')
                    .append(csv(feeRule.getFeeType())).append(',')
                    .append(csv(feeRule.getFeeMode())).append(',')
                    .append(csv(feeRule.getFeeRate())).append(',')
                    .append(csv(feeRule.getFixedAmount())).append(',')
                    .append(csv(feeRule.getFeeBearer())).append(',')
                    .append(csv(feeRule.getStatus())).append(',')
                    .append(csv(feeRule.getStatusType())).append(',')
                    .append(csv(feeRule.getCreatedAt())).append('\n');
        }
        builder.append('\n');
        builder.append("分账明细\n");
        builder.append("分账明细号,清分单号,分账类型,对象编号,对象名称,分账金额,状态,状态样式,创建时间\n");
        for (ShareItemDTO shareItem : detail.getShareItems()) {
            builder.append(csv(shareItem.getShareItemNo())).append(',')
                    .append(csv(shareItem.getClearingNo())).append(',')
                    .append(csv(shareItem.getShareType())).append(',')
                    .append(csv(shareItem.getShareTargetNo())).append(',')
                    .append(csv(shareItem.getShareTargetName())).append(',')
                    .append(csv(shareItem.getShareAmount())).append(',')
                    .append(csv(shareItem.getShareStatus())).append(',')
                    .append(csv(shareItem.getStatusType())).append(',')
                    .append(csv(shareItem.getCreatedAt())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private PageResultDTO<ClearingOrderDTO> page(List<ClearingOrderDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
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
