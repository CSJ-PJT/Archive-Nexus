alter table nexus_tasks alter column status type varchar(40);
alter table nexus_tasks add column if not exists evidence_json text;
alter table nexus_tasks add column if not exists recommendation_json text;
alter table nexus_tasks add column if not exists confidence double precision;
alter table nexus_tasks add column if not exists correlation_id varchar(80);
alter table nexus_tasks add column if not exists workflow_id varchar(80);
alter table nexus_tasks add column if not exists approval_id varchar(80);
alter table nexus_tasks add column if not exists rpa_task_id varchar(80);
update nexus_tasks set correlation_id = 'LEGACY-' || id where correlation_id is null;
alter table nexus_tasks alter column correlation_id set not null;
create unique index if not exists nexus_tasks_correlation_id_idx on nexus_tasks(correlation_id);
create index if not exists nexus_tasks_workflow_id_idx on nexus_tasks(workflow_id);

create table if not exists nexus_audit_logs (
  id bigserial primary key,
  actor varchar(120) not null,
  action varchar(100) not null,
  reason text,
  task_id varchar(48),
  correlation_id varchar(80),
  workflow_id varchar(80),
  details_json text not null,
  occurred_at timestamptz not null
);
create index if not exists nexus_audit_logs_task_idx on nexus_audit_logs(task_id, occurred_at desc);
create index if not exists nexus_audit_logs_correlation_idx on nexus_audit_logs(correlation_id, occurred_at desc);

create table if not exists production_aggregates (
  aggregate_id varchar(40) primary key, factory_id varchar(40) not null, target_quantity bigint not null,
  produced_quantity bigint not null, projected_at timestamptz not null
);
create table if not exists inspection_aggregates (
  aggregate_id varchar(40) primary key, factory_id varchar(40) not null, inspection_count bigint not null,
  average_defect_rate double precision not null, projected_at timestamptz not null
);
create table if not exists maintenance_aggregates (
  aggregate_id varchar(40) primary key, factory_id varchar(40) not null, open_event_count bigint not null,
  critical_event_count bigint not null, projected_at timestamptz not null
);
create table if not exists approval_aggregates (
  aggregate_id varchar(48) primary key, task_id varchar(48) not null, workflow_id varchar(80), approval_id varchar(80),
  status varchar(40) not null, projected_at timestamptz not null
);
