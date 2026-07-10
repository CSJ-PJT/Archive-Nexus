alter table nexus_workforce_allocation
  add column if not exists allocation_id varchar(120),
  add column if not exists target_service varchar(80) not null default 'Archive-Nexus',
  add column if not exists role_type varchar(60),
  add column if not exists allocated_headcount integer,
  add column if not exists capacity_per_person_per_day integer not null default 0,
  add column if not exists productivity_score numeric(8,4) not null default 1,
  add column if not exists wage_per_day numeric(18,2) not null default 0,
  add column if not exists effective_capacity integer not null default 0,
  add column if not exists used_capacity integer not null default 0,
  add column if not exists remaining_capacity integer not null default 0,
  add column if not exists updated_at timestamptz;

update nexus_workforce_allocation
set allocation_id = coalesce(allocation_id, event_id),
    role_type = coalesce(role_type, workforce_role),
    allocated_headcount = coalesce(allocated_headcount, assigned_units),
    wage_per_day = case when wage_per_day = 0 then cost_per_unit_krw else wage_per_day end,
    productivity_score = case when productivity_score = 1 then skill_level else productivity_score end,
    updated_at = coalesce(updated_at, created_at)
where allocation_id is null
   or role_type is null
   or allocated_headcount is null
   or updated_at is null;

create unique index if not exists nexus_workforce_allocation_allocation_id_key
  on nexus_workforce_allocation(allocation_id);

create index if not exists nexus_workforce_allocation_workday_role_idx
  on nexus_workforce_allocation(workday_id, role_type, status);

create table if not exists nexus_workday_result (
  id bigserial primary key,
  workday_id varchar(120) not null unique,
  work_date date not null,
  total_capacity integer not null default 0,
  used_capacity integer not null default 0,
  remaining_capacity integer not null default 0,
  production_requested integer not null default 0,
  production_completed integer not null default 0,
  production_backlog integer not null default 0,
  quality_checked integer not null default 0,
  quality_defects integer not null default 0,
  maintenance_completed integer not null default 0,
  payroll_cost numeric(18,2) not null default 0,
  productivity_score numeric(8,4) not null default 0,
  bottleneck_role varchar(60),
  status varchar(40) not null,
  evidence_json text not null default '{}',
  created_at timestamptz not null
);

create index if not exists nexus_workday_result_date_idx
  on nexus_workday_result(work_date desc, created_at desc);
