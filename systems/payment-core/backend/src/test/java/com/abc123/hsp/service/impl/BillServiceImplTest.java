package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.mapper.BillMapper;
import java.util.Collections;
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
        query.setPageNo(0);
        query.setPageSize(500);

        org.mockito.Mockito.when(billMapper.findAll(query)).thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(billMapper.count(query)).thenReturn(0L);
        new BillServiceImpl(billMapper).list(query);

        org.junit.jupiter.api.Assertions.assertEquals(1, query.getPageNo());
        org.junit.jupiter.api.Assertions.assertEquals(100, query.getPageSize());
        verify(billMapper).findAll(query);
        verify(billMapper).count(query);
    }
}
