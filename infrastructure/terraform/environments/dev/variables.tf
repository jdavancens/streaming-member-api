variable "aws_region" {
  type    = string
  default = "us-east-1"
}
variable "aws_account_id" { type = string }
variable "mysql_password" {
  type      = string
  sensitive = true
  description = "RDS billing DB master password (matches scripts/aws-setup.sh)"
}
