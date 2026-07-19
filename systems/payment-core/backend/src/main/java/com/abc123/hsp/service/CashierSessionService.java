package com.abc123.hsp.service;

import com.abc123.hsp.dto.CashierSessionListItemDTO;
import java.util.List;

/**
 * 收银台会话 Service，承接终端会话查询和失效识别。
 */
public interface CashierSessionService {

    /**
     * 查询收银台会话列表。
     *
     * @return 收银台会话列表
     */
    List<CashierSessionListItemDTO> list();
}
