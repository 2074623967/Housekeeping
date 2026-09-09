package com.abc123.hsp.dto;

/**
 * 运营分页查询的统一边界。
 */
public final class PageQueryBounds {

    public static final int MAX_PAGE_NO = 1000;
    public static final int MAX_PAGE_SIZE = 100;

    private PageQueryBounds() {
    }

    public static int normalizePageNo(int pageNo) {
        return Math.min(Math.max(pageNo, 1), MAX_PAGE_NO);
    }

    public static int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    public static int offset(int pageNo, int pageSize) {
        return (normalizePageNo(pageNo) - 1) * normalizePageSize(pageSize);
    }
}
