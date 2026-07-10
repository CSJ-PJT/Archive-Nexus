create table if not exists nexus_workforce_allocation (
  id bigserial primary key,
  event_id varchar(120) not null unique,
  idempotency_key varchar(180) not null unique,
  source_service varchar(80) not null,
  workforce_role varchar(40) not null,
  assigned_units integer not null,
  skill_level numeric(8,4) not null default 1,
  cost_per_unit_krw numeric(18,2) not null default 0,
  workday_id varchar(120),
  simulation_run_id varchar(120),
  settlement_cycle_id varchar(120),
  correlation_id varchar(120),
  causation_id varchar(120),
  hop_count integer not null default 0,
  max_hop integer not null default 8,
  status varchar(40) not null default 'ACTIVE',
  reason text,
  payload_json text not null default '{}',
  assigned_at timestamptz not null,
  created_at timestamptz not null
);

create index if not exists nexus_workforce_allocation_role_idx
  on nexus_workforce_allocation(workforce_role, status, created_at desc);

create index if not exists nexus_workforce_allocation_workday_idx
  on nexus_workforce_allocation(workday_id, created_at desc);

create table if not exists nexus_workday_productivity (
  id bigserial primary key,
  workday_id varchar(120) not null unique,
  work_date date not null,
  simulation_run_id varchar(120),
  settlement_cycle_id varchar(120),
  correlation_id varchar(120),
  causation_id varchar(120),
  total_capacity integer not null,
  processed_units integer not null,
  backlog_before integer not null,
  backlog_after integer not null,
  labor_cost_krw numeric(18,2) not null default 0,
  productivity_rate numeric(8,4) not null default 0,
  bottleneck_role varchar(40),
  status varchar(40) not null,
  evidence_json text not null default '{}',
  created_at timestamptz not null
);

create index if not exists nexus_workday_productivity_date_idx
  on nexus_workday_productivity(work_date desc, created_at desc);
