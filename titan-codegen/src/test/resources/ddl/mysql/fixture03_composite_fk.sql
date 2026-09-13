CREATE TABLE accounts (
  id int PRIMARY KEY
);

CREATE TABLE orders (
  tenant_id int NOT NULL,
  order_id int NOT NULL,
  account_id int NOT NULL,
  amount decimal(10,2),
  PRIMARY KEY (tenant_id, order_id),
  CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  UNIQUE KEY uq_orders_tenant_account (tenant_id, account_id)
);
