CREATE TABLE `orders_20` (
    `id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_orders_20_cust_created` (`customer_id`, `created_at`)
);

CREATE TABLE `order_items_20` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `sku` VARCHAR(40) NOT NULL,
    `qty` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_20_orders_20` FOREIGN KEY (`order_id`) REFERENCES `orders_20`(`id`)
);

CREATE INDEX `idx_order_items_20_sku` ON `order_items_20` (`sku`);

CREATE VIEW `active_orders_20` AS
SELECT `id`, `customer_id`, `status`
FROM `orders_20`
WHERE `status` = 'ACTIVE';
