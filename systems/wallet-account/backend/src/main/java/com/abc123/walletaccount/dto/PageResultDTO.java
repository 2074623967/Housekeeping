package com.abc123.walletaccount.dto;

import java.util.List;
import lombok.Data;

@Data
public class PageResultDTO<T> {

    private long total;
    private List<T> records;
}
