package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 渠道返回码批量导入预检或应用结果。
 */
@Data
public class PaymentReturnCodeImportResultDTO {

    /** 应用时用于防止覆盖并发配置变化的基线指纹。 */
    private String baselineFingerprint;
    /** 应用后的配置指纹，预检阶段为空。 */
    private String resultFingerprint;
    /** 应用成功时生成的审计批次号。 */
    private String batchNo;
    /** 整批是否通过校验。 */
    private Boolean valid;
    /** 是否已经执行数据库写入。 */
    private Boolean applied;
    private Integer totalCount;
    private Integer createCount;
    private Integer updateCount;
    private Integer unchangedCount;
    private Integer invalidCount;
    private Integer appliedCount;
    private List<PaymentReturnCodeImportItemDTO> items;
}
