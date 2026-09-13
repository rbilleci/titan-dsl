CREATE TABLE audit_log (
  id int NOT NULL,
  actor varchar(128),
  action varchar(128) NOT NULL,
  created_at datetime NOT NULL,
  PRIMARY KEY (id)
);

CREATE VIEW recent_audit AS
SELECT a.id, a.actor, a.action
FROM audit_log a;
