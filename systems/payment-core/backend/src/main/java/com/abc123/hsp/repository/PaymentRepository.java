package com.abc123.hsp.repository;

import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentNotifyItemDTO;
import com.abc123.hsp.dto.PaymentRouteItemDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
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
                "SELECT p.payment_order_id, p.order_no, p.customer_name, "
                        + "CONCAT('¥', FORMAT(p.amount, 2)) AS amount_view, p.payment_method, p.channel_code, "
                        + "p.channel_transaction_no, p.status, p.status_type, "
                        + "DATE_FORMAT(p.created_at, '%Y-%m-%d %H:%i:%s') AS created_at_view, "
                        + "pr.prepay_order_no, b.bill_no "
                        + "FROM t_payment_order p "
                        + "LEFT JOIN t_prepay_order pr ON pr.payment_order_id = p.payment_order_id "
                        + "LEFT JOIN t_bill b ON b.order_no = p.order_no "
                        + "ORDER BY p.created_at DESC",
                (rs, rowNum) -> mapListItem(rs)
        );
    }

    public PaymentDetailDTO findDetail(String paymentOrderId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT p.payment_order_id, p.order_no, p.customer_name, "
                            + "CONCAT('¥', FORMAT(p.amount, 2)) AS amount_view, p.payment_method, p.channel_code, "
                            + "p.channel_transaction_no, p.status, p.status_type, "
                            + "DATE_FORMAT(p.created_at, '%Y-%m-%d %H:%i:%s') AS created_at_view, "
                            + "pr.prepay_order_no, b.bill_no "
                            + "FROM t_payment_order p "
                            + "LEFT JOIN t_prepay_order pr ON pr.payment_order_id = p.payment_order_id "
                            + "LEFT JOIN t_bill b ON b.order_no = p.order_no "
                            + "WHERE p.payment_order_id = ?",
                    (rs, rowNum) -> {
                        PaymentDetailDTO dto = new PaymentDetailDTO();
                        dto.setPaymentOrderId(rs.getString("payment_order_id"));
                        dto.setPrepayOrderNo(rs.getString("prepay_order_no"));
                        dto.setBillNo(rs.getString("bill_no"));
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
                    },
                    paymentOrderId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public PrepayOrderDTO createPrepay(String orderNo, String payScene) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT order_no, customer_name, order_amount, paid_amount FROM t_order WHERE order_no = ?",
                    (rs, rowNum) -> {
                        BigDecimal orderAmount = rs.getBigDecimal("order_amount");
                        BigDecimal paidAmount = rs.getBigDecimal("paid_amount");
                        BigDecimal payAmount = orderAmount.subtract(paidAmount);
                        String billNo = ensureBill(orderNo, rs.getString("customer_name"), orderAmount, paidAmount);
                        String prepayOrderNo = "PRE" + System.currentTimeMillis();
                        String cashierTitle = "家政服务收银台";
                        String paymentOrderId = ensurePaymentOrder(orderNo, rs.getString("customer_name"), payAmount, payScene);
                        jdbcTemplate.update(
                                "INSERT INTO t_prepay_order(prepay_order_no,bill_no,order_no,customer_name,amount,pay_scene,cashier_title,cashier_status,cashier_status_type,payment_order_id,created_at,expires_at) "
                                        + "VALUES(?,?,?,?,?,?,?,?,?,?,NOW(),DATE_ADD(NOW(), INTERVAL 1 HOUR))",
                                prepayOrderNo, billNo, orderNo, rs.getString("customer_name"), payAmount, payScene, cashierTitle, "待支付", "warn", paymentOrderId
                        );
                        return findPrepay(prepayOrderNo);
                    },
                    orderNo
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public PrepayOrderDTO findPrepay(String prepayOrderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT prepay_order_no, bill_no, order_no, customer_name, CONCAT('¥', FORMAT(amount, 2)) AS amount_view, "
                            + "pay_scene, cashier_title, cashier_status, cashier_status_type, payment_order_id, "
                            + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at_view, "
                            + "DATE_FORMAT(expires_at, '%Y-%m-%d %H:%i:%s') AS expires_at_view "
                            + "FROM t_prepay_order WHERE prepay_order_no = ?",
                    (rs, rowNum) -> {
                        PrepayOrderDTO dto = new PrepayOrderDTO();
                        dto.setPrepayOrderNo(rs.getString("prepay_order_no"));
                        dto.setBillNo(rs.getString("bill_no"));
                        dto.setOrderNo(rs.getString("order_no"));
                        dto.setCustomerName(rs.getString("customer_name"));
                        dto.setAmount(rs.getString("amount_view"));
                        dto.setPayScene(rs.getString("pay_scene"));
                        dto.setCashierTitle(rs.getString("cashier_title"));
                        dto.setCashierStatus(rs.getString("cashier_status"));
                        dto.setCashierStatusType(rs.getString("cashier_status_type"));
                        dto.setPaymentOrderId(rs.getString("payment_order_id"));
                        dto.setCreatedAt(rs.getString("created_at_view"));
                        dto.setExpiresAt(rs.getString("expires_at_view"));
                        return dto;
                    },
                    prepayOrderNo
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public CashierPageDTO findCashierPage(String prepayOrderNo) {
        PrepayOrderDTO prepay = findPrepay(prepayOrderNo);
        if (prepay == null) {
            return null;
        }
        CashierPageDTO dto = new CashierPageDTO();
        dto.setPrepayOrderNo(prepay.getPrepayOrderNo());
        dto.setOrderNo(prepay.getOrderNo());
        dto.setBillNo(prepay.getBillNo());
        dto.setCustomerName(prepay.getCustomerName());
        dto.setAmount(prepay.getAmount());
        dto.setPayScene(prepay.getPayScene());
        dto.setTitle(prepay.getCashierTitle());
        dto.setStatus(prepay.getCashierStatus());
        dto.setStatusType(prepay.getCashierStatusType());
        dto.setExpiresAt(prepay.getExpiresAt());
        dto.setChannels(Arrays.asList("微信支付", "支付宝", "银行卡"));
        return dto;
    }

    public void updatePaymentStatus(String paymentOrderId, String status, String statusType, String channelTransactionNo) {
        jdbcTemplate.update(
                "UPDATE t_payment_order SET status = ?, status_type = ?, channel_transaction_no = ? WHERE payment_order_id = ?",
                status, statusType, channelTransactionNo, paymentOrderId
        );
    }

    public void updatePrepayStatus(String prepayOrderNo, String status, String statusType) {
        jdbcTemplate.update(
                "UPDATE t_prepay_order SET cashier_status = ?, cashier_status_type = ? WHERE prepay_order_no = ?",
                status, statusType, prepayOrderNo
        );
    }

    public void insertNotifyLog(String notifyNo, String paymentOrderId, String channelCode, String notifyType, String notifyPayload, String notifyResult, String notifyStatus, String notifyStatusType) {
        jdbcTemplate.update(
                "INSERT INTO t_payment_notify_log(notify_no,payment_order_id,channel_code,notify_type,notify_payload,notify_result,notify_status,notify_status_type,created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,NOW())",
                notifyNo, paymentOrderId, channelCode, notifyType, notifyPayload, notifyResult, notifyStatus, notifyStatusType
        );
    }

    public void insertRouteRecord(String routeNo, String paymentOrderId, String channelCode, String routeRule, String routeResult) {
        jdbcTemplate.update(
                "INSERT INTO t_payment_route_record(route_no,payment_order_id,channel_code,route_rule,route_result,created_at) VALUES(?,?,?,?,?,NOW())",
                routeNo, paymentOrderId, channelCode, routeRule, routeResult
        );
    }

    public void insertEvent(String eventNo, String eventType, String paymentOrderId, String bizNo, String eventPayload) {
        jdbcTemplate.update(
                "INSERT INTO t_payment_event(event_no,event_type,payment_order_id,biz_no,event_payload,created_at) VALUES(?,?,?,?,?,NOW())",
                eventNo, eventType, paymentOrderId, bizNo, eventPayload
        );
    }

    public List<String> findNotifyLogs(String paymentOrderId) {
        return jdbcTemplate.query(
                "SELECT CONCAT(DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s'), ' | ', notify_type, ' | ', notify_status) AS item "
                        + "FROM t_payment_notify_log WHERE payment_order_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> rs.getString("item"),
                paymentOrderId
        );
    }

    public List<PaymentNotifyItemDTO> findNotifyItems(String paymentOrderId) {
        return jdbcTemplate.query(
                "SELECT notify_no, channel_code, notify_type, notify_status, notify_status_type, "
                        + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at_view "
                        + "FROM t_payment_notify_log WHERE payment_order_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    PaymentNotifyItemDTO dto = new PaymentNotifyItemDTO();
                    dto.setNotifyNo(rs.getString("notify_no"));
                    dto.setChannelCode(rs.getString("channel_code"));
                    dto.setNotifyType(rs.getString("notify_type"));
                    dto.setNotifyStatus(rs.getString("notify_status"));
                    dto.setNotifyStatusType(rs.getString("notify_status_type"));
                    dto.setCreatedAt(rs.getString("created_at_view"));
                    return dto;
                },
                paymentOrderId
        );
    }

    public List<PaymentRouteItemDTO> findRouteItems(String paymentOrderId) {
        return jdbcTemplate.query(
                "SELECT route_no, channel_code, route_rule, route_result, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at_view "
                        + "FROM t_payment_route_record WHERE payment_order_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    PaymentRouteItemDTO dto = new PaymentRouteItemDTO();
                    dto.setRouteNo(rs.getString("route_no"));
                    dto.setChannelCode(rs.getString("channel_code"));
                    dto.setRouteRule(rs.getString("route_rule"));
                    dto.setRouteResult(rs.getString("route_result"));
                    dto.setCreatedAt(rs.getString("created_at_view"));
                    return dto;
                },
                paymentOrderId
        );
    }

    public PaymentDetailDTO enrichDetail(PaymentDetailDTO detail) {
        if (detail != null) {
            detail.setRouteLogs(findRouteLogs(detail.getPaymentOrderId()));
            detail.setNotifyLogs(findNotifyLogs(detail.getPaymentOrderId()));
            detail.setEventLogs(findEventItems(detail.getPaymentOrderId()));
        }
        return detail;
    }

    public List<String> findEventItems(String paymentOrderId) {
        return jdbcTemplate.query(
                "SELECT CONCAT(event_type, ' | ', biz_no, ' | ', DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')) AS item "
                        + "FROM t_payment_event WHERE payment_order_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> rs.getString("item"),
                paymentOrderId
        );
    }

    public List<String> findRouteLogs(String paymentOrderId) {
        return jdbcTemplate.query(
                "SELECT CONCAT(route_rule, ' | ', route_result, ' | ', DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')) AS item "
                        + "FROM t_payment_route_record WHERE payment_order_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> rs.getString("item"),
                paymentOrderId
        );
    }

    public int countByPrepayOrderNo(String prepayOrderNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_prepay_order WHERE prepay_order_no = ?",
                Integer.class,
                prepayOrderNo
        );
        return count == null ? 0 : count;
    }

    public String findPaymentOrderIdByPrepayOrderNo(String prepayOrderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT payment_order_id FROM t_prepay_order WHERE prepay_order_no = ?",
                    String.class,
                    prepayOrderNo
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public void ensurePaymentOrderByPrepay(String prepayOrderNo, String paymentMethod, String channelCode) {
        jdbcTemplate.update(
                "UPDATE t_prepay_order SET cashier_status = '支付中', cashier_status_type = 'info' WHERE prepay_order_no = ?",
                prepayOrderNo
        );
        jdbcTemplate.update(
                "UPDATE t_payment_order SET payment_method = ?, channel_code = ? WHERE payment_order_id = ?",
                paymentMethod, channelCode, findPaymentOrderIdByPrepayOrderNo(prepayOrderNo)
        );
    }

    public String findBillNoByPrepayOrderNo(String prepayOrderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT bill_no FROM t_prepay_order WHERE prepay_order_no = ?",
                    String.class,
                    prepayOrderNo
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public String findOrderNoByPrepayOrderNo(String prepayOrderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT order_no FROM t_prepay_order WHERE prepay_order_no = ?",
                    String.class,
                    prepayOrderNo
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public String ensureBill(String orderNo, String customerName, BigDecimal orderAmount, BigDecimal paidAmount) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT bill_no FROM t_bill WHERE order_no = ?",
                    String.class,
                    orderNo
            );
        } catch (EmptyResultDataAccessException exception) {
            String billNo = "BILL" + System.currentTimeMillis();
            BigDecimal remain = orderAmount.subtract(paidAmount);
            String status = remain.compareTo(BigDecimal.ZERO) <= 0 ? "已结清" : "待支付";
            String type = remain.compareTo(BigDecimal.ZERO) <= 0 ? "success" : "warn";
            jdbcTemplate.update(
                    "INSERT INTO t_bill(bill_no,order_no,customer_name,bill_amount,paid_amount,bill_status,bill_status_type,due_at,created_at) VALUES(?,?,?,?,?,?,?,DATE_ADD(NOW(), INTERVAL 1 DAY),NOW())",
                    billNo, orderNo, customerName, orderAmount, paidAmount, status, type
            );
            return billNo;
        }
    }

    public String ensurePaymentOrder(String orderNo, String customerName, BigDecimal amount, String payScene) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT payment_order_id FROM t_payment_order WHERE order_no = ? ORDER BY created_at DESC LIMIT 1",
                    String.class,
                    orderNo
            );
        } catch (EmptyResultDataAccessException exception) {
            String paymentOrderId = "PAY" + System.currentTimeMillis();
            jdbcTemplate.update(
                    "INSERT INTO t_payment_order(payment_order_id,order_no,customer_name,amount,payment_method,channel_code,channel_transaction_no,status,status_type,created_at) VALUES(?,?,?,?,?,?,?,?,?,NOW())",
                    paymentOrderId, orderNo, customerName, amount, "待选渠道", payScene, "", "PREPAY_CREATED", "info"
            );
            return paymentOrderId;
        }
    }

    private PaymentListItemDTO mapListItem(ResultSet rs) throws SQLException {
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
}
