create table if not exists ai_query_history (
    query_id varchar(80) primary key,
    original_question text not null,
    requested_by varchar(120) not null,
    selected_factory_id varchar(80),
    routed_intents_json text not null,
    invoked_agents_json text not null,
    agent_results_json text not null,
    final_answer text not null,
    evidence_json text not null,
    recommended_actions_json text not null,
    confidence numeric(6,5) not null,
    execution_status varchar(40) not null,
    execution_time_ms bigint not null,
    error_message text,
    rpa_task_id varchar(80),
    created_at timestamptz not null
);

create index if not exists idx_ai_query_history_created_at on ai_query_history (created_at desc);
