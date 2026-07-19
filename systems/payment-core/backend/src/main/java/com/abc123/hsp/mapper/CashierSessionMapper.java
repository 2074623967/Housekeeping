package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
import java.util.List;

/**
 * 收银台会话 Mapper，负责预付单会话查询。
 */
public interface CashierSessionMapper {

    /**
     * 查询收银台会话列表。
     *
     * @return 收银台会话列表
     */
    List<CashierSessionListItemDTO> findAll(CashierSessionQueryDTO query);
}
