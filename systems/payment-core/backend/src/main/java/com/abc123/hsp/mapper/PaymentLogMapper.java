package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.dto.PaymentLogQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付处理日志 Mapper。
 */
public interface PaymentLogMapper {

    /**
     * 查询支付处理日志。
     *
     * @return 支付处理日志列表
     */
    List<PaymentLogListItemDTO> findAll(@Param("query") PaymentLogQueryDTO query);

    /**
     * 统计符合条件的支付处理日志总数。
     */
    long count(@Param("query") PaymentLogQueryDTO query);
}
