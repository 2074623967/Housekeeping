package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.dto.PaymentLogQueryDTO;
import java.util.List;

/**
 * 支付处理日志 Service，面向支付运营和异常排查。
 */
public interface PaymentLogService {

    /**
     * 查询支付处理日志。
     *
     * @return 支付处理日志列表
     */
    List<PaymentLogListItemDTO> list(PaymentLogQueryDTO query);
}
