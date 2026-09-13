CREATE TABLE public.audit_log (
  id int4 NOT NULL,
  actor varchar(128),
  action varchar(128) NOT NULL,
  created_at timestamp without time zone NOT NULL,
  CONSTRAINT audit_log_pkey PRIMARY KEY (id)
);

CREATE VIEW public.recent_audit AS
SELECT a.id, a.actor, a.action
FROM public.audit_log a;
