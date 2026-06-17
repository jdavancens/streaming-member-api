output "dns_name"           { value = aws_lb.main.dns_name }
output "router_tg_arn"      { value = aws_lb_target_group.router.arn }
output "alb_sg_id"          { value = aws_security_group.alb.id }
