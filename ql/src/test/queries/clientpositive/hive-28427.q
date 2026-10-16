create table tab1 (id string, col string) stored as orc;
select * from tab1 a join tab1 b on a.id = b.id
where lower(a.col) not in ('a', 'b') or lower(a.col) is null;
