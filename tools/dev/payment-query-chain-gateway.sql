UPDATE t_gateway_certificate
SET expire_at = DATEADD('DAY', 30, CURRENT_DATE)
WHERE certificate_code = 'CERT_WX_2026';
