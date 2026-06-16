terraform {
  required_version = ">= 1.9"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.0" }
    random = { source = "hashicorp/random", version = "~> 3.0" }
  }

  backend "s3" {
    bucket = "streaming-member-api-tfstate-dev"
    key    = "dev/terraform.tfstate"
    region = var.aws_region
  }
}

provider "aws" {
  region = var.aws_region
}

locals {
  name     = "member-api-dev"
  services = ["router", "member-service", "billing-service",
               "profile-service", "entitlement-service", "discovery-service"]
}

module "vpc" {
  source          = "../../modules/vpc"
  name            = local.name
  cidr            = "10.0.0.0/16"
  azs             = ["${var.aws_region}a", "${var.aws_region}b"]
  public_subnets  = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnets = ["10.0.10.0/24", "10.0.11.0/24"]
}

module "ecr" {
  source   = "../../modules/ecr"
  name     = local.name
  services = local.services
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

module "ecs" {
  source          = "../../modules/ecs"
  cluster_name    = local.name
  region          = var.aws_region
  vpc_id          = module.vpc.vpc_id
  private_subnets = module.vpc.private_subnets
  services        = local.services
  ecr_base_url    = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${local.name}"
}

module "iam" {
  source      = "../../modules/iam"
  name        = local.name
  region      = var.aws_region
  github_repo = "josephdavancens/streaming-member-api"
}
