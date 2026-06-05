-- SCRUM-139: seed company_name, cusip, sec_number, exchange for all instruments

UPDATE market_schema.instruments SET company_name = 'Apple Inc.',              cusip = '037833100', sec_number = 'AAPL-US',  exchange = 'NASDAQ' WHERE symbol = 'AAPL';
UPDATE market_schema.instruments SET company_name = 'Microsoft Corporation',   cusip = '594918104', sec_number = 'MSFT-US',  exchange = 'NASDAQ' WHERE symbol = 'MSFT';
UPDATE market_schema.instruments SET company_name = 'Alphabet Inc.',           cusip = '02079K305', sec_number = 'GOOGL-US', exchange = 'NASDAQ' WHERE symbol = 'GOOGL';
UPDATE market_schema.instruments SET company_name = 'Tesla Inc.',              cusip = '88160R101', sec_number = 'TSLA-US',  exchange = 'NASDAQ' WHERE symbol = 'TSLA';
UPDATE market_schema.instruments SET company_name = 'Amazon.com Inc.',         cusip = '023135106', sec_number = 'AMZN-US',  exchange = 'NASDAQ' WHERE symbol = 'AMZN';
UPDATE market_schema.instruments SET company_name = 'NVIDIA Corporation',      cusip = '67066G104', sec_number = 'NVDA-US',  exchange = 'NASDAQ' WHERE symbol = 'NVDA';
UPDATE market_schema.instruments SET company_name = 'Meta Platforms Inc.',     cusip = '30303M102', sec_number = 'META-US',  exchange = 'NASDAQ' WHERE symbol = 'META';
UPDATE market_schema.instruments SET company_name = 'JPMorgan Chase & Co.',    cusip = '46625H100', sec_number = 'JPM-US',   exchange = 'NYSE'   WHERE symbol = 'JPM';
UPDATE market_schema.instruments SET company_name = 'Bank of America Corp.',   cusip = '060505104', sec_number = 'BAC-US',   exchange = 'NYSE'   WHERE symbol = 'BAC';
UPDATE market_schema.instruments SET company_name = 'Test Halted Stock',       cusip = 'TEST00001', sec_number = 'HALT-US',  exchange = 'NYSE'   WHERE symbol = 'HALT';