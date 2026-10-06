DO
$$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname = 'datastream-sparenaproxy-user'
    ) THEN
        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            REVOKE SELECT ON TABLES
            FROM "datastream-sparenaproxy-user";

        REVOKE SELECT ON ALL TABLES IN SCHEMA public
            FROM "datastream-sparenaproxy-user";

        REVOKE USAGE ON SCHEMA public
            FROM "datastream-sparenaproxy-user";

        DROP ROLE "datastream-sparenaproxy-user";
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname = 'sparenaproxy-db-instance'
    ) THEN
        ALTER ROLE "sparenaproxy-db-instance" WITH NOREPLICATION;
    END IF;
END
$$;
