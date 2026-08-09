package com.abc123.refund.dto;

import java.util.List;
import lombok.Data;

/**
 * 分页结果。
 *
 * @param <T> 列表项类型
 */
@Data
public class PageResultDTO<T> {

    /** 当前页数据。 */
    private List<T> items;
    /** 总记录数。 */
    private long total;
    /** 当前页码。 */
    private int pageNo;
    /** 每页条数。 */
    private int pageSize;

    public PageResultDTO(List<T> items, long total, int pageNo, int pageSize) {
        this.items = items;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }
}

