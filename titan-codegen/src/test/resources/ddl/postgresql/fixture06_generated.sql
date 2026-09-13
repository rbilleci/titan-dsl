CREATE TABLE public.orders_6 (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (customer_id, created_at)
);

CREATE TABLE public.order_items_6 (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    sku VARCHAR(40) NOT NULL,
    qty INTEGER NOT NULL,
    CONSTRAINT fk_order_items_6_orders_6 FOREIGN KEY (order_id) REFERENCES public.orders_6(id)
);

CREATE INDEX idx_order_items_6_sku ON public.order_items_6(sku);

CREATE VIEW public.active_orders_6 AS
SELECT id, customer_id, status
FROM public.orders_6
WHERE status = 'ACTIVE';
