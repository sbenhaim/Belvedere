-- install vector extension at custom path
create extension vector;
alter extension vector update;

create table if not exists public.doc
(
    collection varchar not null,
    contents   text    not null,
    embedding  vector  not null,
    meta       json,
    primary key (collection, contents)
);

alter table public.doc
    owner to postgres;

drop function search;

create or replace function search(col character varying, emb vector, k integer)
    returns table (contents text, similarity double precision)
    language plpgsql
as
$$
BEGIN
    RETURN QUERY
        SELECT
              doc.contents
            , 1 - (doc.embedding <=> emb) as similarity
        FROM doc
        where collection = col
        ORDER BY doc.embedding <=> emb
        LIMIT k;
END;
$$;

alter function search(varchar, vector, integer) owner to postgres;

insert into doc (collection, contents, embedding)
values ('test', 'test', '[4, 4, 5]')
on conflict (collection, contents) do update set embedding = excluded.embedding;

select distinct collection from doc