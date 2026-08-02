package com.abc123.walletaccount.dto;

import java.util.List;
import lombok.Data;

@Data
public class PageResultDTO<T> {

    /** 总记录数。 */
    private long total;
    /** 当前页数据。 */
    private List<T> records;
}
