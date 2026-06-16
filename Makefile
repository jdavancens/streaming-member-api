export PATH := $(HOME)/.rover/bin:$(PATH)

.PHONY: compose dev build test clean

compose:
	rover supergraph compose --config supergraph.yaml --elv2-license=accept > services/router/supergraph.graphql

schema-check:
	rover subgraph check --name member   --schema schema/member/member.graphqls
	rover subgraph check --name billing  --schema schema/billing/billing.graphqls
	rover subgraph check --name profile  --schema schema/profile/profile.graphqls
	rover subgraph check --name entitlement --schema schema/entitlement/entitlement.graphqls
	rover subgraph check --name discovery --schema schema/discovery/discovery.graphqls

spec-check:
	cd specs && npx tsp compile . --no-emit

dev: compose
	docker-compose up

build:
	mvn clean package -DskipTests

test:
	mvn test

clean:
	mvn clean
	docker-compose down -v
