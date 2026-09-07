output "public_ip" {
  description = "Elastic IP address serving the application through Nginx."
  value       = aws_eip.app.public_ip
}

output "instance_id" {
  description = "EC2 instance id running the application container."
  value       = aws_instance.app.id
}

output "vpc_id" {
  description = "Identifier of the project VPC."
  value       = aws_vpc.main.id
}

output "app_security_group_id" {
  description = "Security group attached to the application instance."
  value       = aws_security_group.app.id
}

output "db_security_group_id" {
  description = "Security group attached to the RDS instance."
  value       = aws_security_group.db.id
}

output "db_endpoint" {
  description = "RDS PostgreSQL endpoint in host:port form."
  value       = aws_db_instance.main.endpoint
}

output "db_host" {
  description = "RDS PostgreSQL hostname."
  value       = aws_db_instance.main.address
}

output "db_name" {
  description = "Initial database name."
  value       = aws_db_instance.main.db_name
}

output "db_username" {
  description = "PostgreSQL master username."
  value       = aws_db_instance.main.username
}

output "log_group_name" {
  description = "CloudWatch log group receiving application logs."
  value       = aws_cloudwatch_log_group.app.name
}

output "application_url" {
  description = "Base URL of the deployed application."
  value       = "http://${aws_eip.app.public_ip}"
}
