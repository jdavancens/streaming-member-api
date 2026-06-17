output "alb_dns_name"            { value = module.alb.dns_name }
output "ecr_repository_urls"     { value = module.ecr.repository_urls }
output "rds_endpoint"            { value = module.rds.endpoint }
output "kafka_bootstrap_brokers" { value = module.msk.bootstrap_brokers }
output "redis_endpoint"          { value = module.redis.primary_endpoint }
output "github_actions_role_arn" { value = module.iam.github_actions_role_arn }
output "service_dns_namespace"   { value = module.ecs.dns_namespace }
