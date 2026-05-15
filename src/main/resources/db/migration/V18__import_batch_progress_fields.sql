alter table import_batch add column if not exists started_at timestamp with time zone;
alter table import_batch add column if not exists completed_at timestamp with time zone;
alter table import_batch add column if not exists planned_total_rows integer not null default 0;
alter table import_batch add column if not exists planned_runnable_rows integer not null default 0;
