.PHONY: build start restart

build:
	sbt keycloak-to-kafka/assembly task-tracker/Docker/publishLocal auth/Docker/publishLocal accounting/Docker/publishLocal

start: build
	docker-compose up -d

restart:
	docker-compose down
	$(MAKE) start
