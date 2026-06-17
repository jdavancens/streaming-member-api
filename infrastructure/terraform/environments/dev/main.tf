terraform {
  required_version = ">= 1.9"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.0" }
    random = { source = "hashicorp/random", version = "~> 3.0" }
  }

  backend "s3" {
    bucket         = "streaming-member-api-tfstate-dev"
    key            = "dev/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "streaming-member-api-tfstate-lock"
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

locals {
  name       = "member-api-dev"
  account_id = data.aws_caller_identity.current.account_id
  dns_ns     = "${local.name}.local"

  subgraph_services = [
    "member-service", "billing-service", "profile-service",
    "entitlement-service", "discovery-service"
  ]
  all_services = concat(["router"], local.subgraph_services)
}

# ── Networking ───────────────────────────────────────────────────────────────

module "vpc" {
  source          = "../../modules/vpc"
  name            = local.name
  cidr            = "10.0.0.0/16"
  azs             = ["${var.aws_region}a", "${var.aws_region}b"]
  public_subnets  = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnets = ["10.0.10.0/24", "10.0.11.0/24"]
}

module "alb" {
  source         = "../../modules/alb"
  name           = local.name
  vpc_id         = module.vpc.vpc_id
  public_subnets = module.vpc.public_subnets
}

# ── Data Stores ──────────────────────────────────────────────────────────────

module "ecr" {
  source   = "../../modules/ecr"
  name     = local.name
  services = local.all_services
}

module "rds" {
  source     = "../../modules/rds"
  name       = local.name
  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets
}

module "msk" {
  source     = "../../modules/msk"
  name       = local.name
  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets
}

module "redis" {
  source     = "../../modules/redis"
  name       = local.name
  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets
}

module "keyspaces" {
  source     = "../../modules/keyspaces"
  name       = local.name
  region     = var.aws_region
  account_id = local.account_id
  keyspaces  = ["member_api", "profile_api"]
}

# ── Secrets (generated, stored in Secrets Manager — never in git) ─────────────

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name = "${local.name}/jwt/secret"
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = random_password.jwt_secret.result
}

# ── IAM ──────────────────────────────────────────────────────────────────────

module "iam" {
  source      = "../../modules/iam"
  name        = local.name
  region      = var.aws_region
  github_repo = "josephdavancens/streaming-member-api"
}

# ── ECS ──────────────────────────────────────────────────────────────────────

module "ecs" {
  source          = "../../modules/ecs"
  cluster_name    = local.name
  region          = var.aws_region
  vpc_id          = module.vpc.vpc_id
  private_subnets = module.vpc.private_subnets
  ecr_base_url    = "${local.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${local.name}"
  router_tg_arn   = module.alb.router_tg_arn
  alb_sg_id       = module.alb.alb_sg_id

  extra_task_policies = [module.keyspaces.keyspaces_policy_arn]

  services = {
    router = {
      port = 4000
      environment = []
      secrets     = []
    }

    member-service = {
      port = 8080
      environment = [
        { name = "SPRING_DATA_CASSANDRA_CONTACT_POINTS",    value = module.keyspaces.contact_point },
        { name = "SPRING_DATA_CASSANDRA_PORT",              value = tostring(module.keyspaces.port) },
        { name = "SPRING_DATA_CASSANDRA_LOCAL_DATACENTER",  value = var.aws_region },
        { name = "SPRING_DATA_CASSANDRA_KEYSPACE_NAME",     value = "member_api" },
        { name = "CASSANDRA_KEYSPACES_ENABLED",             value = "true" },
        { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS",          value = module.msk.bootstrap_brokers },
      ]
      secrets = [
        { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
      ]
    }

    billing-service = {
      port = 8080
      environment = [
        { name = "SPRING_DATASOURCE_URL",          value = "jdbc:mysql://${module.rds.endpoint}/billing_db" },
        { name = "SPRING_DATASOURCE_USERNAME",     value = "billing" },
        { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = module.msk.bootstrap_brokers },
      ]
      secrets = [
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = module.rds.password_secret_arn },
      ]
    }

    profile-service = {
      port = 8080
      environment = [
        { name = "SPRING_DATA_CASSANDRA_CONTACT_POINTS",    value = module.keyspaces.contact_point },
        { name = "SPRING_DATA_CASSANDRA_PORT",              value = tostring(module.keyspaces.port) },
        { name = "SPRING_DATA_CASSANDRA_LOCAL_DATACENTER",  value = var.aws_region },
        { name = "SPRING_DATA_CASSANDRA_KEYSPACE_NAME",     value = "profile_api" },
        { name = "CASSANDRA_KEYSPACES_ENABLED",             value = "true" },
        { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS",          value = module.msk.bootstrap_brokers },
      ]
      secrets = []
    }

    entitlement-service = {
      port = 8080
      environment = [
        { name = "SPRING_DATA_REDIS_HOST",         value = module.redis.primary_endpoint },
        { name = "SPRING_DATA_REDIS_PORT",         value = "6379" },
        { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = module.msk.bootstrap_brokers },
      ]
      secrets = []
    }

    discovery-service = {
      port = 8080
      environment = [
        { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = module.msk.bootstrap_brokers },
      ]
      secrets = []
    }
  }
}
