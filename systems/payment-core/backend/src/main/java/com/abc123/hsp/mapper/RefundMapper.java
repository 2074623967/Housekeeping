package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundPaymentSourceDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RefundMapper {

    /**
     * 查询退款单分页列表。
     */
    List<RefundListItemDTO> findAll(@Param("query") RefundQueryDTO query);

    /**
     * 统计符合条件的退款单总数。
     */
    long count(@Param("query") RefundQueryDTO query);

    /**
     * 查询失败退款单号列表。
     */
    List<String> findFailedRefundOrderIds();

    /**
     * 查询退款单详情。
     */
    RefundListItemDTO findByRefundOrderId(String refundOrderId);

    /**
     * 查询可退款的原支付单快照。
     */
    RefundPaymentSourceDTO findPaymentSource(String paymentOrderId);

    /**
     * 汇总原支付单已申请且未终止的退款金额。
     */
    BigDecimal sumActiveRefundAmount(String paymentOrderId);

    /**
     * 插入退款申请单。
     */
    void insertRefund(@Param("refundOrderId") String refundOrderId,
                      @Param("source") RefundPaymentSourceDTO source,
                      @Param("request") RefundApplyRequestDTO request,
                      @Param("status") String status,
                      @Param("statusType") String statusType);

    /**
     * 更新退款单状态。
     */
    int updateRefundStatus(@Param("refundOrderId") String refundOrderId,
                           @Param("fromStatus") String fromStatus,
                           @Param("status") String status,
                           @Param("statusType") String statusType,
                           @Param("success") boolean success);
}
