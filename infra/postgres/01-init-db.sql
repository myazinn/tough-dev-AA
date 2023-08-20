CREATE DATABASE keycloak;
CREATE USER keycloak WITH ENCRYPTED PASSWORD 'keycloak';
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
ALTER DATABASE keycloak OWNER TO keycloak;

CREATE DATABASE auth;
CREATE USER auth WITH ENCRYPTED PASSWORD 'auth';
GRANT ALL PRIVILEGES ON DATABASE auth TO auth;
ALTER DATABASE auth OWNER TO auth;

CREATE DATABASE tasktracker;
CREATE USER tasktracker WITH ENCRYPTED PASSWORD 'tasktracker';
GRANT ALL PRIVILEGES ON DATABASE tasktracker TO tasktracker;
ALTER DATABASE tasktracker OWNER TO tasktracker;

CREATE DATABASE accounting;
CREATE USER accounting WITH ENCRYPTED PASSWORD 'accounting';
GRANT ALL PRIVILEGES ON DATABASE accounting TO accounting;
ALTER DATABASE accounting OWNER TO accounting;
