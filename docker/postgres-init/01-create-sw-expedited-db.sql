-- Only runs when the postgres data volume is initialized for the first time
-- (docker-entrypoint-initdb.d scripts are skipped on an existing volume).
-- auth-lava's own database (auth_db) is created via POSTGRES_DB and doesn't need this.
CREATE DATABASE sw_expedited_db;
