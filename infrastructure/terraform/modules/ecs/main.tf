resource "aws_ecs_cluster" "main" {
  name = var.cluster_name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# ── Service Discovery ───────────────────────────────────────────────────────

resource "aws_service_discovery_private_dns_namespace" "main" {
  name = "${var.cluster_name}.local"
  vpc  = var.vpc_id
}

resource "aws_service_discovery_service" "svc" {
  for_each = var.services
  name     = each.key

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.main.id
    routing_policy = "MULTIVALUE"
    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config { failure_threshold = 1 }
}

# ── IAM ─────────────────────────────────────────────────────────────────────

resource "aws_iam_role" "task_execution" {
  name = "${var.cluster_name}-task-execution-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Action = "sts:AssumeRole", Effect = "Allow",
    Principal = { Service = "ecs-tasks.amazonaws.com" } }]
  })
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "secrets_read" {
  name = "secrets-read"
  role = aws_iam_role.task_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = "arn:aws:secretsmanager:${var.region}:*:secret:${var.cluster_name}/*"
    }]
  })
}

resource "aws_iam_role" "task" {
  name = "${var.cluster_name}-task-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Action = "sts:AssumeRole", Effect = "Allow",
    Principal = { Service = "ecs-tasks.amazonaws.com" } }]
  })
}

resource "aws_iam_role_policy_attachment" "task_extra" {
  count      = length(var.extra_task_policies)
  role       = aws_iam_role.task.name
  policy_arn = var.extra_task_policies[count.index]
}

# ── Security Group ───────────────────────────────────────────────────────────

resource "aws_security_group" "services" {
  name   = "${var.cluster_name}-services-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  # Allow ALB to reach router on 4000 and 8088
  ingress {
    from_port       = 4000
    to_port         = 4000
    protocol        = "tcp"
    security_groups = [var.alb_sg_id]
  }

  ingress {
    from_port       = 8088
    to_port         = 8088
    protocol        = "tcp"
    security_groups = [var.alb_sg_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ── CloudWatch Logs ──────────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "svc" {
  for_each          = var.services
  name              = "/ecs/${var.cluster_name}/${each.key}"
  retention_in_days = 7
}

# ── Task Definitions ─────────────────────────────────────────────────────────

resource "aws_ecs_task_definition" "svc" {
  for_each                 = var.services
  family                   = "${var.cluster_name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name      = each.key
    image     = "${var.ecr_base_url}/${each.key}:latest"
    essential = true
    portMappings = [
      { containerPort = each.value.port, hostPort = each.value.port }
    ]
    environment = each.value.environment
    secrets     = each.value.secrets
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = "/ecs/${var.cluster_name}/${each.key}"
        awslogs-region        = var.region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])
}

# ── ECS Services ─────────────────────────────────────────────────────────────

resource "aws_ecs_service" "svc" {
  for_each        = var.services
  name            = each.key
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.svc[each.key].arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnets
    security_groups  = [aws_security_group.services.id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = aws_service_discovery_service.svc[each.key].arn
  }

  dynamic "load_balancer" {
    for_each = each.key == "router" ? [1] : []
    content {
      target_group_arn = var.router_tg_arn
      container_name   = "router"
      container_port   = 4000
    }
  }

  lifecycle {
    ignore_changes = [task_definition]
  }
}
