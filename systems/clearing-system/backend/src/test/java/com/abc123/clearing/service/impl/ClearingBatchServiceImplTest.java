package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.clearing.dto.ClearingBatchDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.RerunClearingBatchRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 清分批次服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ClearingBatchServiceImplTest {

    @Autowired
    private ClearingBatchServiceImpl clearingBatchService;

    @Test
    void shouldCreateNextVersionBatchWhenRerun() {
        RerunClearingBatchRequestDTO request = new RerunClearingBatchRequestDTO();
        request.setOperatorName("清分运营");
        request.setReason("补偿重跑");

        ClearingBatchDTO result = clearingBatchService.rerun("CLB10001", request);
        PageResultDTO<ClearingBatchDTO> pageResult = clearingBatchService.list("", "", 1, 20);

        assertEquals("V2", result.getVersionNo());
        assertEquals("已完成", result.getBatchStatus());
        assertEquals(2, pageResult.getTotal());
    }
}
