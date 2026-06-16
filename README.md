# Streaming Member API

A Consumer Edge GraphQL supergraph that models the member management layer of a streaming platform. Five Spring Boot subgraphs are composed into a unified API by Apollo Router using Federation v2. Development is schema-first: GraphQL SDL files are the source of truth, and TypeSpec covers the REST auth and async event contracts.

## Architecture

```
Client (iOS / Android / TV / Web)
  └─ Apollo Router :4000          ← Consumer Edge (supergraph)
      ├─ member-service      :8081 → Cassandra   (auth, member CRUD)
      ├─ billing-service     :8082 → MySQL       (plans, subscriptions)
      ├─ profile-service     :8083 → Cassandra   (up to 5 profiles per member)
      ├─ entitlement-service :8084 → Redis       (concurrent stream enforcement)
      └─ discovery-service   :8085               (SDUI home/browse screens)
                    ↕
           Kafka  (member.events · subscription.events)
```

Two API paradigms are implemented:

- **Client-driven** — standard GraphQL queries; clients declare what fields they need
- **Server-Driven UI (SDUI)** — `homeScreen` / `browseScreen` queries return a `DiscoveryComponent` union; the client renders whatever component tree the server returns

## Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose
- Node.js 20+ (for TypeSpec spec validation)

## First-time Setup

**1. Install Rover CLI** (Apollo's schema composition tool):
```bash
curl -sSL https://rover.apollo.dev/nix/latest | sh
```
Rover installs to `~/.rover/bin/`. The Makefile adds this to PATH automatically, so no shell config change is needed.

**2. Pull Docker images** — verify all required images are available before first run:
```bash
docker pull apache/kafka:3.7.1
docker pull cassandra:5.0
docker pull mysql:8.4
docker pull redis:7.4-alpine
docker pull ghcr.io/apollographql/router:v1.55.0
```

**3. Install TypeSpec tooling** (for spec validation only):
```bash
cd specs && npm ci && cd ..
```

## Quick Start

```bash
# Compose the supergraph schema from SDL files in schema/
make compose               # writes services/router/supergraph.graphql

# Build all service images and start everything
docker-compose up --build

# Router is now at http://localhost:4000
# GraphiQL sandbox at http://localhost:4000/
```

## Spec-Driven Workflow

**SDL files in `schema/` are the contract.** Never edit schema files inside a service directory — those are generated from `schema/` at build time via Maven Resources Plugin.

To add or change an endpoint:

```bash
# 1. Edit the SDL
vim schema/member/member.graphqls

# 2. Validate the change composes cleanly
make compose

# 3. Build the affected service (picks up the updated schema)
mvn clean package -pl services/member-service -am -DskipTests

# 4. Run tests
mvn test -pl services/member-service
```

TypeSpec specs (REST auth + Kafka events) live in `specs/`:

```bash
make spec-check    # tsp compile --no-emit
```

## Example Queries

**Client-driven — fetch member dashboard in one round trip:**
```graphql
query MemberDashboard($memberId: ID!) {
  member(id: $memberId) {
    email
    subscription { plan { name maxStreams } status periodEnd }
    profiles { name avatarUrl isKids }
    canStream { allowed reason concurrentStreams maxStreams }
  }
}
```

**SDUI — server controls what the home screen renders:**
```graphql
query HomeScreen($memberId: ID!, $context: ScreenContext!) {
  homeScreen(memberId: $memberId, context: $context) {
    version
    components {
      ... on HeroComponent    { title backgroundImageUrl ctaLabel }
      ... on RowComponent     { label items { title thumbnailUrl type } }
      ... on BillboardComponent { headline subtext }
      ... on TabComponent     { tabs { label selected } }
    }
  }
}
```

**Acquire a stream slot (enforces concurrent limit by plan tier):**
```graphql
mutation {
  acquireStream(memberId: "uuid", deviceId: "device-abc") {
    streamId
    expiresAt
  }
}
```

## Schema Governance

All SDL changes require an entry in `SCHEMA_CHANGELOG.md` before merging. Breaking changes are caught on PRs by `schema-check.yml` (rover composition).

Custom lifecycle directives are defined in `schema/member/directives.graphqls`:
- `@experimental` — field may change without notice
- `@sunset(date:, reason:)` — field is scheduled for removal

## Deployment

**Provision AWS infrastructure (Terraform):**
```bash
cd infrastructure/terraform/environments/dev
cp example.tfvars terraform.tfvars
# Edit terraform.tfvars with your AWS account ID
terraform init && terraform plan && terraform apply
```

**GitHub Actions** deploys automatically on push to `main`. Required secrets:

| Secret | Description |
|---|---|
| `AWS_OIDC_ROLE_ARN` | Output from `terraform apply` → `github_actions_role_arn` |
| `AWS_ACCOUNT_ID` | Your AWS account number |
| `APOLLO_KEY` | Apollo GraphOS key (optional — for schema registry) |

Each service has its own path-scoped workflow so only changed services rebuild.
