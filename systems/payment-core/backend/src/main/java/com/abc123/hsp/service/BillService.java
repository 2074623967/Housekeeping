package com.abc123.hsp.service;

import com.abc123.hsp.dto.BillListItemDTO;
import java.util.List;

/**
 * 账单中心 Service，提供交易账单视角查询能力。
 */
public interface BillService {

    /**
     * 查询账单中心列表。
     *
     * @return 账单列表
     */
    List<BillListItemDTO> list();
}
