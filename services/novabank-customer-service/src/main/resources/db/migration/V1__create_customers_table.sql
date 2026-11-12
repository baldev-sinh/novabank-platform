CREATE TABLE customers (
    id            UUID         PRIMARY KEY,
    -- The auth service's user id. One customer profile per user.
    user_id       UUID         NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    date_of_birth DATE         NOT NULL,
    -- E.164: "+" and at most 15 digits.
    phone         VARCHAR(16)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    -- Also the index for lookups by user_id.
    CONSTRAINT uk_customers_user_id UNIQUE (user_id),
    CONSTRAINT ck_customers_status
        CHECK (status IN ('PENDING_KYC', 'ACTIVE', 'INACTIVE', 'BLOCKED'))
);
