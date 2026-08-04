package com.abc123.riskcontrol.dto;

import java.util.List;
import lombok.Data;

/**
 * 分页结果。
 */
@Data
public class PageResultDTO<T> {

    /** 记录列表。 */
    private List<T> records;
    /** 总条数。 */
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

