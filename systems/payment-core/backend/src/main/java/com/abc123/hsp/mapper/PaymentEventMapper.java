package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付事件出站 Mapper。
 */
public interface PaymentEventMapper {

    /**
     * 查询支付事件出站列表。
     */
    List<PaymentEventListItemDTO> findAll(@Param("query") PaymentEventQueryDTO query);

    /**
     * 统计支付事件出站总数。
     */
    long count(@Param("query") PaymentEventQueryDTO query);

    /**
     * 查询失败事件号列表。
     */
    List<String> findFailedEventNos();

    /**
     * 手动重发事件，模拟事件重新投递成功。
     */
    int markRepublished(@Param("eventNo") String eventNo);
}
