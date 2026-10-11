package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.clearing.dto.ClearingOrderDTO;
import com.abc123.clearing.dto.PageResultDTO;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 清分结果服务导出测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ClearingOrderServiceImplTest {

    @Autowired
    private ClearingOrderServiceImpl clearingOrderService;

    @Test
    void shouldExportClearingOrderListAndDetailSnapshots() {
        PageResultDTO<ClearingOrderDTO> result = clearingOrderService.list("", "", "", "", 1, 20);
        String clearingNo = result.getItems().get(0).getClearingNo();

        String listCsv = new String(clearingOrderService.exportList("", "", "", ""), StandardCharsets.UTF_8);
        String detailCsv = new String(clearingOrderService.exportDetail(clearingNo), StandardCharsets.UTF_8);

        assertTrue(listCsv.contains("清分单号,清分批次号,支付单号"));
        assertTrue(listCsv.contains("清分状态,状态样式,规则版本"));
        assertTrue(listCsv.contains(clearingNo));
        assertTrue(listCsv.contains("清分成功,success"));
        assertTrue(detailCsv.contains("清分结果详情快照"));
        assertTrue(detailCsv.contains("清分状态,状态样式,规则版本"));
        assertTrue(detailCsv.contains("费用规则快照"));
        assertTrue(detailCsv.contains("状态,状态样式,创建时间"));
        assertTrue(detailCsv.contains("分账明细"));
        assertTrue(detailCsv.contains("状态,状态样式,创建时间"));
        assertTrue(detailCsv.contains(clearingNo));
    }
}
