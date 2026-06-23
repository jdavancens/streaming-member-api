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

  subgraph_services = [
    "member-service", "billing-service", "profile-service",
    "entitlement-service", "discovery-service"
  ]
  all_services = concat(["router"], local.subgraph_services)
}

# ── CloudFormation outputs (data stores provisioned by scripts/dev-resources.yaml) ──

data "aws_cloudformation_stack" "dev" {
  name = "streaming-member-api-dev"
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

# ── Compute / Container Registry ─────────────────────────────────────────────

module "ecr" {
  source   = "../../modules/ecr"
  name     = local.name
  services = local.all_services
}

# ── Secrets ──────────────────────────────────────────────────────────────────

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

resource "aws_secretsmanager_secret" "mysql_password" {
  name = "${local.name}/mysql/password"
}

resource "aws_secretsmanager_secret_version" "mysql_password" {
  secret_id     = aws_secretsmanager_secret.mysql_password.id
  secret_string = var.mysql_password
}

# ── IAM ──────────────────────────────────────────────────────────────────────

module "iam" {
  source      = "../../modules/iam"
  name        = local.name
  region      = var.aws_region
  github_repo = "jdavancens/streaming-member-api"
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

  extra_task_policies = [module.iam.dynamodb_policy_arn]

  services = {
    router = {
      port        = 4000
      environment = []
      secrets     = []
    }

    member-service = {
      port = 8081
      environment = [
        { name = "PORT",                      value = "8081" },
        { name = "AWS_REGION",               value = var.aws_region },
        { name = "MEMBER_EVENTS_TOPIC_ARN",  value = data.aws_cloudformation_stack.dev.outputs["MemberEventsTopicArn"] },
      ]
      secrets = [
        { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
      ]
    }

    billing-service = {
      port = 8082
      environment = [
        { name = "PORT",                             value = "8082" },
        { name = "AWS_REGION",                      value = var.aws_region },
        { name = "MYSQL_HOST",                      value = data.aws_cloudformation_stack.dev.outputs["BillingDBEndpoint"] },
        { name = "MYSQL_USER",                      value = "billing" },
        { name = "MYSQL_DATABASE",                  value = "billing" },
        { name = "SUBSCRIPTION_EVENTS_TOPIC_ARN",  value = data.aws_cloudformation_stack.dev.outputs["SubscriptionEventsTopicArn"] },
      ]
      secrets = [
        { name = "MYSQL_PASSWORD", valueFrom = aws_secretsmanager_secret.mysql_password.arn },
      ]
    }

    profile-service = {
      port = 8083
      environment = [
        { name = "PORT",                             value = "8083" },
        { name = "AWS_REGION",                      value = var.aws_region },
        { name = "PROFILE_MEMBER_EVENTS_QUEUE_URL", value = data.aws_cloudformation_stack.dev.outputs["ProfileMemberEventsQueueUrl"] },
      ]
      secrets = []
    }

    entitlement-service = {
      port = 8084
      environment = [
        { name = "PORT",                                  value = "8084" },
        { name = "AWS_REGION",                           value = var.aws_region },
        { name = "ENT_SUBSCRIPTION_EVENTS_QUEUE_URL",   value = data.aws_cloudformation_stack.dev.outputs["EntitlementSubscriptionEventsQueueUrl"] },
        { name = "STREAM_SLOT_TTL_SECONDS",              value = "14400" },
      ]
      secrets = []
    }

    discovery-service = {
      port = 8085
      environment = [
        { name = "PORT",       value = "8085" },
        { name = "AWS_REGION", value = var.aws_region },
      ]
      secrets = []
    }
  }
}
