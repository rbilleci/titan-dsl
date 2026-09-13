CREATE TABLE `orders_16` (
    `id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_orders_16_cust_created` (`customer_id`, `created_at`)
);

CREATE TABLE `order_items_16` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `sku` VARCHAR(40) NOT NULL,
    `qty` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_16_orders_16` FOREIGN KEY (`order_id`) REFERENCES `orders_16`(`id`)
);

CREATE INDEX `idx_order_items_16_sku` ON `order_items_16` (`sku`);

CREATE VIEW `active_orders_16` AS
SELECT `id`, `customer_id`, `status`
FROM `orders_16`
WHERE `status` = 'ACTIVE';
