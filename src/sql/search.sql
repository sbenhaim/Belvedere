create or replace function search(col character varying, embedding vector, k integer)
    returns table (text text, similarity double precision)
    language plpgsql
as
$$
BEGIN
    RETURN QUERY
        SELECT
              doc.text
            , 1 - (doc.vector <=> embedding) as similarity
        FROM doc
        where collection = col
        ORDER BY doc.vector <=> embedding
        LIMIT k;
END;
$$;

alter function search(varchar, vector, integer) owner to postgres;
