package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.BillListItemDTO;
import com.abc123.hsp.dto.BillQueryDTO;
import java.util.List;

/**
 * 账单中心 Mapper，负责账单列表查询。
 */
public interface BillMapper {

    /**
     * 查询账单中心列表。
     *
     * @return 账单列表
     */
    List<BillListItemDTO> findAll(BillQueryDTO query);

    /**
     * 统计符合条件的账单总数。
     */
    long count(BillQueryDTO query);
}
