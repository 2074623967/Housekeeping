package com.abc123.gatewayaccess.dto;

import java.util.List;
import lombok.Data;

/**
 * 简单分页结果。
 */
@Data
public class PageResultDTO<T> {

    private List<T> records;
    private long total;
    private int pageNo;
    private int pageSize;

    public PageResultDTO(List<T> records, long total, int pageNo, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }
}
