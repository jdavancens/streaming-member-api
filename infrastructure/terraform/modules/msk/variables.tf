variable "name" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "broker_count" { default = 2 }
variable "instance_type" { default = "kafka.t3.small" }
