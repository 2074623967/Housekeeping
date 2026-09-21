package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentEventDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventOverviewDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付事件出站 Mapper。
 */
public interface PaymentEventMapper {

    /**
     * 按事件号查询支付事件详情。
     */
    PaymentEventDetailDTO findDetail(@Param("eventNo") String eventNo);

    /**
     * 查询支付事件出站列表。
     */
    List<PaymentEventListItemDTO> findAll(@Param("query") PaymentEventQueryDTO query);

    /**
     * 查询导出用的支付事件出站列表。
     */
    List<PaymentEventListItemDTO> findAllForExport(@Param("query") PaymentEventQueryDTO query);

    /**
     * 查询支付事件出站总览。
     */
    PaymentEventOverviewDTO findOverview(@Param("query") PaymentEventQueryDTO query);

    /**
     * 统计支付事件出站总数。
     */
    long count(@Param("query") PaymentEventQueryDTO query);

    /**
     * 查询所有可人工介入的失败事件号。
     */
    List<String> findAllFailedEventNos(@Param("batchSize") int batchSize);

    /**
     * 查询已到自动重试时间的失败事件号。
     */
    List<String> findDueFailedEventNos(@Param("batchSize") int batchSize);

    /**
     * 按事件号查询支付事件。
     */
    PaymentEventListItemDTO findByEventNo(@Param("eventNo") String eventNo);

    /**
     * 按事件号锁定待派发事件，避免多实例并发重复调用渠道。
     */
    PaymentEventListItemDTO findByEventNoForUpdate(@Param("eventNo") String eventNo);

    /**
     * 标记事件发布成功。
     */
    int markPublishSuccess(@Param("eventNo") String eventNo);

    /**
     * 记录支付成功事件已被清分系统接受。
     */
    int markPaymentSuccessClearingAccepted(@Param("eventNo") String eventNo);

    /**
     * 记录支付成功事件的 HTTP 下游均已接受。
     */
    int markPaymentSuccessHttpCompleted(@Param("eventNo") String eventNo);

    /**
     * 标记事件发布失败。
     */
    int markPublishFailed(@Param("eventNo") String eventNo);

    /**
     * 标记事件进入死信状态。
     */
    int markPublishDeadLetter(@Param("eventNo") String eventNo);

    /**
     * 标记外部处理结果未知，停止所有直接重试。
     */
    int markPublishUnknown(@Param("eventNo") String eventNo);

    /**
     * 记录最近一次成功重发的来源身份。
     */
    int updateLastRepublishAudit(@Param("eventNo") String eventNo,
                                 @Param("operator") String operator);

    /**
     * 兼容旧测试和旧调用方的重发标记方法。
     */
    default int markRepublished(@Param("eventNo") String eventNo) {
        return markPublishSuccess(eventNo);
    }
}
