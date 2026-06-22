export PATH := $(HOME)/.rover/bin:$(PATH)

.PHONY: compose dev build test clean aws-setup sandbox sandbox-build

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
	docker-compose up --build

aws-setup:
	bash scripts/aws-setup.sh

build:
	for svc in member billing profile entitlement discovery; do \
	  cd services/$$svc-service && npm ci && npm run build && cd ../..; \
	done

test:
	for svc in member billing profile entitlement discovery; do \
	  cd services/$$svc-service && npm test && cd ../..; \
	done

clean:
	docker-compose down -v
	for svc in member billing profile entitlement discovery; do \
	  rm -rf services/$$svc-service/dist services/$$svc-service/node_modules; \
	done

sandbox-build:
	docker build -f Dockerfile.sandbox -t streaming-member-api-sandbox .

sandbox:
	./scripts/ai-task
