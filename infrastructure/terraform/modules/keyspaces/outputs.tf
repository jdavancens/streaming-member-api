output "contact_point" { value = "cassandra.${var.region}.amazonaws.com" }
output "port" { value = 9142 }
output "keyspaces_policy_arn" { value = aws_iam_policy.keyspaces_access.arn }
