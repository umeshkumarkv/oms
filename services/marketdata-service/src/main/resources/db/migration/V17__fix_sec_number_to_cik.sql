-- SCRUM-170: correct sec_number to SEC CIK format (V14 seeded ticker-exchange codes instead)
UPDATE market_schema.instruments SET sec_number = '320193'  WHERE symbol = 'AAPL';
UPDATE market_schema.instruments SET sec_number = '789019'  WHERE symbol = 'MSFT';
UPDATE market_schema.instruments SET sec_number = '1652044' WHERE symbol = 'GOOGL';
UPDATE market_schema.instruments SET sec_number = '1318605' WHERE symbol = 'TSLA';
UPDATE market_schema.instruments SET sec_number = '1018724' WHERE symbol = 'AMZN';
UPDATE market_schema.instruments SET sec_number = '1045810' WHERE symbol = 'NVDA';
UPDATE market_schema.instruments SET sec_number = '1326801' WHERE symbol = 'META';
UPDATE market_schema.instruments SET sec_number = '19617'   WHERE symbol = 'JPM';
UPDATE market_schema.instruments SET sec_number = '70858'   WHERE symbol = 'BAC';
UPDATE market_schema.instruments SET sec_number = '9999999' WHERE symbol = 'HALT';
