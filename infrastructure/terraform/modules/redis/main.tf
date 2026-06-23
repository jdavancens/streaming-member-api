resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.name}-redis-subnet-group"
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "redis" {
  name   = "${var.name}-redis-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id       = "${var.name}-redis"
  description                = "Redis for entitlement stream slots"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.node_type
  num_cache_clusters         = 1
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = false

  tags = { Name = "${var.name}-redis" }
}
