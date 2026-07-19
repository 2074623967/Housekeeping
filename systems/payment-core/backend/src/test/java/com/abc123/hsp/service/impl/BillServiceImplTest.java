package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.mapper.BillMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 账单分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class BillServiceImplTest {

    @Mock
    private BillMapper billMapper;

    @Test
    void shouldNormalizeAndForwardBillPagingQuery() {
        BillQueryDTO query = new BillQueryDTO();
        query.setBillNo(" BILL-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setBillStatus("待支付");
        query.setPageNo(2);
        query.setPageSize(50);

        new BillServiceImpl(billMapper).list(query);

        verify(billMapper).findAll(query);
        verify(billMapper).count(query);
    }
}
