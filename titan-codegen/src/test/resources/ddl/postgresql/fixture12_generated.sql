CREATE TABLE public.orders_12 (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (customer_id, created_at)
);

CREATE TABLE public.order_items_12 (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    sku VARCHAR(40) NOT NULL,
    qty INTEGER NOT NULL,
    CONSTRAINT fk_order_items_12_orders_12 FOREIGN KEY (order_id) REFERENCES public.orders_12(id)
);

CREATE INDEX idx_order_items_12_sku ON public.order_items_12(sku);

CREATE VIEW public.active_orders_12 AS
SELECT id, customer_id, status
FROM public.orders_12
WHERE status = 'ACTIVE';
