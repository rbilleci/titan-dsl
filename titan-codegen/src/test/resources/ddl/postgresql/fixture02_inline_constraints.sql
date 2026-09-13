CREATE TABLE public.tenants (
  tenant_id int4 PRIMARY KEY,
  external_id varchar(64) UNIQUE,
  created_at timestamp without time zone NOT NULL
);
