CREATE TABLE "public"."tenants" (
  "tenant_id" int4 PRIMARY KEY
);

CREATE TABLE "public"."user_profiles" (
  "id" int4 NOT NULL,
  "display_name" varchar(120) NOT NULL,
  "tenant_id" int4,
  CONSTRAINT "user_profiles_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "user_profiles_tenant_fk" FOREIGN KEY ("tenant_id") REFERENCES "public"."tenants" ("tenant_id")
);
