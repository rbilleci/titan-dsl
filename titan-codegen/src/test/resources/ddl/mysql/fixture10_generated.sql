CREATE TABLE `orders_10` (
    `id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_orders_10_cust_created` (`customer_id`, `created_at`)
);

CREATE TABLE `order_items_10` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `sku` VARCHAR(40) NOT NULL,
    `qty` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_10_orders_10` FOREIGN KEY (`order_id`) REFERENCES `orders_10`(`id`)
);

CREATE INDEX `idx_order_items_10_sku` ON `order_items_10` (`sku`);

CREATE VIEW `active_orders_10` AS
SELECT `id`, `customer_id`, `status`
FROM `orders_10`
WHERE `status` = 'ACTIVE';
