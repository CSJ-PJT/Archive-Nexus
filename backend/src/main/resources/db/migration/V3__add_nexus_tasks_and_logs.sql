create table if not exists nexus_tasks (
 id varchar(48) primary key,title varchar(180) not null,task_type varchar(40) not null,factory_id varchar(40),question text,
 requested_by varchar(120) not null,status varchar(20) not null,attempt_count integer not null default 0,max_attempts integer not null default 3,
 result_summary text,error_message text,created_at timestamptz not null,started_at timestamptz,completed_at timestamptz,updated_at timestamptz not null
);
create index if not exists nexus_tasks_status_idx on nexus_tasks(status);
create index if not exists nexus_tasks_created_at_idx on nexus_tasks(created_at desc);
create table if not exists nexus_task_logs (
 id bigserial primary key,task_id varchar(48) not null references nexus_tasks(id) on delete cascade,level varchar(16) not null,message text not null,created_at timestamptz not null
);
create index if not exists nexus_task_logs_task_id_idx on nexus_task_logs(task_id,created_at);
