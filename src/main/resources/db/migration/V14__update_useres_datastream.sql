
DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'sparenaproxy-db-instance')
        THEN
            alter user "sparenaproxy-db-instance" with replication;
        END IF;
    END
$$;
DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'datastream-sparenaproxy-user')
        THEN
            alter user "datastream-sparenaproxy-user" with replication;
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "datastream-sparenaproxy-user";
            GRANT USAGE ON SCHEMA public TO "datastream-sparenaproxy-user";
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "datastream-sparenaproxy-user";
        END IF;
    END
$$;
