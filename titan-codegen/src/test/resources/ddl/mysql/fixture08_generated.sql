CREATE TABLE `orders_8` (
    `id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_orders_8_cust_created` (`customer_id`, `created_at`)
);

CREATE TABLE `order_items_8` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `sku` VARCHAR(40) NOT NULL,
    `qty` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_8_orders_8` FOREIGN KEY (`order_id`) REFERENCES `orders_8`(`id`)
);

CREATE INDEX `idx_order_items_8_sku` ON `order_items_8` (`sku`);

CREATE VIEW `active_orders_8` AS
SELECT `id`, `customer_id`, `status`
FROM `orders_8`
WHERE `status` = 'ACTIVE';
