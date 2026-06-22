# Rewrite: Spring Boot → TypeScript/Node.js

## Context

This is a portfolio project for a Netflix SE5 job application. The architecture (Apollo Federation v2, 5 subgraphs, GraphQL schemas, TypeSpec specs, Terraform infra) is correct and stays unchanged. Two things change:

1. **Language**: Java 21 / Spring Boot 3.3.4 / Netflix DGS → TypeScript / Node.js / Apollo Server 4
2. **Data stores**: Cassandra + Redis + Kafka → DynamoDB + SQS/SNS (real AWS dev account) + MySQL Docker

The owner's primary production stack is TypeScript/Node.js with DynamoDB + SQS/SNS (4 years at Nike). This makes the demo authentic to actual production depth rather than a stack stood up to look impressive.

---

## Data Store Decisions

| Service | Before | After | Why |
|---|---|---|---|
| member + profile | Cassandra | DynamoDB (real AWS) | Owner's actual Nike production experience |
| entitlement stream slots | Redis TTL | DynamoDB TTL (real AWS) | Owner built TTL workflows at Nike (EU consent TTL purge) |
| billing | MySQL | RDS MySQL (real AWS, Free Tier) | Relational model fits billing; consistent with all-AWS infra story |
| async events | Kafka | SQS/SNS (real AWS) | Owner's actual Nike production experience |

Services connect to a real AWS dev account. Run `make aws-setup` once to provision tables/queues/topics.

---

## What Stays Unchanged (do not touch)

- `schema/` — all `.graphqls` files (Apollo Federation v2.6 SDL source of truth)
- `specs/` — TypeSpec auth + Kafka/event specs (rename Kafka references to SQS in comments only if desired)
- `services/router/` — Apollo Router (pre-built Rust binary)
- `supergraph.yaml` / `supergraph-prod.yaml`
- `SCHEMA_CHANGELOG.md`
- `bruno/` — API testing collection

---

## What Changes

### Deleted
- `pom.xml` (root Maven POM)
- `services/*/pom.xml` and all `src/main/java/` source under each service

### New per service (5 services)
```
services/{name}-service/
├── package.json
├── tsconfig.json
├── Dockerfile
└── src/
    ├── index.ts
    ├── schema.ts
    ├── resolvers/
    │   └── index.ts
    └── datasources/
        └── {client}.ts
```

### Updated
- `docker-compose.yml` — update service build contexts, remove Cassandra/Redis/Kafka
- `Makefile` — replace `mvn` targets with `npm`; add `aws-setup` target
- `.github/workflows/_deploy-service.yml` — replace Maven build with npm build
- `infrastructure/terraform/` — swap Keyspaces + ElastiCache + MSK modules for DynamoDB + SQS + SNS

---

## Tech Stack Per Service

| Service | Port | Data | Messaging |
|---|---|---|---|
| member-service | 8081 | DynamoDB (`members` table) | SQS/SNS producer (`member.events`) |
| billing-service | 8082 | MySQL (`plans`, `subscriptions`) | SQS/SNS producer (`subscription.events`) |
| profile-service | 8083 | DynamoDB (`profiles` table) | SQS/SNS consumer (`member.events`) |
| entitlement-service | 8084 | DynamoDB (`stream_slots` table, TTL) | SQS/SNS consumer (`subscription.events`) |
| discovery-service | 8085 | none (stateless) | — |

**Common deps (all services):**
```json
{
  "@apollo/server": "^4",
  "@apollo/subgraph": "^2",
  "graphql": "^16",
  "express": "^4",
  "body-parser": "^1",
  "cors": "^2",
  "@aws-sdk/client-dynamodb": "^3",
  "@aws-sdk/lib-dynamodb": "^3",
  "typescript": "^5",
  "ts-node": "^10",
  "@types/express": "^4",
  "@types/node": "^20"
}
```

**member-service extras:** `jsonwebtoken`, `bcryptjs`, `@aws-sdk/client-sns`, `@aws-sdk/client-sqs`  
**billing-service extras:** `mysql2`, `@aws-sdk/client-sns`  
**entitlement-service extras:** `@aws-sdk/client-sqs` (consumer)  
**profile-service extras:** `@aws-sdk/client-sqs` (consumer)

---

## AWS Dev Account Setup

No LocalStack. Services connect to real AWS using credentials from `~/.aws/credentials` or environment variables. A one-time `make aws-setup` script provisions the dev resources.

Remove from docker-compose: `cassandra`, `kafka`, `redis`, `mysql` service blocks.  
Keep: all 5 app services, `router`. All data stores are now in AWS.

### `scripts/dev-resources.yaml` (CloudFormation template)

Declarative, idempotent. Deploy with `make aws-setup`.

```yaml
AWSTemplateFormatVersion: "2010-09-09"
Description: streaming-member-api dev resources (DynamoDB + SQS + SNS)

Resources:

  MembersTable:
    Type: AWS::DynamoDB::Table
    Properties:
      TableName: members
      BillingMode: PAY_PER_REQUEST
      AttributeDefinitions:
        - AttributeName: id
          AttributeType: S
      KeySchema:
        - AttributeName: id
          KeyType: HASH

  ProfilesTable:
    Type: AWS::DynamoDB::Table
    Properties:
      TableName: profiles
      BillingMode: PAY_PER_REQUEST
      AttributeDefinitions:
        - AttributeName: memberId
          AttributeType: S
        - AttributeName: id
          AttributeType: S
      KeySchema:
        - AttributeName: memberId
          KeyType: HASH
        - AttributeName: id
          KeyType: RANGE

  StreamSlotsTable:
    Type: AWS::DynamoDB::Table
    Properties:
      TableName: stream_slots
      BillingMode: PAY_PER_REQUEST
      AttributeDefinitions:
        - AttributeName: streamId
          AttributeType: S
        - AttributeName: memberId
          AttributeType: S
      KeySchema:
        - AttributeName: streamId
          KeyType: HASH
      GlobalSecondaryIndexes:
        - IndexName: memberId-index
          KeySchema:
            - AttributeName: memberId
              KeyType: HASH
          Projection:
            ProjectionType: ALL
      TimeToLiveSpecification:
        AttributeName: expiresAt
        Enabled: true

  MemberEventsTopic:
    Type: AWS::SNS::Topic
    Properties:
      TopicName: member-events

  SubscriptionEventsTopic:
    Type: AWS::SNS::Topic
    Properties:
      TopicName: subscription-events

  ProfileMemberEventsQueue:
    Type: AWS::SQS::Queue
    Properties:
      QueueName: profile-member-events

  EntitlementSubscriptionEventsQueue:
    Type: AWS::SQS::Queue
    Properties:
      QueueName: entitlement-subscription-events

  ProfileQueuePolicy:
    Type: AWS::SQS::QueuePolicy
    Properties:
      Queues: [!Ref ProfileMemberEventsQueue]
      PolicyDocument:
        Statement:
          - Effect: Allow
            Principal: {Service: sns.amazonaws.com}
            Action: sqs:SendMessage
            Resource: !GetAtt ProfileMemberEventsQueue.Arn
            Condition:
              ArnEquals:
                aws:SourceArn: !Ref MemberEventsTopic

  EntitlementQueuePolicy:
    Type: AWS::SQS::QueuePolicy
    Properties:
      Queues: [!Ref EntitlementSubscriptionEventsQueue]
      PolicyDocument:
        Statement:
          - Effect: Allow
            Principal: {Service: sns.amazonaws.com}
            Action: sqs:SendMessage
            Resource: !GetAtt EntitlementSubscriptionEventsQueue.Arn
            Condition:
              ArnEquals:
                aws:SourceArn: !Ref SubscriptionEventsTopic

  ProfileSubscription:
    Type: AWS::SNS::Subscription
    Properties:
      TopicArn: !Ref MemberEventsTopic
      Protocol: sqs
      Endpoint: !GetAtt ProfileMemberEventsQueue.Arn

  EntitlementSubscription:
    Type: AWS::SNS::Subscription
    Properties:
      TopicArn: !Ref SubscriptionEventsTopic
      Protocol: sqs
      Endpoint: !GetAtt EntitlementSubscriptionEventsQueue.Arn

  # RDS MySQL (Free Tier eligible: db.t3.micro, 20GB gp2)
  BillingDBSubnetGroup:
    Type: AWS::RDS::DBSubnetGroup
    Properties:
      DBSubnetGroupDescription: Billing RDS subnet group
      SubnetIds: !Ref SubnetIds  # parameter — pass your default VPC subnet IDs

  BillingDBSecurityGroup:
    Type: AWS::EC2::SecurityGroup
    Properties:
      GroupDescription: Allow MySQL access from dev machine
      VpcId: !Ref VpcId  # parameter
      SecurityGroupIngress:
        - IpProtocol: tcp
          FromPort: 3306
          ToPort: 3306
          CidrIp: !Ref DevMachineIp  # parameter — your public IP e.g. "1.2.3.4/32"

  BillingDB:
    Type: AWS::RDS::DBInstance
    Properties:
      DBInstanceIdentifier: billing-dev
      DBInstanceClass: db.t3.micro
      Engine: mysql
      EngineVersion: "8.4"
      MasterUsername: billing
      MasterUserPassword: !Ref DBPassword  # parameter — pass via --parameter-overrides
      DBName: billing
      AllocatedStorage: 20
      StorageType: gp2
      PubliclyAccessible: true   # dev only — lock down in prod
      DBSubnetGroupName: !Ref BillingDBSubnetGroup
      VPCSecurityGroups: [!GetAtt BillingDBSecurityGroup.GroupId]
      DeletionProtection: false

Parameters:
  VpcId:
    Type: AWS::EC2::VPC::Id
    Description: Default VPC ID
  SubnetIds:
    Type: List<AWS::EC2::Subnet::Id>
    Description: Subnet IDs (at least 2 AZs for subnet group)
  DevMachineIp:
    Type: String
    Description: Your public IP in CIDR notation (e.g. 1.2.3.4/32)
  DBPassword:
    Type: String
    NoEcho: true
    Description: RDS master password

Outputs:
  MemberEventsTopicArn:
    Value: !Ref MemberEventsTopic
  SubscriptionEventsTopicArn:
    Value: !Ref SubscriptionEventsTopic
  ProfileMemberEventsQueueUrl:
    Value: !Ref ProfileMemberEventsQueue
  EntitlementSubscriptionEventsQueueUrl:
    Value: !Ref EntitlementSubscriptionEventsQueue
  BillingDBEndpoint:
    Value: !GetAtt BillingDB.Endpoint.Address
```

### `scripts/aws-setup.sh`
```bash
#!/bin/bash
set -e
REGION=${AWS_REGION:-us-east-1}
STACK=streaming-member-api-dev

# Resolve default VPC and subnets automatically
VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text --region $REGION)
SUBNET_IDS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID \
  --query 'Subnets[*].SubnetId' --output text --region $REGION | tr '\t' ',')
DEV_IP=$(curl -s https://checkip.amazonaws.com)/32

echo "VPC: $VPC_ID"
echo "Subnets: $SUBNET_IDS"
echo "Dev IP: $DEV_IP"

read -s -p "RDS master password: " DB_PASSWORD
echo

aws cloudformation deploy \
  --template-file scripts/dev-resources.yaml \
  --stack-name $STACK \
  --region $REGION \
  --parameter-overrides \
    VpcId=$VPC_ID \
    SubnetIds=$SUBNET_IDS \
    DevMachineIp=$DEV_IP \
    DBPassword=$DB_PASSWORD

echo "Outputs (add to .env):"
aws cloudformation describe-stacks \
  --stack-name $STACK \
  --region $REGION \
  --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
  --output table
```

---

## AWS SDK Client Pattern (shared across services)

No endpoint override needed — SDK picks up credentials from `~/.aws/credentials`, env vars, or IAM role in prod automatically.

### `src/datasources/dynamodb.ts`
```typescript
import { DynamoDBClient } from '@aws-sdk/client-dynamodb';
import { DynamoDBDocumentClient } from '@aws-sdk/lib-dynamodb';

export const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION ?? 'us-east-1' })
);
```

### `src/datasources/sns.ts`
```typescript
import { SNSClient, PublishCommand } from '@aws-sdk/client-sns';

const sns = new SNSClient({ region: process.env.AWS_REGION ?? 'us-east-1' });

export async function publish(topicArn: string, message: object) {
  await sns.send(new PublishCommand({
    TopicArn: topicArn,
    Message: JSON.stringify(message),
  }));
}
```

### `src/datasources/sqs.ts` (for consumers)
```typescript
import { SQSClient, ReceiveMessageCommand, DeleteMessageCommand } from '@aws-sdk/client-sqs';

const sqs = new SQSClient({ region: process.env.AWS_REGION ?? 'us-east-1' });

export async function poll(queueUrl: string, handler: (body: object) => Promise<void>) {
  while (true) {
    const res = await sqs.send(new ReceiveMessageCommand({
      QueueUrl: queueUrl,
      MaxNumberOfMessages: 10,
      WaitTimeSeconds: 20, // long polling
    }));
    for (const msg of res.Messages ?? []) {
      try {
        const body = JSON.parse(msg.Body ?? '{}');
        const payload = body.Message ? JSON.parse(body.Message) : body; // unwrap SNS envelope
        await handler(payload);
        await sqs.send(new DeleteMessageCommand({
          QueueUrl: queueUrl,
          ReceiptHandle: msg.ReceiptHandle!,
        }));
      } catch (err) {
        console.error('message processing failed', err);
      }
    }
  }
}
```

---

## Service Environment Variables

Values for ARNs and queue URLs are output by `make aws-setup`. Store them in a `.env` file (gitignored) and reference in docker-compose.

### member-service
```
PORT=8081
AWS_REGION=us-east-1
MEMBER_EVENTS_TOPIC_ARN=<output from aws-setup>
JWT_SECRET=dev-secret-change-in-prod
```

### billing-service
```
PORT=8082
MYSQL_HOST=<BillingDBEndpoint output from aws-setup>
MYSQL_USER=billing
MYSQL_PASSWORD=<your RDS password>
MYSQL_DATABASE=billing
AWS_REGION=us-east-1
SUBSCRIPTION_EVENTS_TOPIC_ARN=<output from aws-setup>
```

### profile-service
```
PORT=8083
AWS_REGION=us-east-1
PROFILE_MEMBER_EVENTS_QUEUE_URL=<output from aws-setup>
```

### entitlement-service
```
PORT=8084
AWS_REGION=us-east-1
ENT_SUBSCRIPTION_EVENTS_QUEUE_URL=<output from aws-setup>
STREAM_SLOT_TTL_SECONDS=14400
```

### discovery-service
```
PORT=8085
```

---

## Resolver Implementation Notes

### member-service
- `Query.member(id)` → DynamoDB `GetCommand` on `members` table
- `Mutation.register(input)` → DynamoDB `PutCommand`, publish `MemberRegistered` to SNS `member-events`, return JWT
- `Member.__resolveReference({ id })` → DynamoDB `GetCommand`

### billing-service
- `Member.subscription` → MySQL SELECT from `subscriptions JOIN plans`
- `Mutation.subscribe` → MySQL INSERT + SNS publish `SubscriptionCreated` to `subscription-events`
- `Mutation.cancelSubscription` / `changePlan` → MySQL UPDATE + SNS publish

### profile-service
- `Member.profiles` → DynamoDB `QueryCommand` on `profiles` (partition key = `memberId`)
- `Mutation.createProfile` / `updateProfile` / `deleteProfile` → DynamoDB writes
- SQS consumer: polls `profile-member-events` → on `MemberCancelled`, soft-delete profiles

### entitlement-service
- `Member.canStream` → DynamoDB `QueryCommand` on `stream_slots` (GSI on `memberId`), compare count vs `maxStreams` from cache
- `Mutation.acquireStream` → DynamoDB `PutCommand` with `expiresAt = now + TTL` (TTL attribute)
- `Mutation.releaseStream` → DynamoDB `DeleteCommand`
- `Mutation.heartbeatStream` → DynamoDB `UpdateCommand` to extend `expiresAt`
- SQS consumer: polls `entitlement-subscription-events` → on `SubscriptionChanged`, update in-memory `maxStreams` cache (or re-derive from billing)

### discovery-service
- Stateless. Return hardcoded but realistic component trees.
- Include one of each union type: `HeroComponent`, `RowComponent`, `BillboardComponent`, `TabComponent`.

---

## Dockerfile Pattern (all services)

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json tsconfig.json ./
RUN npm ci
COPY src/ ./src/

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/src ./src
COPY package.json tsconfig.json ./
# Schema SDL files read at runtime
COPY schema/ ./schema/
RUN npm run build
CMD ["node", "dist/index.js"]
```

docker-compose build context must be `.` (repo root) so `COPY schema/` works.

---

## Makefile Changes

```makefile
dev: compose
	docker-compose up --build

compose:
	~/.rover/bin/rover supergraph compose --config supergraph.yaml > services/router/supergraph.graphql

aws-setup:
	bash scripts/aws-setup.sh   # deploys scripts/dev-resources.yaml CloudFormation stack

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
```

---

## Terraform Updates

Swap out these modules in `infrastructure/terraform/`:
- Remove: `modules/keyspaces/` → Replace with `modules/dynamodb/` (tables: members, profiles, stream_slots with TTL)
- Remove: `modules/msk/` → Replace with `modules/sns/` + `modules/sqs/`
- Remove: `modules/redis/` → No replacement (entitlement now uses DynamoDB)
- Keep: `modules/rds/`, `modules/vpc/`, `modules/alb/`, `modules/ecs/`, `modules/ecr/`, `modules/iam/`

Update `infrastructure/terraform/environments/dev/main.tf` to reference new modules.

---

## GitHub Actions Changes

In `.github/workflows/_deploy-service.yml`:
```yaml
# Remove:
- run: mvn -B clean package -DskipTests

# Add:
- name: Install and build
  run: npm ci && npm run build
  working-directory: services/${{ inputs.service }}-service

# Update Docker build context from service dir to repo root:
- name: Build and push
  uses: docker/build-push-action@v5
  with:
    context: .                                          # repo root
    file: services/${{ inputs.service }}-service/Dockerfile
    push: true
    tags: ${{ steps.meta.outputs.tags }}
```

---

## Execution Order

1. Delete all `pom.xml` files and `src/main/java/` directories
2. Write `scripts/dev-resources.yaml` (CloudFormation) and `scripts/aws-setup.sh`
3. Update `docker-compose.yml` (remove Cassandra/Redis/Kafka, update service build contexts to repo root)
4. Write `package.json` + `tsconfig.json` for all 5 services
5. Write shared datasource clients: `dynamodb.ts`, `sns.ts`, `sqs.ts`, `mysql.ts`
6. Write `src/schema.ts` + `src/index.ts` for all 5 services
7. Write resolvers — start with discovery-service (stateless), then member, billing, profile, entitlement
8. Write `Dockerfile` for each service
9. Update `Makefile`
10. Update `.github/workflows/_deploy-service.yml`
11. Update Terraform modules
12. Run `make dev` — verify full stack at http://localhost:4000

---

## Verification

Run `make dev`, then test these three queries at http://localhost:4000:

```graphql
# 1. Full member dashboard (exercises federation across all 5 subgraphs)
query MemberDashboard($memberId: ID!) {
  member(id: $memberId) {
    email
    subscription { plan { name maxStreams } status periodEnd }
    profiles { name avatarUrl isKids }
    canStream { allowed reason concurrentStreams maxStreams }
  }
}

# 2. SDUI home screen
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

# 3. Acquire stream slot (DynamoDB TTL entitlement)
mutation {
  acquireStream(memberId: "uuid", deviceId: "device-abc") {
    streamId
    expiresAt
  }
}
```

All three should return real data, not errors.
