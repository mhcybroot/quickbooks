create table if not exists company (
    id bigserial primary key,
    name varchar(255) not null,
    code varchar(64) not null unique,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists app_user (
    id bigserial primary key,
    username varchar(255) not null unique,
    password_hash varchar(255) not null,
    platform_role varchar(32) not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists company_membership (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    company_id bigint not null references company(id) on delete cascade,
    role varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique(user_id, company_id)
);

create table if not exists audit_log (
    id bigserial primary key,
    actor_user_id bigint references app_user(id) on delete set null,
    company_id bigint references company(id) on delete set null,
    action_type varchar(128) not null,
    summary text,
    created_at timestamp with time zone not null
);

insert into company(name, code, status, created_at, updated_at)
select 'Default Company', 'DEFAULT', 'ACTIVE', now(), now()
where not exists (select 1 from company where code = 'DEFAULT');

alter table qbo_connection add column if not exists company_id bigint;
alter table qbo_connection add column if not exists connected boolean not null default true;
update qbo_connection set company_id = (select id from company where code = 'DEFAULT') where company_id is null;
alter table qbo_connection alter column company_id set not null;
alter table qbo_connection add constraint fk_qbo_connection_company foreign key (company_id) references company(id);

drop index if exists uq_qbo_connection_realm_id;
create unique index if not exists uq_qbo_connection_realm_active on qbo_connection(realm_id) where connected = true;
create index if not exists ix_qbo_connection_company_connected on qbo_connection(company_id, connected, updated_at desc);

alter table import_batch add column if not exists company_id bigint;
update import_batch set company_id = (select id from company where code = 'DEFAULT') where company_id is null;
alter table import_batch alter column company_id set not null;
alter table import_batch add constraint fk_import_batch_company foreign key (company_id) references company(id);
create index if not exists ix_import_batch_company_created_at on import_batch(company_id, created_at desc);

alter table import_run add column if not exists company_id bigint;
update import_run set company_id = (select id from company where code = 'DEFAULT') where company_id is null;
alter table import_run alter column company_id set not null;
alter table import_run add constraint fk_import_run_company foreign key (company_id) references company(id);
create index if not exists ix_import_run_company_created_at on import_run(company_id, created_at desc);

alter table reconciliation_session add column if not exists company_id bigint;
update reconciliation_session set company_id = (select id from company where code = 'DEFAULT') where company_id is null;
alter table reconciliation_session alter column company_id set not null;
alter table reconciliation_session add constraint fk_reconciliation_session_company foreign key (company_id) references company(id);
create index if not exists ix_reconciliation_session_company_created_at on reconciliation_session(company_id, created_at desc);
