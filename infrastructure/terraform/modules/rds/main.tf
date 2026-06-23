resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-rds-subnet-group"
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "rds" {
  name   = "${var.name}-rds-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }
}

resource "random_password" "db_password" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "db_password" {
  name = "${var.name}/rds/password"
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

resource "aws_db_instance" "main" {
  identifier             = "${var.name}-mysql"
  engine                 = "mysql"
  engine_version         = "8.0"
  instance_class         = var.instance_class
  allocated_storage      = 20
  db_name                = var.database_name
  username               = var.username
  password               = random_password.db_password.result
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  skip_final_snapshot    = var.skip_final_snapshot
  multi_az               = var.multi_az
  storage_encrypted      = true

  tags = { Name = "${var.name}-mysql" }
}
