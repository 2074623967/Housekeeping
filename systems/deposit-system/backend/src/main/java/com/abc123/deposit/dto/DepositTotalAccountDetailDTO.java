package com.abc123.deposit.dto;

import java.util.List;
import lombok.Data;

/**
 * 总保证金账户详情预览。
 */
@Data
public class DepositTotalAccountDetailDTO {

    /** 总账户摘要。 */
    private DepositTotalAccountDTO summary;
    /** 规则绑定预览。 */
    private List<DepositRuleBindingPreviewDTO> bindings;
    /** 结转预览。 */
    private List<DepositMigrationPreviewDTO> migrations;
}
