variable "project_name" {
  description = "Branch-scoped project name used as the resource prefix."
  type        = string
}

variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "ssh_public_key" {
  description = "Public half of the platform-managed SSH key pair."
  type        = string
}

variable "db_password" {
  description = "Master password for the RDS PostgreSQL instance."
  type        = string
  sensitive   = true
}

variable "vpc_cidr" {
  description = "CIDR block for the project VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for the public subnets (RDS requires at least two AZs)."
  type        = list(string)
  default     = ["10.20.1.0/24", "10.20.2.0/24"]
}

variable "instance_type" {
  description = "EC2 instance type for the application server."
  type        = string
  default     = "t3.small"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage for the RDS instance in GiB."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "employeedb"
}

variable "db_username" {
  description = "PostgreSQL master username."
  type        = string
  default     = "employeeadmin"
}

variable "app_port" {
  description = "Port the application container listens on inside the instance."
  type        = number
  default     = 8080
}

variable "ssh_ingress_cidr" {
  description = "CIDR allowed to reach the instance over SSH (CI runners use dynamic addresses)."
  type        = string
  default     = "0.0.0.0/0"
}

variable "cpu_alarm_threshold" {
  description = "Average CPU percentage that triggers the CloudWatch alarm."
  type        = number
  default     = 80
}

variable "log_retention_days" {
  description = "Retention for the application CloudWatch log group."
  type        = number
  default     = 14
}
