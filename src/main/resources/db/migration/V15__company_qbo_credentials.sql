create table if not exists company_qbo_app_credentials (
    id bigserial primary key,
    company_id bigint not null references company(id) on delete cascade,
    client_id varchar(255) not null,
    client_secret_encrypted text not null,
    redirect_uri_override varchar(1024),
    active boolean not null,
    updated_by_user_id bigint references app_user(id) on delete set null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique(company_id)
);

alter table qbo_connection add column if not exists credential_source varchar(64) not null default 'GLOBAL_FALLBACK';
alter table qbo_connection add column if not exists client_id_hint varchar(32);
