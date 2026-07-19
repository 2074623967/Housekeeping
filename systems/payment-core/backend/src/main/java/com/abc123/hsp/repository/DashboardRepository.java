package com.abc123.hsp.repository;

import com.abc123.hsp.dto.DashboardCardDTO;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DashboardCardDTO> findCards() {
        return jdbcTemplate.query(
                "SELECT card_key, title, value, badge_type, badge_text FROM t_dashboard_card ORDER BY sort_no ASC",
                (rs, rowNum) -> {
                    DashboardCardDTO dto = new DashboardCardDTO();
                    dto.setKey(rs.getString("card_key"));
                    dto.setTitle(rs.getString("title"));
                    dto.setValue(rs.getString("value"));
                    dto.setBadgeType(rs.getString("badge_type"));
                    dto.setBadgeText(rs.getString("badge_text"));
                    return dto;
                }
        );
    }
}
