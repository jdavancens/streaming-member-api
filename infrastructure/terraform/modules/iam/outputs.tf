output "github_actions_role_arn" { value = aws_iam_role.github_actions.arn }
output "dynamodb_policy_arn" { value = aws_iam_policy.dynamodb_access.arn }
