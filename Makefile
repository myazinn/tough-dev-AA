.PHONY: buildKeycloak

buildKeycloak:
	sbt keycloak-to-kafka/assembly
	cp keycloak-to-kafka/target/scala-3.3.0/keycloak-to-kafka-assembly-0.1.0.jar infra/keycloak-to-kafka
	docker build -t keycloak-kafka infra/keycloak-to-kafka
