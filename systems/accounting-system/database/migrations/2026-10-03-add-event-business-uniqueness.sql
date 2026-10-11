ALTER TABLE t_account_event
  ADD UNIQUE KEY uk_event_type_biz_no (event_type, biz_no);
