package com.abc123.deposit.dto;

import java.util.Collections;
import java.util.List;
import lombok.Data;

/** 分页查询统一响应。 */
@Data
public class PageResultDTO<T> {

    private List<T> items;
    private long total;
    private int pageNo;
    private int pageSize;

    public PageResultDTO(List<T> items, long total, int pageNo, int pageSize) {
        this.items = items == null ? Collections.<T>emptyList() : items;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }
}
