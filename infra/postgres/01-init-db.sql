CREATE DATABASE keycloak;
CREATE USER keycloak WITH ENCRYPTED PASSWORD 'keycloak';
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
ALTER DATABASE keycloak OWNER TO keycloak;

CREATE DATABASE tasktracker;
CREATE USER tasktracker WITH ENCRYPTED PASSWORD 'tasktracker';
GRANT ALL PRIVILEGES ON DATABASE tasktracker TO tasktracker;
ALTER DATABASE tasktracker OWNER TO tasktracker;
