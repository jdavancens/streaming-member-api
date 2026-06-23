resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.name}-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type  = var.instance_type
    client_subnets = var.subnet_ids
    storage_info {
      ebs_storage_info { volume_size = 20 }
    }
    security_groups = [aws_security_group.msk.id]
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"
      in_cluster    = true
    }
  }

  tags = { Name = "${var.name}-kafka" }
}

resource "aws_security_group" "msk" {
  name   = "${var.name}-msk-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port   = 9092
    to_port     = 9092
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }
}
