package com.abc123.settlement.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 分页返回结构。
 */
@Data
@AllArgsConstructor
public class PageResultDTO<T> {

    private List<T> items;
    private int total;
    private int pageNo;
    private int pageSize;
}
