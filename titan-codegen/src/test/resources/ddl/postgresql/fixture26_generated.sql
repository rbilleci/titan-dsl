CREATE TABLE public.orders_26 (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (customer_id, created_at)
);

CREATE TABLE public.order_items_26 (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    sku VARCHAR(40) NOT NULL,
    qty INTEGER NOT NULL,
    CONSTRAINT fk_order_items_26_orders_26 FOREIGN KEY (order_id) REFERENCES public.orders_26(id)
);

CREATE INDEX idx_order_items_26_sku ON public.order_items_26(sku);

CREATE VIEW public.active_orders_26 AS
SELECT id, customer_id, status
FROM public.orders_26
WHERE status = 'ACTIVE';
