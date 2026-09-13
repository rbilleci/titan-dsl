CREATE TABLE app.users (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    country VARCHAR(2) NOT NULL,
    manager_id INTEGER REFERENCES app.users(id)
);
