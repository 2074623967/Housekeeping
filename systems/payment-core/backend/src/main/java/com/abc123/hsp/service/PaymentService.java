package com.abc123.hsp.service;

import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCloseRequestDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import java.util.List;

/**
 * 支付核心服务，定义支付主链路和后台查询能力。
 */
public interface PaymentService {

    /**
     * 查询支付单列表。
     */
    List<PaymentListItemDTO> list();

    /**
     * 查询支付单详情和轨迹信息。
     */
    PaymentDetailDTO detail(String paymentOrderId);

    /**
     * 创建预付单。
     */
    PrepayOrderDTO prepay(PrepayRequestDTO request);

    /**
     * 获取收银台页面数据。
     */
    CashierPageDTO cashier(String prepayOrderNo);

    /**
     * 提交支付。
     */
    PrepayOrderDTO submit(PaymentSubmitRequestDTO request);

    /**
     * 处理渠道回调。
     */
    PaymentDetailDTO callback(String channel, PaymentCallbackRequestDTO request);

    /**
     * 主动查单。
     */
    PaymentDetailDTO query(PaymentQueryRequestDTO request);

    /**
     * 关闭支付单。
     */
    PaymentDetailDTO close(PaymentCloseRequestDTO request);
}
