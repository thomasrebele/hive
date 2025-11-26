-- check if kll is undefined
set metastore.stats.fetch.kll;

CREATE TABLE tab1 AS (SELECT 1 as key);

DESCRIBE FORMATTED tab1 key;
set metastore.stats.fetch.kll=true;

CREATE TABLE tab2 AS (SELECT 1 as key);

set hive.stats.kll.enable=true;
ANALYZE TABLE tab2 COMPUTE STATISTICS FOR COLUMNS;
DESCRIBE FORMATTED tab2 key;
