package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 收款记录 Mapper，负责统一收款记录及渠道维度记录查询。
 */
public interface PaymentRecordMapper {

    /**
     * 查询收款记录。
     *
     * @param recordType 记录类型，ALL、WECHAT、BANK_CARD
     * @return 收款记录列表
     */
    List<PaymentRecordRowDTO> findAll(@Param("query") PaymentRecordQueryDTO query);

    /**
     * 统计符合条件的收款记录总数。
     */
    long count(@Param("query") PaymentRecordQueryDTO query);
}
