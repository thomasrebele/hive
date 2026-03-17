set hive.stats.autogather=true;
set hive.stats.column.autogather=true;

CREATE TABLE test_stats1 (a int, b timestamp with local time zone, c int) STORED AS TEXTFILE;
INSERT INTO test_stats1 (a, b, c) VALUES (1, "2020-11-02 00:00:00", 2);
DESCRIBE FORMATTED test_stats1 a;
DESCRIBE FORMATTED test_stats1 b;
DESCRIBE FORMATTED test_stats1 c;


CREATE TABLE test_stats0 (a int, b timestamp, c int) STORED AS TEXTFILE;
INSERT INTO test_stats0 (a, b, c) VALUES (1, "2020-11-02 00:00:00", 2);
DESCRIBE FORMATTED test_stats0 a;
DESCRIBE FORMATTED test_stats0 b;
DESCRIBE FORMATTED test_stats0 c;
