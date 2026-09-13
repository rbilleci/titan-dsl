CREATE TABLE tenants (
  tenant_id int PRIMARY KEY,
  external_id varchar(64) UNIQUE,
  created_at datetime NOT NULL
);
