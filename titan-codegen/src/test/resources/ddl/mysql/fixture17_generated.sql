CREATE TABLE `orders_17` (
    `id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_orders_17_cust_created` (`customer_id`, `created_at`)
);

CREATE TABLE `order_items_17` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `sku` VARCHAR(40) NOT NULL,
    `qty` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_17_orders_17` FOREIGN KEY (`order_id`) REFERENCES `orders_17`(`id`)
);

CREATE INDEX `idx_order_items_17_sku` ON `order_items_17` (`sku`);

CREATE VIEW `active_orders_17` AS
SELECT `id`, `customer_id`, `status`
FROM `orders_17`
WHERE `status` = 'ACTIVE';
