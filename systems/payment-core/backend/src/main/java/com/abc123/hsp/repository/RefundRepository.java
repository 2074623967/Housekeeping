package com.abc123.hsp.repository;

import com.abc123.hsp.dto.RefundListItemDTO;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefundRepository {

    private final JdbcTemplate jdbcTemplate;

    public RefundRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RefundListItemDTO> findAll() {
        return jdbcTemplate.query(
                "SELECT refund_order_id, payment_order_id, order_no, customer_name, "
                        + "CONCAT('¥', FORMAT(refund_amount, 2)) AS refund_amount_view, refund_method, "
                        + "status, status_type, "
                        + "DATE_FORMAT(applied_at, '%Y-%m-%d %H:%i:%s') AS applied_at_view, "
                        + "CASE WHEN success_at IS NULL THEN '-' ELSE DATE_FORMAT(success_at, '%Y-%m-%d %H:%i:%s') END AS success_at_view "
                        + "FROM t_refund_order ORDER BY applied_at DESC",
                (rs, rowNum) -> {
                    RefundListItemDTO dto = new RefundListItemDTO();
                    dto.setRefundOrderId(rs.getString("refund_order_id"));
                    dto.setPaymentOrderId(rs.getString("payment_order_id"));
                    dto.setOrderNo(rs.getString("order_no"));
                    dto.setCustomerName(rs.getString("customer_name"));
                    dto.setRefundAmount(rs.getString("refund_amount_view"));
                    dto.setRefundMethod(rs.getString("refund_method"));
                    dto.setStatus(rs.getString("status"));
                    dto.setStatusType(rs.getString("status_type"));
                    dto.setAppliedAt(rs.getString("applied_at_view"));
                    dto.setSuccessAt(rs.getString("success_at_view"));
                    return dto;
                }
        );
    }
}
