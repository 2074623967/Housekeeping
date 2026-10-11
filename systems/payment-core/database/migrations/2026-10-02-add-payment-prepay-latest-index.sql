-- 支付单详情与列表按支付单号查找最新预付事实；旧库缺少支付单号前导索引。
-- 索引构建可能影响大表写入；仅在结构核对、容量评估和受控变更窗口后执行一次。
ALTER TABLE t_prepay_order
    ADD INDEX idx_prepay_payment_created (payment_order_id, created_at, id);
