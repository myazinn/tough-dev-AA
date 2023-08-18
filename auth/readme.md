This is an anti-corruption layer for the authentication service.
It is responsible for translating the authentication service's data model into the domain model of the application.
E.g. on update keycloak sends only updated fields, but the application expects a full user object.
