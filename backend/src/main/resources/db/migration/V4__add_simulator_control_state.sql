create table if not exists simulator_control_state (
    id varchar(80) primary key,
    running boolean not null,
    tick bigint not null,
    last_parallel_worker_count integer not null,
    updated_at timestamptz not null
);

insert into simulator_control_state (id, running, tick, last_parallel_worker_count, updated_at)
select id, running, tick, last_parallel_worker_count, saved_at
from simulator_state
where id = 'archive-nexus-runtime'
on conflict (id) do nothing;
