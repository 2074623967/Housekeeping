package com.abc123.walletaccount.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletIdempotentRecordEntity {

    /** 数据库主键。 */
    private Long id;
    /** 请求号。 */
    private String requestNo;
    /** 业务类型。 */
    private String bizType;
    /** 幂等键。 */
    private String idempotentKey;
    /** 结果引用号。 */
    private String resultRefNo;
    /** 处理状态。 */
    private String status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
