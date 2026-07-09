alter table nexus_outbox_event
  alter column payload type text using payload::text;
