package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.DashboardCardDTO;
import java.util.List;

public interface DashboardMapper {

    List<DashboardCardDTO> findCards();
}
