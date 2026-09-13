CREATE TABLE `orders_9` (
    `id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_orders_9_cust_created` (`customer_id`, `created_at`)
);

CREATE TABLE `order_items_9` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `sku` VARCHAR(40) NOT NULL,
    `qty` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_9_orders_9` FOREIGN KEY (`order_id`) REFERENCES `orders_9`(`id`)
);

CREATE INDEX `idx_order_items_9_sku` ON `order_items_9` (`sku`);

CREATE VIEW `active_orders_9` AS
SELECT `id`, `customer_id`, `status`
FROM `orders_9`
WHERE `status` = 'ACTIVE';
