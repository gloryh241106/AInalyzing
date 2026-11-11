CREATE TABLE demo_cases (
    case_code    VARCHAR(40) PRIMARY KEY,
    title        TEXT        NOT NULL,
    request_json JSONB       NOT NULL,
    demand_slow  INTEGER[]   NOT NULL CHECK (cardinality(demand_slow) = 14),
    demand_base  INTEGER[]   NOT NULL CHECK (cardinality(demand_base) = 14),
    demand_fast  INTEGER[]   NOT NULL CHECK (cardinality(demand_fast) = 14)
);
