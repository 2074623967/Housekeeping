package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 渠道返回码导入单行差异。
 */
@Data
public class PaymentReturnCodeImportItemDTO {

    /** CSV 数据行号，从表头下一行开始计数。 */
    private Long rowNumber;
    /** CREATE、UPDATE、UNCHANGED 或 INVALID。 */
    private String action;
    /** 当前行是否可应用。 */
    private Boolean valid;
    /** 校验或差异摘要。 */
    private String message;
    /** UPDATE 时发生变化的字段。 */
    private List<String> changedFields;
    /** 规范化后的候选映射。 */
    private PaymentChannelReturnCodeConfigDTO candidate;
}
