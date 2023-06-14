drop table if exists public.doc;

create table public.doc (
  collection character varying not null,
  contents text not null,
  embedding vector not null,
  meta json,
  constraint doc_pkey primary key (collection, contents)
);
