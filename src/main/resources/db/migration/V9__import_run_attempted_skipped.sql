alter table import_run add column attempted_rows int not null default 0;
alter table import_run add column skipped_rows int not null default 0;
