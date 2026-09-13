CREATE TABLE public.accounts (
  id int4 PRIMARY KEY
);

CREATE TABLE public.orders (
  tenant_id int4 NOT NULL,
  order_id int4 NOT NULL,
  account_id int4 NOT NULL,
  amount numeric(10,2),
  CONSTRAINT orders_pkey PRIMARY KEY (tenant_id, order_id),
  CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES public.accounts (id),
  UNIQUE (tenant_id, account_id)
);

CREATE TABLE public.order_lines (
  tenant_id int4 NOT NULL,
  order_id int4 NOT NULL,
  line_no int4 NOT NULL,
  CONSTRAINT order_lines_pkey PRIMARY KEY (tenant_id, order_id, line_no),
  CONSTRAINT fk_order_lines_order FOREIGN KEY (tenant_id, order_id) REFERENCES public.orders (tenant_id, order_id)
);
