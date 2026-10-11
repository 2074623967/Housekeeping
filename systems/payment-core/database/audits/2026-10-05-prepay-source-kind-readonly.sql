-- 仅在获准的只读会话中运行；当前订单行存在与否不是历史创建来源证据。
-- 查询不修改 source_kind，也不生成自动补标候选。
USE housekeeping_payment_core;

SELECT COALESCE(payment_order.status, 'MISSING_PAYMENT') AS payment_status,
       CASE WHEN EXISTS (
           SELECT 1 FROM t_order current_order
           WHERE current_order.order_no = prepay_order.order_no
       ) THEN 'PRESENT_NOW' ELSE 'ABSENT_NOW' END AS current_order_presence,
       COUNT(*) AS unknown_prepay_count
FROM t_prepay_order prepay_order
LEFT JOIN t_payment_order payment_order
       ON payment_order.payment_order_id = prepay_order.payment_order_id
WHERE CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) = CAST('UNKNOWN#' AS BINARY(17))
GROUP BY payment_status, current_order_presence
ORDER BY payment_status, current_order_presence;

SELECT prepay_order.prepay_order_no,
       prepay_order.payment_order_id,
       prepay_order.order_no,
       prepay_order.created_at AS prepay_created_at,
       COALESCE(payment_order.status, 'MISSING_PAYMENT') AS payment_status,
       payment_order.success_at AS payment_success_at,
       prepay_order.amount AS prepay_amount,
       payment_order.amount AS payment_amount,
       CASE WHEN EXISTS (
           SELECT 1 FROM t_order current_order
           WHERE current_order.order_no = prepay_order.order_no
       ) THEN 'PRESENT_NOW' ELSE 'ABSENT_NOW' END AS current_order_presence,
       (SELECT COUNT(*) FROM t_prepay_order bound_prepay
        WHERE bound_prepay.payment_order_id = prepay_order.payment_order_id)
           AS payment_prepay_count
FROM t_prepay_order prepay_order
LEFT JOIN t_payment_order payment_order
       ON payment_order.payment_order_id = prepay_order.payment_order_id
WHERE CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) = CAST('UNKNOWN#' AS BINARY(17))
ORDER BY CASE
             WHEN payment_order.status IN ('PREPAY_CREATED', 'PAYING', 'WAIT_CALLBACK', 'RISK_REVIEW') THEN 0
             WHEN payment_order.status = 'SUCCESS' THEN 1
             ELSE 2
         END,
         prepay_order.created_at DESC,
         prepay_order.id DESC
LIMIT 500;

SELECT COALESCE(payment_order.status, 'MISSING_PAYMENT') AS payment_status,
       CASE
           WHEN CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) NOT IN (
               CAST('UNKNOWN#' AS BINARY(17)), CAST('STANDARD_ORDER#' AS BINARY(17)),
               CAST('VIRTUAL#' AS BINARY(17)))
               THEN 'INVALID_SOURCE_KIND'
           WHEN CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) = CAST('STANDARD_ORDER#' AS BINARY(17)) THEN 'STANDARD_MISSING_NOW'
           ELSE 'VIRTUAL_COLLISION_NOW'
       END AS conflict_type,
       COUNT(*) AS conflicted_prepay_count
FROM t_prepay_order prepay_order
LEFT JOIN t_payment_order payment_order
       ON payment_order.payment_order_id = prepay_order.payment_order_id
LEFT JOIN t_order current_order
       ON current_order.order_no = prepay_order.order_no
WHERE (CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) = CAST('STANDARD_ORDER#' AS BINARY(17))
       AND current_order.order_no IS NULL)
   OR (CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) = CAST('VIRTUAL#' AS BINARY(17))
       AND current_order.order_no IS NOT NULL)
   OR CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) NOT IN (
       CAST('UNKNOWN#' AS BINARY(17)), CAST('STANDARD_ORDER#' AS BINARY(17)),
       CAST('VIRTUAL#' AS BINARY(17)))
GROUP BY payment_status, conflict_type
ORDER BY payment_status, conflict_type;

SELECT prepay_order.prepay_order_no,
       prepay_order.payment_order_id,
       prepay_order.order_no,
       prepay_order.source_kind,
       prepay_order.created_at AS prepay_created_at,
       COALESCE(payment_order.status, 'MISSING_PAYMENT') AS payment_status,
       prepay_order.amount AS prepay_amount,
       payment_order.amount AS payment_amount,
       CASE
           WHEN CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) NOT IN (
               CAST('UNKNOWN#' AS BINARY(17)), CAST('STANDARD_ORDER#' AS BINARY(17)),
               CAST('VIRTUAL#' AS BINARY(17)))
               THEN 'INVALID_SOURCE_KIND'
           WHEN CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) = CAST('STANDARD_ORDER#' AS BINARY(17)) THEN 'STANDARD_MISSING_NOW'
           ELSE 'VIRTUAL_COLLISION_NOW'
       END AS conflict_type,
       (SELECT COUNT(*) FROM t_prepay_order bound_prepay
        WHERE bound_prepay.payment_order_id = prepay_order.payment_order_id)
           AS payment_prepay_count
FROM t_prepay_order prepay_order
LEFT JOIN t_payment_order payment_order
       ON payment_order.payment_order_id = prepay_order.payment_order_id
LEFT JOIN t_order current_order
       ON current_order.order_no = prepay_order.order_no
WHERE (CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) = CAST('STANDARD_ORDER#' AS BINARY(17))
       AND current_order.order_no IS NULL)
   OR (CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) = CAST('VIRTUAL#' AS BINARY(17))
       AND current_order.order_no IS NOT NULL)
   OR CAST(CONCAT(prepay_order.source_kind, '#') AS BINARY(17)) NOT IN (
       CAST('UNKNOWN#' AS BINARY(17)), CAST('STANDARD_ORDER#' AS BINARY(17)),
       CAST('VIRTUAL#' AS BINARY(17)))
ORDER BY CASE
             WHEN payment_order.status IN ('PREPAY_CREATED', 'PAYING', 'WAIT_CALLBACK', 'RISK_REVIEW') THEN 0
             WHEN payment_order.status = 'SUCCESS' THEN 1
             ELSE 2
         END,
         prepay_order.created_at DESC,
         prepay_order.id DESC
LIMIT 500;
