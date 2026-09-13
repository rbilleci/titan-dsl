CREATE TABLE `tenants` (
  `tenant_id` int PRIMARY KEY
);

CREATE TABLE `user_profiles` (
  `id` int NOT NULL,
  `display_name` varchar(120) NOT NULL,
  `tenant_id` int,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_user_profiles_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`tenant_id`)
);
