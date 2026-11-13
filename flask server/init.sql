CREATE TABLE IF NOT EXISTS Categories (
    category_id SERIAL PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    is_essential BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS expenses (
    expense_id SERIAL PRIMARY KEY,
    amount NUMERIC(10, 2) NOT NULL,
    description TEXT,
    expense_date DATE NOT NULL DEFAULT CURRENT_DATE,
    source VARCHAR(100),
    user_id INTEGER DEFAULT 1,
    category_id INTEGER REFERENCES Categories(category_id),
    status VARCHAR(50) DEFAULT 'categorized'
);

-- Default fallback category
INSERT INTO Categories (category_name, is_essential)
VALUES ('Uncategorized', TRUE)
ON CONFLICT (category_name) DO NOTHING;
