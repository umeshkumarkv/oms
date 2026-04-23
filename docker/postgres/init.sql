-- Broker Dealer OMS POC — PostgreSQL Schema Initialization
-- Creates all service schemas in a single database

CREATE SCHEMA IF NOT EXISTS auth_schema;
CREATE SCHEMA IF NOT EXISTS order_schema;
CREATE SCHEMA IF NOT EXISTS client_schema;
CREATE SCHEMA IF NOT EXISTS market_schema;
CREATE SCHEMA IF NOT EXISTS risk_schema;
CREATE SCHEMA IF NOT EXISTS execution_schema;
CREATE SCHEMA IF NOT EXISTS audit_schema;