create table if not exists app_job (
    id bigserial primary key,
    company_id bigint not null references company(id),
    type varchar(100) not null,
    status varchar(50) not null,
    description varchar(255) not null,
    total_units integer not null default 0,
    completed_units integer not null default 0,
    summary_message text,
    result_payload text,
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone
);

create index if not exists idx_app_job_company_type_created
    on app_job(company_id, type, created_at desc);
