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

select '\nrounding mode\n';

CREATE TABLE rounding_mode (a decimal(38,24)) stored as orc;

insert into rounding_mode (a) values (5.5), (2.5), (1.6), (1.1),
(1.0), (-1.0), (-1.1), (-1.6), (-2.5), (-5.5);

select a, CAST(a as DECIMAL(10,0)) from rounding_mode;

