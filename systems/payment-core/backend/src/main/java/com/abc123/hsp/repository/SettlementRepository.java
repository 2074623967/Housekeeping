package com.abc123.hsp.repository;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementRepository {

    private final JdbcTemplate jdbcTemplate;

    public SettlementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WorkerSettlementListItemDTO> findWorkerSettlements() {
        return jdbcTemplate.query(
                "SELECT settlement_order_id, worker_name, "
                        + "CONCAT(DATE_FORMAT(period_start, '%Y-%m-%d'), ' ~ ', DATE_FORMAT(period_end, '%Y-%m-%d')) AS period_view, "
                        + "CONCAT('¥', FORMAT(amount_should_settle, 2)) AS amount_should_settle_view, "
                        + "CONCAT('¥', FORMAT(deduct_amount, 2)) AS deduct_amount_view, "
                        + "CONCAT('¥', FORMAT(amount_net_settle, 2)) AS amount_net_settle_view, "
                        + "CONCAT('¥', FORMAT(deposit_impact_amount, 2)) AS deposit_impact_amount_view, "
                        + "status, status_type, payout_status, payout_status_type "
                        + "FROM t_worker_settlement_order ORDER BY period_end DESC, created_at DESC",
                (rs, rowNum) -> {
                    WorkerSettlementListItemDTO dto = new WorkerSettlementListItemDTO();
                    dto.setSettlementOrderId(rs.getString("settlement_order_id"));
                    dto.setWorkerName(rs.getString("worker_name"));
                    dto.setPeriod(rs.getString("period_view"));
                    dto.setAmountShouldSettle(rs.getString("amount_should_settle_view"));
                    dto.setDeductAmount(rs.getString("deduct_amount_view"));
                    dto.setAmountNetSettle(rs.getString("amount_net_settle_view"));
                    dto.setDepositImpactAmount(rs.getString("deposit_impact_amount_view"));
                    dto.setStatus(rs.getString("status"));
                    dto.setStatusType(rs.getString("status_type"));
                    dto.setPayoutStatus(rs.getString("payout_status"));
                    dto.setPayoutStatusType(rs.getString("payout_status_type"));
                    return dto;
                }
        );
    }
}
