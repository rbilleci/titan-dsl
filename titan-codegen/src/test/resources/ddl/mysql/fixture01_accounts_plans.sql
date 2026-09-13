CREATE TABLE plans (
  id int NOT NULL,
  code varchar(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_plans_code (code)
);

CREATE TABLE accounts (
  id int NOT NULL,
  email varchar(255) NOT NULL,
  plan_id int,
  active boolean NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_accounts_email (email),
  CONSTRAINT fk_accounts_plan FOREIGN KEY (plan_id) REFERENCES plans(id),
  KEY idx_accounts_active (active)
);
