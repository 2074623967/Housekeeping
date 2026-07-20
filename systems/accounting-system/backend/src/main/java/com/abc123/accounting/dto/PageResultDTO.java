package com.abc123.accounting.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 通用分页结果。
 *
 * @param <T> 列表项类型
 */
@Data
@AllArgsConstructor
public class PageResultDTO<T> {

    /** 当前页数据。 */
    private List<T> items;
    /** 总记录数。 */
    private long total;
    /** 页码。 */
    private int pageNo;
    /** 每页条数。 */
    private int pageSize;
}
