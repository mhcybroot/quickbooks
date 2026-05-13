alter table app_user add column if not exists blocked boolean not null default false;
alter table app_user add column if not exists must_change_password boolean not null default false;
alter table app_user add column if not exists blocked_reason varchar(512);
alter table app_user add column if not exists blocked_at timestamp with time zone;
alter table app_user add column if not exists blocked_by_user_id bigint;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_schema = 'public'
          and table_name = 'app_user'
          and constraint_name = 'fk_app_user_blocked_by_user'
    ) then
        alter table app_user
            add constraint fk_app_user_blocked_by_user
            foreign key (blocked_by_user_id) references app_user(id) on delete set null;
    end if;
end
$$;
