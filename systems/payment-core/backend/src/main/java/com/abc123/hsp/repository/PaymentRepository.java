package com.abc123.hsp.repository;

import com.abc123.hsp.dto.PaymentListItemDTO;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PaymentListItemDTO> findAll() {
        return jdbcTemplate.query(
                "SELECT payment_order_id, order_no, customer_name, "
                        + "CONCAT('¥', FORMAT(amount, 2)) AS amount_view, payment_method, channel_code, "
                        + "channel_transaction_no, status, status_type, "
                        + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at_view "
                        + "FROM t_payment_order ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    PaymentListItemDTO dto = new PaymentListItemDTO();
                    dto.setPaymentOrderId(rs.getString("payment_order_id"));
                    dto.setOrderNo(rs.getString("order_no"));
                    dto.setCustomerName(rs.getString("customer_name"));
                    dto.setAmount(rs.getString("amount_view"));
                    dto.setPaymentMethod(rs.getString("payment_method"));
                    dto.setChannel(rs.getString("channel_code"));
                    dto.setChannelTransactionNo(rs.getString("channel_transaction_no"));
                    dto.setStatus(rs.getString("status"));
                    dto.setStatusType(rs.getString("status_type"));
                    dto.setCreatedAt(rs.getString("created_at_view"));
                    return dto;
                }
        );
    }
}
