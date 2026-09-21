package com.abc123.settlement.dto;

import java.util.List;
import java.util.Collections;
import lombok.Data;

/**
 * 分页返回结构。
 */
@Data
public class PageResultDTO<T> {

    private List<T> items;
    private int total;
    private int pageNo;
    private int pageSize;

    public PageResultDTO(List<T> items, int total, int pageNo, int pageSize) {
        this.items = items == null ? Collections.emptyList() : items;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }
}
