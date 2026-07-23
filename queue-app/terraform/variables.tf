variable "region" {
  type = string
}

variable "name" {
  type    = string
  default = "queue-app"
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "image" {
  type        = string
  description = "Immutable image digest, not latest"
}

variable "db_username" {
  type      = string
  sensitive = true
}

variable "hmac_secret_arn" {
  type        = string
  description = "Existing Secrets Manager JSON secret with key, version and adminToken keys"
}

variable "previous_hmac_secret_arns" {
  type        = map(string)
  default     = {}
  description = "Previous HMAC key version to Secrets Manager ARN; retain through retry grace"
}

variable "certificate_arn" {
  type = string
}
