DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_user where usename = 'sparenajob')
        THEN
            GRANT SELECT ON ALL TABLES IN SCHEMA PUBLIC TO "sparenajob";
END IF;
END
$$;