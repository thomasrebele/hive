PREHOOK: query: create table tab1 (id string, col string) stored as orc
PREHOOK: type: CREATETABLE
PREHOOK: Output: database:default
PREHOOK: Output: default@tab1
POSTHOOK: query: create table tab1 (id string, col string) stored as orc
POSTHOOK: type: CREATETABLE
POSTHOOK: Output: database:default
POSTHOOK: Output: default@tab1
PREHOOK: query: select * from tab1 a join tab1 b on a.id = b.id
where lower(a.col) not in ('a', 'b') or lower(a.col) is null
PREHOOK: type: QUERY
PREHOOK: Input: default@tab1
#### A masked pattern was here ####
POSTHOOK: query: select * from tab1 a join tab1 b on a.id = b.id
where lower(a.col) not in ('a', 'b') or lower(a.col) is null
POSTHOOK: type: QUERY
POSTHOOK: Input: default@tab1
#### A masked pattern was here ####
