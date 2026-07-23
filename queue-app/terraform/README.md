# AWS Terraform baseline

This baseline provisions the existing VPC public/private subnets, ALB, ECS Fargate (2–4 tasks), and Multi-AZ RDS PostgreSQL. The database is reachable only from the API security group.

Secrets are intentionally not Terraform variables. `hmac_secret_arn` references an existing Secrets Manager secret containing `key`, `version`, and `adminToken`; RDS uses its AWS-managed master secret. Pass immutable image digests rather than `latest`.

Validation only (does not create or change infrastructure):

```shell
terraform init
terraform fmt -check
terraform validate
```

Do not run `terraform apply` as part of manual validation. State backend, VPC/subnets, ACM, DNS, and secret creation/rotation remain environment-owner configuration outside this baseline.
