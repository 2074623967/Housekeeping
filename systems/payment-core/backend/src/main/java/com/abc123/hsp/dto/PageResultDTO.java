package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 后台列表统一分页结果。
 *
 * @param <T> 列表行类型
 */
@Data
public class PageResultDTO<T> {

    /** 当前页数据。 */
    private List<T> items;
    /** 符合条件的总条数。 */
    private long total;
    /** 当前页码，从 1 开始。 */
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
