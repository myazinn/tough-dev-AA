.PHONY: build run

build:
	sbt keycloak-to-kafka/assembly task-tracker/Docker/publishLocal auth/Docker/publishLocal accounting/Docker/publishLocal

run: build
	docker-compose up -d
