package com.abc123.walletaccount.dto;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class WalletAccountQueryDTO {

    /** 账户编号或主体名称关键字。 */
    private String keyword;
    /** 主体类型。 */
    private String ownerType;
    /** 账户类型。 */
    private String accountType;
    /** 账户状态。 */
    private String accountStatus;
    /** 外部主体编号。 */
    private String extRefNo;
    /** 业务线编码。 */
    private String bizLineCode;
    /** 租户编码。 */
    private String tenantCode;
    /** 开户时间起点，含边界。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime openedStartTime;
    /** 开户时间终点，含边界。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime openedEndTime;
    /** 页码，从 1 开始。 */
    private Integer pageNo = 1;
    /** 每页条数。 */
    private Integer pageSize = 20;
}
