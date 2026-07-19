package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.dto.PaymentLogQueryDTO;
import java.util.List;

/**
 * 支付处理日志 Mapper。
 */
public interface PaymentLogMapper {

    /**
     * 查询支付处理日志。
     *
     * @return 支付处理日志列表
     */
    List<PaymentLogListItemDTO> findAll(PaymentLogQueryDTO query);
}
