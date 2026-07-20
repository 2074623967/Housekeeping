package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付交易异常中心 Mapper。
 */
public interface PaymentIssueMapper {

    /**
     * 查询支付交易异常列表。
     */
    List<PaymentIssueRowDTO> findAll(@Param("query") PaymentIssueQueryDTO query);

    /**
     * 统计支付交易异常数量。
     */
    long count(@Param("query") PaymentIssueQueryDTO query);
}
