alter table company_qbo_app_credentials add column if not exists qbo_environment varchar(32);
update company_qbo_app_credentials set qbo_environment = 'SANDBOX' where qbo_environment is null;
alter table company_qbo_app_credentials alter column qbo_environment set not null;
