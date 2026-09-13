CREATE TABLE public.plans (
  id int4 NOT NULL,
  code varchar(32) NOT NULL,
  CONSTRAINT plans_pkey PRIMARY KEY (id),
  CONSTRAINT plans_code_key UNIQUE (code)
);

CREATE TABLE public.accounts (
  id int4 NOT NULL,
  email varchar(255) NOT NULL,
  plan_id int4,
  active boolean NOT NULL,
  CONSTRAINT accounts_pkey PRIMARY KEY (id),
  CONSTRAINT accounts_email_key UNIQUE (email),
  CONSTRAINT accounts_plan_fk FOREIGN KEY (plan_id) REFERENCES public.plans (id)
);

CREATE INDEX idx_accounts_active ON public.accounts (active);
