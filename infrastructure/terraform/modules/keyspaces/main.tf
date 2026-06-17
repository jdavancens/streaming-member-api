resource "aws_keyspaces_keyspace" "ks" {
  for_each = toset(var.keyspaces)
  name     = each.key
  tags     = { Name = each.key }
}

resource "aws_iam_policy" "keyspaces_access" {
  name = "${var.name}-keyspaces-access"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cassandra:Select", "cassandra:Modify", "cassandra:Alter", "cassandra:Create", "cassandra:Drop"]
      Resource = "arn:aws:cassandra:${var.region}:${var.account_id}:/keyspace/*/table/*"
    }]
  })
}
