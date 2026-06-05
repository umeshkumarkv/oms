-- SCRUM-139: add search/typeahead columns to instruments

ALTER TABLE market_schema.instruments
    ADD COLUMN IF NOT EXISTS company_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS cusip        VARCHAR(9),
    ADD COLUMN IF NOT EXISTS sec_number   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS exchange     VARCHAR(20);