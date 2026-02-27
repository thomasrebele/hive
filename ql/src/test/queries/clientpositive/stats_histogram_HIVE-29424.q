set hive.vectorized.execution.enabled=false;

select '\nground truth of test data for HIVE-29424\n';

select '\nVALUES\n';

select '\ncheck selectivity of BETWEEN with NULLS\n';

CREATE TABLE test_stats1 (e int) stored as orc;

insert into test_stats1 (e) values 
(1), (2), (2), (2), (2),
(2), (2), (2), (3), (4),
(5), (6), (7), (NULL), (NULL),
(NULL), (NULL), (NULL), (NULL), (NULL);

select count(*) from test_stats1 where e BETWEEN -1000 AND 1000;
select count(*) from test_stats1 where e BETWEEN 1 AND 3;
select count(*) from test_stats1 where e NOT BETWEEN 1 AND 3;

select '\nVALUES2\n';

CREATE TABLE tabVALUES2 (e decimal(38,10), inc boolean) stored as orc;

insert into tabVALUES2 (e, inc) values
(-99.95001, true),
(-99.950005, true),
(-99.95, true),
(-99.94999, true),
(-99.94998, true),
(0.0, true),
(1.0, true),
(10.0, true),
(99.94998, true),
(99.94999, true),
(99.95, true),
(99.950005, true),
(99.95001, true),
(99.999985, true),
(99.99999, true),
(100.0, true),
(100.00001, true),
(100.000015, true),
(100.04999, true),
(100.049995, true),
(100.05, true),
(100.05001, true),
(100.05002, true),
(1000.0, true),
(10000.0, true),
(100000.0, true),
(1000000.0, true),
(10000000.0, true);

select * from tabVALUES2;
select CAST(e as DECIMAL(3,1)) from tabVALUES2;

select '\ndecimal(2,1)';

select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) < 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(2,1)) < 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) <= 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(2,1)) <= 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) > 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(2,1)) > 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) >= 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(2,1)) >= 100.0;


select '\ndecimal(3,1)';

select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) < 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) < 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) <= 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) <= 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) > 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) > 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) >= 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) >= 100.0;

select '\ndecimal(4,1)';

select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) < 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(4,1)) < 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) <= 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(4,1)) <= 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) > 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(4,1)) > 100.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) >= 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(4,1)) >= 100.0;

select '\nother comparisons decimal(7,1)';
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) > 10000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) > 10000.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) >= 9999.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) >= 9999.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) >= 10000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) >= 10000.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) > 10000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) > 10000.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) > 10001.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) > 10001.0;


select '\nother comparisons decimal(3,1)';
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) >= 1.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) >= 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) > 1.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) > 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) <= 1.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) <= 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) < 1.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) < 1.0;


select '\nbetween';

select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) IS NOT NULL;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) IS NOT NULL;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) IS NOT NULL;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) IS NOT NULL;


select '\ntinyint between';
select count(*) from tabVALUES2 where CAST(e as TINYINT) BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as TINYINT) BETWEEN 100.0 AND 1000.0;
select count(*) from tabVALUES2 where CAST(e as TINYINT) NOT BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as TINYINT) NOT BETWEEN 100.0 AND 1000.0;

select count(*) from tabVALUES2 where CAST(e as TINYINT) BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as TINYINT) BETWEEN 1.0 AND 100.0;
select count(*) from tabVALUES2 where CAST(e as TINYINT) NOT BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as TINYINT) NOT BETWEEN 1.0 AND 100.0;


select '\ndecimal(2,1) between';
select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(2,1)) BETWEEN 100.0 AND 1000.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) NOT BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(2,1)) NOT BETWEEN 100.0 AND 1000.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(2,1)) BETWEEN 1.0 AND 100.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) NOT BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(2,1)) NOT BETWEEN 1.0 AND 100.0;


select '\ndecimal(3,1) between';
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) BETWEEN 100.0 AND 1000.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) NOT BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) NOT BETWEEN 100.0 AND 1000.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) BETWEEN 1.0 AND 100.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) NOT BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(3,1)) NOT BETWEEN 1.0 AND 100.0;


select '\ndecimal(4,1) between';
select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(4,1)) BETWEEN 100.0 AND 1000.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) NOT BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(4,1)) NOT BETWEEN 100.0 AND 1000.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(4,1)) BETWEEN 1.0 AND 100.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) NOT BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(4,1)) NOT BETWEEN 1.0 AND 100.0;


select '\ndecimal(7,1) between';
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) BETWEEN 100.0 AND 1000.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) NOT BETWEEN 100.0 AND 1000.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) NOT BETWEEN 100.0 AND 1000.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) BETWEEN 1.0 AND 100.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) NOT BETWEEN 1.0 AND 100.0;
select * from tabVALUES2 where CAST(e as DECIMAL(7,1)) NOT BETWEEN 1.0 AND 100.0;


select '\nsome corner cases';
select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) BETWEEN 100.0 AND 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) BETWEEN 100.0 AND 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) BETWEEN 100.0 AND 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) BETWEEN 100.0 AND 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(2,1)) NOT BETWEEN 100.0 AND 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(3,1)) NOT BETWEEN 100.0 AND 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(4,1)) NOT BETWEEN 100.0 AND 1.0;
select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) NOT BETWEEN 100.0 AND 1.0;

select count(*) from tabVALUES2 where CAST(e as DECIMAL(7,1)) BETWEEN 100.0 AND 100.0;

select '\nVALUES3\n';

CREATE TABLE tabVALUES3 (e decimal(38,10)) stored as orc;

insert into tabVALUES3 (e) values
(-9.223373E18),
(-9.223372E18),
(9.223372E18),
(9.223373E18),

(-2.147484E9),
(-2.1474836E9),
(2.1474836E9),
(2.147484E9),

(-32769.0),
(-32768.996),
(32767.998),
(32768.0),

(-129),
(-128.99998),
(127.99999),
(128.0),

(10),
(10.0001),
(10.9999),
(11)

;


SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) IS NOT NULL;
SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) BETWEEN 0 AND 10;
SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) BETWEEN 0 AND 10.9999;
SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) BETWEEN 0 AND 11;
SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) BETWEEN 10 AND 20;
SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) BETWEEN 10.9999 AND 20;
SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) BETWEEN 11 AND 20;

SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) BETWEEN 0 AND 1E20;
SELECT count(*) from tabVALUES3 where CAST(e as TINYINT) BETWEEN -1E20 AND 0;


SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) BETWEEN 0 AND 10;
SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) BETWEEN 0 AND 10.9999;
SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) BETWEEN 0 AND 11;
SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) BETWEEN 10 AND 20;
SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) BETWEEN 10.9999 AND 20;
SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) BETWEEN 11 AND 20;

SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) IS NOT NULL;
SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) BETWEEN 0 AND 1E20;
SELECT count(*) from tabVALUES3 where CAST(e as SMALLINT) BETWEEN -1E20 AND 0;


SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) BETWEEN 0 AND 10;
SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) BETWEEN 0 AND 10.9999;
SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) BETWEEN 0 AND 11;
SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) BETWEEN 10 AND 20;
SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) BETWEEN 10.9999 AND 20;
SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) BETWEEN 11 AND 20;

SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) IS NOT NULL;
SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) BETWEEN 0 AND 1E20;
SELECT count(*) from tabVALUES3 where CAST(e as INTEGER) BETWEEN -1E20 AND 0;


SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) BETWEEN 0 AND 10;
SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) BETWEEN 0 AND 10.9999;
SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) BETWEEN 0 AND 11;
SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) BETWEEN 10 AND 20;
SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) BETWEEN 10.9999 AND 20;
SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) BETWEEN 11 AND 20;

SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) IS NOT NULL;
SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) BETWEEN 0 AND 1E20;
SELECT count(*) from tabVALUES3 where CAST(e as BIGINT) BETWEEN -1E20 AND 0;






select '\nVALUES_tmp1\n';

CREATE TABLE tabVALUES_tmp1 (e integer) stored as orc;

insert into tabVALUES_tmp1 (e) values
(-101),
(-100),
(-99),
(0),
(1),
(10),
(99),
(100),
(101),
(1000),
(10000),
(100000)
;

select * from tabVALUES_tmp1;
select CAST(e as DECIMAL(3,1)) from tabVALUES_tmp1;
select CAST(e as DECIMAL(3,1)) from tabVALUES_tmp1;


select '\ndecimal(2,1)';

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) < 100.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) < 100.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) < 99.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) < 99.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) < 101.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) < 101.0;


select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) > -100.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) > -100.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) > -99.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) > -99.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) > -101.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(2,1)) > -101.0;



select '\ndecimal(3,1)';

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) < 100.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) < 100.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) < 99.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) < 99.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) < 101.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) < 101.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) > -100.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) > -100.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) > -99.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) > -99.0;

select count(*) from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) > -101.0;
select * from tabVALUES_tmp1 where CAST(e as DECIMAL(3,1)) > -101.0;









select '\nVALUES_tmp2\n';



CREATE TABLE tabVALUES_tmp2 (e decimal(38,10), inc boolean) stored as orc;

insert into tabVALUES_tmp2 (e, inc) values
(-129, true),
(-128.99999, true),
(-128.95, true),
(-128.9499, true),
(127.94998, true),
(127.94999, true),
(127.95, true),
(127.99999, true),
(128, true);

select CAST(e as TINYINT) from tabVALUES_tmp2;



select '\nVALUES_TIME\n';

CREATE TABLE tabVALUES_TIME (ts timestamp, d date) stored as orc;

insert into tabVALUES_TIME (ts, d) values
("2020-11-01", "2020-11-01"),
("2020-11-02", "2020-11-02"),
("2020-11-03", "2020-11-03"),
("2020-11-04", "2020-11-04"),
("2020-11-05T11:23:45", "2020-11-05T11:23:45"),
("2020-11-06", "2020-11-06"),
("2020-11-07", "2020-11-07");

select '\nwithout cast\n';

select count(*) from tabVALUES_TIME where ts >= "2020-11-01";
select * from tabVALUES_TIME where ts >= "2020-11-01";

select count(*) from tabVALUES_TIME where ts >= "2020-11-03";
select * from tabVALUES_TIME where ts >= "2020-11-03";

select count(*) from tabVALUES_TIME where ts >= "2020-11-07";
select * from tabVALUES_TIME where ts >= "2020-11-07";


select count(*) from tabVALUES_TIME where ts > "2020-11-01";
select * from tabVALUES_TIME where ts > "2020-11-01";

select count(*) from tabVALUES_TIME where ts > "2020-11-03";
select * from tabVALUES_TIME where ts > "2020-11-03";

select count(*) from tabVALUES_TIME where ts > "2020-11-07";
select * from tabVALUES_TIME where ts > "2020-11-07";


select count(*) from tabVALUES_TIME where ts <= "2020-11-01";
select * from tabVALUES_TIME where ts <= "2020-11-01";

select count(*) from tabVALUES_TIME where ts <= "2020-11-03";
select * from tabVALUES_TIME where ts <= "2020-11-03";

select count(*) from tabVALUES_TIME where ts <= "2020-11-07";
select * from tabVALUES_TIME where ts <= "2020-11-07";


select count(*) from tabVALUES_TIME where ts < "2020-11-01";
select * from tabVALUES_TIME where ts < "2020-11-01";

select count(*) from tabVALUES_TIME where ts < "2020-11-03";
select * from tabVALUES_TIME where ts < "2020-11-03";

select count(*) from tabVALUES_TIME where ts < "2020-11-07";
select * from tabVALUES_TIME where ts < "2020-11-07";


select '\nwith cast(... AS TIMESTAMP)\n';

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) >= "2020-11-01";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) >= "2020-11-01";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) >= "2020-11-03";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) >= "2020-11-03";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) >= "2020-11-07";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) >= "2020-11-07";


select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) > "2020-11-01";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) > "2020-11-01";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) > "2020-11-03";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) > "2020-11-03";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) > "2020-11-07";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) > "2020-11-07";


select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) <= "2020-11-01";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) <= "2020-11-01";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) <= "2020-11-03";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) <= "2020-11-03";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) <= "2020-11-07";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) <= "2020-11-07";


select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) < "2020-11-01";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) < "2020-11-01";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) < "2020-11-03";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) < "2020-11-03";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) < "2020-11-07";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) < "2020-11-07";


select '\nwith cast(... AS DATE)\n';

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) >= "2020-11-01";
select * from tabVALUES_TIME where CAST(ts AS DATE) >= "2020-11-01";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) >= "2020-11-03";
select * from tabVALUES_TIME where CAST(ts AS DATE) >= "2020-11-03";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) >= "2020-11-07";
select * from tabVALUES_TIME where CAST(ts AS DATE) >= "2020-11-07";


select count(*) from tabVALUES_TIME where CAST(ts AS DATE) > "2020-11-01";
select * from tabVALUES_TIME where CAST(ts AS DATE) > "2020-11-01";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) > "2020-11-03";
select * from tabVALUES_TIME where CAST(ts AS DATE) > "2020-11-03";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) > "2020-11-07";
select * from tabVALUES_TIME where CAST(ts AS DATE) > "2020-11-07";


select count(*) from tabVALUES_TIME where CAST(ts AS DATE) <= "2020-11-01";
select * from tabVALUES_TIME where CAST(ts AS DATE) <= "2020-11-01";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) <= "2020-11-03";
select * from tabVALUES_TIME where CAST(ts AS DATE) <= "2020-11-03";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) <= "2020-11-07";
select * from tabVALUES_TIME where CAST(ts AS DATE) <= "2020-11-07";


select count(*) from tabVALUES_TIME where CAST(ts AS DATE) < "2020-11-01";
select * from tabVALUES_TIME where CAST(ts AS DATE) < "2020-11-01";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) < "2020-11-03";
select * from tabVALUES_TIME where CAST(ts AS DATE) < "2020-11-03";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) < "2020-11-07";
select * from tabVALUES_TIME where CAST(ts AS DATE) < "2020-11-07";

select '\ncompare with non-midnight timestamp\n';


select count(*) from tabVALUES_TIME where ts >= "2020-11-05T11:23:45";
select * from tabVALUES_TIME where ts >= "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where ts > "2020-11-05T11:23:45";
select * from tabVALUES_TIME where ts > "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where ts <= "2020-11-05T11:23:45";
select * from tabVALUES_TIME where ts <= "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where ts < "2020-11-05T11:23:45";
select * from tabVALUES_TIME where ts < "2020-11-05T11:23:45";


select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) >= "2020-11-05T11:23:45";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) >= "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) > "2020-11-05T11:23:45";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) > "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) <= "2020-11-05T11:23:45";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) <= "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where CAST(ts AS TIMESTAMP) < "2020-11-05T11:23:45";
select * from tabVALUES_TIME where CAST(ts AS TIMESTAMP) < "2020-11-05T11:23:45";


select count(*) from tabVALUES_TIME where CAST(ts AS DATE) >= "2020-11-05T11:23:45";
select * from tabVALUES_TIME where CAST(ts AS DATE) >= "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) > "2020-11-05T11:23:45";
select * from tabVALUES_TIME where CAST(ts AS DATE) > "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) <= "2020-11-05T11:23:45";
select * from tabVALUES_TIME where CAST(ts AS DATE) <= "2020-11-05T11:23:45";

select count(*) from tabVALUES_TIME where CAST(ts AS DATE) < "2020-11-05T11:23:45";
select * from tabVALUES_TIME where CAST(ts AS DATE) < "2020-11-05T11:23:45";












select '\nrounding mode\n';

CREATE TABLE rounding_mode (a decimal(38,24)) stored as orc;

insert into rounding_mode (a) values (5.5), (2.5), (1.6), (1.1),
(1.0), (-1.0), (-1.1), (-1.6), (-2.5), (-5.5);

select a, CAST(a as DECIMAL(10,0)) from rounding_mode;

