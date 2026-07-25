package com.abc123.gatewayaccess.dto;

import java.util.List;
import lombok.Data;

/**
 * 简单分页结果。
 */
@Data
public class PageResultDTO<T> {

    /** 分页记录。 */
    private List<T> records;
    /** 总记录数。 */
    private long total;
    /** 页码。 */
    private int pageNo;
    /** 每页条数。 */
    private int pageSize;

    public PageResultDTO(List<T> records, long total, int pageNo, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }
}
