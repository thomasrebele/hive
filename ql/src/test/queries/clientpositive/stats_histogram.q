--! qt:replace:/(\s+numFiles\s+)\S+(\s+)/$1#Masked#$2/
--! qt:replace:/test_stats_[0-9]*/test_stats_#Masked#/

set hivevar:suffix=rand();
set hive.stats.kll.enable=true;
set metastore.stats.fetch.bitvector=true;
set metastore.stats.fetch.kll=true;
set hive.stats.autogather=true;
set hive.stats.column.autogather=true;

CREATE TABLE test_stats_${suffix} (a string, b int, c double, d float, e decimal(5,2), f timestamp, g date)
STORED AS ORC;

INSERT INTO test_stats_${suffix} (a, b, c, d, e, f, g) VALUES ("a", 2, 1.1, 12.2, 1.3, "2020-11-2 00:00:00", "2020-11-2")
,  ("b", 2, 2.1, NULL, 6.3, "2020-11-2 00:00:00", "2020-11-2")
,  ("c", 2, 2.1, NULL, -8.3, "2020-11-2 00:00:00", "2020-11-02")
,  ("d", 2, 3.1, 13.2, 10.2, "2020-11-2 00:00:00", "2020-11-2")
,  ("e", 2, 3.1, 14.2, 10.2, "2020-11-02 00:00:00", "2020-11-2")
,  ("f", 2, 4.1, NULL, 12.2, "2020-11-2 00:00:00", "2020-11-2")
,  ("g", 2, 5.1, 15.2, -10.2, "2020-11-2 00:00:00", "2020-11-2")
,  ("h", 2, 6.1, 16.2, 12.2, "2020-11-2 00:00:00", "2020-11-2")
,  ("i", 3, 6.1, 17.2, 7.2, "2020-11-03 00:00:00", "2020-11-3")
,  ("j", 4, NULL, 20.2, 1.2, "2020-11-4 00:00:00", "2020-11-4")
,  ("k", 5, NULL, 50.2, -123.2, "2020-11-5 00:00:00", "2020-11-05")
,  ("l", 6, NULL, 55.2, 1.2, "2020-11-6 00:00:00", "2020-11-6")
,  ("m", 7, 9.1, 57.2, 1001.2, "2020-11-7 00:00:00", "2020-11-7")
,  ("n", NULL, 100.1, 1000.2, 0.2, NULL, NULL)
,  ("o", NULL, 101.1, 2000.2, -1.2, NULL, NULL);

DESCRIBE FORMATTED test_stats_${suffix} b;
DESCRIBE FORMATTED test_stats_${suffix} c;
DESCRIBE FORMATTED test_stats_${suffix} d;
DESCRIBE FORMATTED test_stats_${suffix} e;
DESCRIBE FORMATTED test_stats_${suffix} f;
DESCRIBE FORMATTED test_stats_${suffix} g;

DESCRIBE FORMATTED test_stats_${suffix};
DESCRIBE FORMATTED test_stats_${suffix} a;

-- EXPLAIN SELECT COUNT(*)
-- FROM test_stats_${suffix} t1 JOIN test_stats_${suffix} t2 ON (t1.a = t2.a)
-- WHERE t1.b BETWEEN 3 AND 5 AND t2.c > 6.0;
-- SELECT COUNT(*)
-- FROM test_stats_${suffix} t1 JOIN test_stats_${suffix} t2 ON (t1.a = t2.a)
-- WHERE t1.b BETWEEN 3 AND 5 AND t2.c > 6.0;
-- 
-- EXPLAIN SELECT COUNT(*) FROM test_stats_${suffix} WHERE b < 3;
-- SELECT COUNT(*) FROM test_stats_${suffix} WHERE b < 3;
-- 
-- EXPLAIN SELECT COUNT(*) FROM test_stats_${suffix} WHERE b >= 7;
-- SELECT COUNT(*) FROM test_stats_${suffix} WHERE b >= 7;
-- 
-- EXPLAIN SELECT COUNT(*) FROM test_stats_${suffix} WHERE d NOT BETWEEN 3 AND 7 AND e > 0;
-- SELECT COUNT(*) FROM test_stats_${suffix} WHERE d NOT BETWEEN 3 AND 7 AND e > 0;
-- 
-- EXPLAIN SELECT COUNT(*) FROM test_stats_${suffix} WHERE f >= "2020-11-7" AND g >= "2020-11-7";
-- SELECT COUNT(*) FROM test_stats_${suffix} WHERE f >= "2020-11-7" AND g >= "2020-11-7";
-- 
-- EXPLAIN SELECT COUNT(*) FROM test_stats_${suffix} WHERE f BETWEEN "2020-11-01" AND "2020-11-06" AND g >= "2020-11-01";
-- SELECT COUNT(*) FROM test_stats_${suffix} WHERE f BETWEEN "2020-11-01" AND "2020-11-06" AND g >= "2020-11-01";

DROP TABLE test_stats_${suffix};
