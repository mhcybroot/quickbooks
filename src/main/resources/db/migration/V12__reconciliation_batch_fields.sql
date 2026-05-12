alter table reconciliation_session_row add column candidate_txn_ids text;
alter table reconciliation_session_row add column candidate_count integer;
alter table reconciliation_session_row add column group_key varchar(255);
alter table reconciliation_session_row add column group_window_start date;
alter table reconciliation_session_row add column group_window_end date;
alter table reconciliation_session_row add column allocation_mode varchar(64);
alter table reconciliation_session_row add column batch_match boolean not null default false;
alter table reconciliation_session_row add column apply_success_count integer not null default 0;
alter table reconciliation_session_row add column apply_fail_count integer not null default 0;
