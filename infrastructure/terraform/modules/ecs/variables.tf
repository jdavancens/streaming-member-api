variable "cluster_name"    { type = string }
variable "region"          { type = string }
variable "vpc_id"          { type = string }
variable "vpc_cidr"        { type = string; default = "10.0.0.0/16" }
variable "private_subnets" { type = list(string) }
variable "ecr_base_url"    { type = string }
variable "router_tg_arn"   { type = string }
variable "alb_sg_id"       { type = string }
variable "task_cpu"        { default = 512 }
variable "task_memory"     { default = 1024 }
variable "desired_count"   { default = 1 }

variable "extra_task_policies" {
  type    = list(string)
  default = []
}

variable "services" {
  type = map(object({
    port        = optional(number, 8080)
    environment = optional(list(object({ name = string, value = string })), [])
    secrets     = optional(list(object({ name = string, valueFrom = string })), [])
  }))
}
