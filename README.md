# Patient Management – Microservices Monorepo

A Spring Boot microservices system demonstrating REST, gRPC, Kafka, and an API Gateway, with
optional AWS-style infrastructure via LocalStack.

- Services
    - api-gateway (Spring Cloud Gateway, port 4004) – routes and JWT validation
    - auth-service (port 4005) – auth endpoints and OpenAPI
    - patient-service (port 4000) – REST CRUD; publishes Kafka events to topic "patient"
    - billing-service (HTTP 4001, gRPC 9001) – gRPC server
    - analytics-service – Kafka consumer of topic "patient"
    - infrastructure – CDK/CloudFormation artifacts and helper script

See arch.png for a conceptual diagram.

## Tech stack

- Java 21, Spring Boot 3.5.x, Maven
- Spring Web, Data JPA, Validation, Spring for Apache Kafka
- Spring Cloud Gateway
- gRPC (grpc-java, grpc-spring-boot-starter)
- PostgreSQL (dev/prod) or H2 (optional dev)
- LocalStack + AWS CLI for infra testing (CloudFormation, ECS/ALB, etc.)
- Docker (recommended for local orchestration)

## Service ports and routes

- api-gateway: 4004
    - /auth/** -> http://auth-service:4005/**
    - /api/patients/** -> http://patient-service:4000/patients/** (JWT required by gateway filter)
    - /api-docs/patients -> patient-service /v3/api-docs
    - /api-docs/auth -> auth-service /v3/api-docs
- auth-service: 4005
- patient-service: 4000
- billing-service: HTTP 4001, gRPC 9001
- analytics-service: no HTTP by default; Kafka consumer

Messaging

- Kafka topic: patient
- Producer: patient-service
- Consumer: analytics-service

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop (Windows: WSL2 recommended)
- Local PostgreSQL and Kafka (or run via Docker, see below)
- Optional: AWS CLI v2 + LocalStack for infrastructure

## Quickstart (Docker-first)

Gateway URIs point to container hostnames (auth-service, patient-service). Prefer running services
in Docker on a shared network.

1) Create a Docker network

```bash
docker network create pm-net
```

2) Start PostgreSQL

```bash
docker run -d --name postgres --network pm-net -p 5432:5432 \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=patientdb postgres:16
```

3) Start Kafka (single-broker) – Redpanda example

```bash
docker run -d --name redpanda --network pm-net -p 9092:9092 -p 9644:9644 \
  redpandadata/redpanda:latest start --overprovisioned --smp 1 --memory 512M \
  --node-id 0 --check=false --kafka-addr PLAINTEXT://0.0.0.0:9092 \
  --advertise-kafka-addr PLAINTEXT://redpanda:9092
```

4) Build all services

```bash
mvn -q -T 1C -DskipTests clean package
```

5) Run services as containers (names must match gateway URIs)

- patient-service

```bash
docker build -t patient-service:local ./patient-service

docker run -d --name patient-service --network pm-net -p 4000:4000 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/patientdb \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=redpanda:9092 \
  patient-service:local
```

- auth-service

```bash
docker build -t auth-service:local ./auth-service

docker run -d --name auth-service --network pm-net -p 4005:4005 auth-service:local
```

- billing-service

```bash
docker build -t billing-service:local ./billing-service

docker run -d --name billing-service --network pm-net -p 4001:4001 -p 9001:9001 \
  billing-service:local
```

- analytics-service

```bash
docker build -t analytics-service:local ./analytics-service

docker run -d --name analytics-service --network pm-net \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=redpanda:9092 \
  analytics-service:local
```

- api-gateway

```bash
docker build -t api-gateway:local ./api-gateway

docker run -d --name api-gateway --network pm-net -p 4004:4004 api-gateway:local
```

6) Smoke tests

```bash
curl -i http://localhost:4004/api/patients
curl -i http://localhost:4004/api-docs/auth
curl -i http://localhost:4004/api-docs/patients
```

Note: Gateway may require a valid JWT for /api/patients/** depending on JwtValidation filter
configuration.

## Running natively (without Docker)

Ensure PostgreSQL and Kafka are running locally, then start each service with env vars.

- patient-service

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/patientdb \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres \
SPRING_JPA_HIBERNATE_DDL_AUTO=update \
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
./mvnw spring-boot:run
```

- auth-service

```bash
./mvnw spring-boot:run
```

- billing-service

```bash
./mvnw spring-boot:run
```

- analytics-service

```bash
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 ./mvnw spring-boot:run
```

- api-gateway (either add host aliases for auth-service/patient-service or adjust URIs to localhost
  in its application.yml)

```bash
./mvnw spring-boot:run
```

## Build and test

- Build all modules

```bash
mvn clean verify
```

- Build a single module

```bash
mvn -pl patient-service -am clean verify
```

## LocalStack infrastructure

The infrastructure module contains a synthesized CloudFormation template at
infrastructure/cdk.out/localstack.template.json and a helper deploy script.

Start LocalStack (Free) in Docker

```bash
docker run -d --name localstack -p 4566:4566 -p 4510-4559:4510-4559 \
  -e DEFAULT_REGION=us-east-1 \
  -e SERVICES=cloudformation,iam,sts,ecs,elbv2,ecr,ec2,logs \
  localstack/localstack:latest
```

Configure an AWS CLI profile for LocalStack (dummy creds)

```bash
aws configure set aws_access_key_id test --profile localstack
aws configure set aws_secret_access_key test --profile localstack
aws configure set region us-east-1 --profile localstack
```

Deploy the stack

```bash
cd infrastructure
bash ./localstack-deploy.sh
```

The script will delete stack patient-management (if present), deploy localstack.template.json, and
print the first ELB DNS name (if created).

Troubleshooting

- InvalidClientTokenId: set any non-empty credentials and region when using the LocalStack endpoint.
    - PowerShell
      ```powershell
      $env:AWS_ACCESS_KEY_ID='test'; $env:AWS_SECRET_ACCESS_KEY='test'; $env:AWS_DEFAULT_REGION='us-east-1'
      ```
    - Bash
      ```bash
      AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
        aws --endpoint-url http://localhost:4566 cloudformation describe-stacks
      ```
- ELBv2 TargetGroup CREATE_FAILED with reason "plugin ... elbv2:pro is disabled": the requested
  ALB/TargetGroup features require LocalStack Pro.
    - Option A: Switch to Pro and redeploy (see below).
    - Option B: Remove/disable ALB/TargetGroup/Listener resources for LocalStack runs in your CDK
      app and re-synthesize a LocalStack-friendly template.

Switching to LocalStack Pro in Docker

1) Get your Pro auth token from your LocalStack account
2) Restart the container with the token

```bash
docker rm -f localstack || true

docker run -d --name localstack -p 4566:4566 -p 4510-4559:4510-4559 \
  -e LOCALSTACK_AUTH_TOKEN=<your-token> \
  -e DEFAULT_REGION=us-east-1 \
  -e SERVICES=cloudformation,iam,sts,ecs,elbv2,ecr,ec2,logs \
  localstack/localstack:latest
```

3) Check logs for "Pro features enabled" and re-run the deploy.

Using LocalStack Desktop

- Activate your license in Desktop; it injects the auth token and manages the container.

Clean up

```bash
aws --endpoint-url=http://localhost:4566 --profile localstack \
  cloudformation delete-stack --stack-name patient-management
```

## Security

- The API Gateway enforces a JwtValidation filter on /api/patients/**. Obtain a JWT from
  auth-service and include it per your setup, or adjust/disable the filter for local dev.

## Known tips and limitations

- Gateway URIs target container hostnames; prefer Docker or add host aliases when running natively.
- H2 settings exist in patient-service but are commented out; use PostgreSQL for dev or enable H2
  for quick tests.
- On Windows, run infrastructure bash scripts via Git Bash or WSL.

## Repository structure

- api-gateway/
- auth-service/
- billing-service/
- analytics-service/
- patient-service/
- infrastructure/
- integration-tests/
- arch.png

## License

This repository includes third-party dependencies that are subject to their own licenses. Ensure
compliance when distributing.

