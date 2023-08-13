.PHONY: buildKeycloak

buildKeycloak:
	sbt auth/assembly
	cp auth/target/scala-3.3.0/auth-assembly-0.1.0.jar infra/keycloak-to-kafka
	docker build -t keycloak-kafka infra/keycloak-to-kafka
