package com.abc123.hsp.repository;

import com.abc123.hsp.dto.OrderListItemDTO;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OrderListItemDTO> findAll() {
        return jdbcTemplate.query(
                "SELECT order_no, customer_name, service_type, worker_name, "
                        + "CONCAT('¥', FORMAT(order_amount, 2)) AS order_amount_view, "
                        + "CONCAT('¥', FORMAT(paid_amount, 2)) AS paid_amount_view, "
                        + "order_status, order_status_type, fulfillment_status, fulfillment_status_type, "
                        + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at_view "
                        + "FROM t_order ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    OrderListItemDTO dto = new OrderListItemDTO();
                    dto.setOrderNo(rs.getString("order_no"));
                    dto.setCustomerName(rs.getString("customer_name"));
                    dto.setServiceType(rs.getString("service_type"));
                    dto.setWorkerName(rs.getString("worker_name"));
                    dto.setOrderAmount(rs.getString("order_amount_view"));
                    dto.setPaidAmount(rs.getString("paid_amount_view"));
                    dto.setOrderStatus(rs.getString("order_status"));
                    dto.setOrderStatusType(rs.getString("order_status_type"));
                    dto.setFulfillmentStatus(rs.getString("fulfillment_status"));
                    dto.setFulfillmentStatusType(rs.getString("fulfillment_status_type"));
                    dto.setCreatedAt(rs.getString("created_at_view"));
                    return dto;
                }
        );
    }
}
