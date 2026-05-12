alter table reconciliation_session_row add column pattern_type varchar(64);
alter table reconciliation_session_row add column pattern_key text;
alter table reconciliation_session_row add column wo_key varchar(255);
alter table reconciliation_session_row add column wo_matched boolean not null default false;
alter table reconciliation_session_row add column wo_source varchar(64);
