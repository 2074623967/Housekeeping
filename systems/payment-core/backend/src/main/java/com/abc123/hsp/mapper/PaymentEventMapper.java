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
     * 按事件号查询支付事件。
     */
    PaymentEventListItemDTO findByEventNo(@Param("eventNo") String eventNo);

    /**
     * 标记事件发布成功。
     */
    int markPublishSuccess(@Param("eventNo") String eventNo);

    /**
     * 标记事件发布失败。
     */
    int markPublishFailed(@Param("eventNo") String eventNo);

    /**
     * 标记事件进入死信状态。
     */
    int markPublishDeadLetter(@Param("eventNo") String eventNo);

    /**
     * 兼容旧测试和旧调用方的重发标记方法。
     */
    default int markRepublished(@Param("eventNo") String eventNo) {
        return markPublishSuccess(eventNo);
    }
}
