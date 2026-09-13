CREATE TABLE `orders_27` (
    `id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_orders_27_cust_created` (`customer_id`, `created_at`)
);

CREATE TABLE `order_items_27` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `sku` VARCHAR(40) NOT NULL,
    `qty` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_27_orders_27` FOREIGN KEY (`order_id`) REFERENCES `orders_27`(`id`)
);

CREATE INDEX `idx_order_items_27_sku` ON `order_items_27` (`sku`);

CREATE VIEW `active_orders_27` AS
SELECT `id`, `customer_id`, `status`
FROM `orders_27`
WHERE `status` = 'ACTIVE';
