output "alb_dns_name"          { value = module.alb.dns_name }
output "ecr_repository_urls"   { value = module.ecr.repository_urls }
output "github_actions_role_arn" { value = module.iam.github_actions_role_arn }
output "service_dns_namespace" { value = module.ecs.dns_namespace }
