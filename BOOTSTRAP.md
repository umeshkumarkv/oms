# BOOTSTRAP.md — Broker Dealer OMS POC
# Execute all steps in order. Do not skip any step.
# Do not implement business logic — stubs and structure only.
# Principal Architect: Umesh Kumar KV | April 2026

---

## Your Role

You are the bootstrap engineer for the Broker Dealer OMS POC.
Your job is to scaffold the complete project skeleton before
Dev 2 and Dev 3 start feature development in Week 2.

## Rules Before Starting

- Read CLAUDE.md fully before writing a single file
- Follow CLAUDE.md for all entity definitions, API contracts,
  Kafka topics, Redis keys, package structure, and domain rules
- Do not implement any business logic anywhere
- Stubs only — empty method bodies that compile and start
- Controllers return placeholder responses only
- Services have empty method stubs only
- One commit per major step below
- Final step — create PR to develop branch

---

## Step 1 — Parent pom.xml

Create pom.xml at repo root:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.4</version>
    <relativePath/>
  </parent>

  <groupId>com.brokerdealer</groupId>
  <artifactId>broker-dealer-parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <name>Broker Dealer OMS POC — Parent</name>

  <modules>
    <module>services/auth-service</module>
    <module>services/order-service</module>
    <module>services/client-service</module>
    <module>services/marketdata-service</module>
    <module>services/risk-service</module>
    <module>services/execution-service</module>
    <module>services/audit-service</module>
    <module>api-gateway</module>
  </modules>

  <properties>
    <java.version>21</java.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <jjwt.version>0.12.3</jjwt.version>
    <resilience4j.version>2.2.0</resilience4j.version>
    <springdoc.version>2.3.0</springdoc.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <lombok.version>1.18.30</lombok.version>
    <jacoco.version>0.8.11</jacoco.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>${spring-cloud.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>${jjwt.version}</version>
      </dependency>
      <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
      </dependency>
      <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>${jjwt.version}</version>
      </dependency>
      <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
      </dependency>
      <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
      </dependency>
      <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>${resilience4j.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <configuration>
            <excludes>
              <exclude>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
              </exclude>
            </excludes>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
            <source>21</source>
            <target>21</target>
            <annotationProcessorPaths>
              <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
              </path>
              <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
              </path>
            </annotationProcessorPaths>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.jacoco</groupId>
          <artifactId>jacoco-maven-plugin</artifactId>
          <version>${jacoco.version}</version>
          <executions>
            <execution>
              <goals><goal>prepare-agent</goal></goals>
            </execution>
            <execution>
              <id>report</id>
              <phase>test</phase>
              <goals><goal>report</goal></goals>
            </execution>
            <execution>
              <id>check</id>
              <goals><goal>check</goal></goals>
              <configuration>
                <rules>
                  <rule>
                    <element>BUNDLE</element>
                    <limits>
                      <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>
                      </limit>
                    </limits>
                  </rule>
                </rules>
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

---

## Step 2 — docker-compose.yml

Create docker-compose.yml at repo root with:

Infrastructure services:
- zookeeper: confluentinc/cp-zookeeper:7.5.0 — port 2181
- kafka: confluentinc/cp-kafka:7.5.0 — port 9092
  Environment:
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: true
    KAFKA_CREATE_TOPICS: >
      order.state.changed:1:1,
      risk.evaluation.requested:1:1,
      risk.evaluation.result:1:1,
      order.execution.requested:1:1,
      execution.completed:1:1,
      order.state.changed.DLT:1:1,
      risk.evaluation.requested.DLT:1:1,
      execution.completed.DLT:1:1
- redis: redis:7-alpine — port 6379
- postgres: postgres:15-alpine — port 5432
  Environment:
    POSTGRES_DB: brokerdealer
    POSTGRES_USER: brokerdealer
    POSTGRES_PASSWORD: brokerdealer
  Init script at docker/postgres/init.sql to create schemas:
    CREATE SCHEMA IF NOT EXISTS auth_schema;
    CREATE SCHEMA IF NOT EXISTS order_schema;
    CREATE SCHEMA IF NOT EXISTS client_schema;
    CREATE SCHEMA IF NOT EXISTS market_schema;
    CREATE SCHEMA IF NOT EXISTS risk_schema;
    CREATE SCHEMA IF NOT EXISTS execution_schema;
    CREATE SCHEMA IF NOT EXISTS audit_schema;

Application services — all depend on kafka, redis, postgres:
- api-gateway — port 8080:8080
- auth-service — port 8081:8081
- order-service — port 8082:8082
- client-service — port 8083:8083
- marketdata-service — port 8084:8084
- risk-service — port 8085:8085
- execution-service — port 8086:8086
- audit-service — port 8087:8087

Common environment variables for all app services:
  SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  SPRING_REDIS_HOST: redis
  SPRING_REDIS_PORT: 6379
  SPRING_DATASOURCE_USERNAME: brokerdealer
  SPRING_DATASOURCE_PASSWORD: brokerdealer
  JWT_SECRET: dev-secret-key-change-in-production-min-32-chars

Per service datasource URL:
  auth-service:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=auth_schema
  order-service:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=order_schema
  client-service:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=client_schema
  marketdata-service:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=market_schema
  risk-service:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=risk_schema
  execution-service:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=execution_schema
  audit-service:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=audit_schema

Health checks for all app services:
  test: curl -f http://localhost:{port}/actuator/health || exit 1
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s

---

## Step 3 — Kubernetes Manifests

Create k8s/ folder with:

k8s/namespace.yaml — namespace: broker-dealer-poc

k8s/configmap.yaml — non-secret config:
  SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka-service:9092
  SPRING_REDIS_HOST: redis-service
  SPRING_REDIS_PORT: "6379"

k8s/secret.yaml — base64 encoded:
  JWT_SECRET
  POSTGRES_PASSWORD

Per service in k8s/services/:
  {service}-deployment.yaml:
    replicas: 1
    image: umeshkumarkv/{service}:latest
    ports: containerPort {service-port}
    envFrom: configMapRef + secretRef
    livenessProbe:
      httpGet path: /actuator/health/liveness
      initialDelaySeconds: 60
      periodSeconds: 30
    readinessProbe:
      httpGet path: /actuator/health/readiness
      initialDelaySeconds: 30
      periodSeconds: 10
    resources:
      requests: memory 256Mi cpu 250m
      limits: memory 512Mi cpu 500m

  {service}-service.yaml:
    type: ClusterIP
    port: {service-port}

k8s/infra/:
  kafka-statefulset.yaml
  redis-deployment.yaml
  postgres-statefulset.yaml with PersistentVolumeClaim 1Gi

---

## Step 4 — Service Skeletons

Create identical structure for all 7 services.
Replace {service} with actual service name.
Replace {ServiceName} with PascalCase service name.
Replace {port} with service port from CLAUDE.md.
Replace {schema} with schema name from CLAUDE.md.

### 4a — Service pom.xml

For each service create services/{service}/pom.xml:
- parent: broker-dealer-parent
- artifactId: {service}
- name: Broker Dealer — {ServiceName}
- Dependencies:
  All services:
    spring-boot-starter-web
    spring-boot-starter-data-jpa
    spring-boot-starter-data-redis
    spring-boot-starter-actuator
    spring-boot-starter-validation
    spring-kafka
    springdoc-openapi-starter-webmvc-ui
    org.flywaydb:flyway-core
    org.postgresql:postgresql
    org.projectlombok:lombok (provided)
    org.mapstruct:mapstruct
    resilience4j-spring-boot3
    io.jsonwebtoken:jjwt-api
    io.jsonwebtoken:jjwt-impl (runtime)
    io.jsonwebtoken:jjwt-jackson (runtime)
    spring-boot-starter-test (test scope)

  auth-service only — additionally:
    spring-boot-starter-security
    spring-security-test (test scope)

  api-gateway only — replace spring-boot-starter-web with:
    spring-cloud-starter-gateway
    spring-boot-starter-webflux

### 4b — Dockerfile per service

Create services/{service}/Dockerfile:

FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY ../../pom.xml ../pom.xml
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE {port}
ENTRYPOINT ["java", "-XX:+UseVirtualThreads", "-jar", "/app/app.jar"]

### 4c — application.yml per service

Create services/{service}/src/main/resources/application.yml:

spring:
  application:
    name: {service}
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME:brokerdealer}
    password: ${SPRING_DATASOURCE_PASSWORD:brokerdealer}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        default_schema: {schema}
        format_sql: false
  flyway:
    schemas: {schema}
    locations: classpath:db/migration
    baseline-on-migrate: true
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: {service}-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.brokerdealer.*
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:6379}
      timeout: 2000ms

server:
  port: {port}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always

resilience4j:
  circuitbreaker:
    instances:
      default:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
  timelimiter:
    instances:
      default:
        timeout-duration: 2s

logging:
  level:
    com.brokerdealer: INFO
    org.springframework.kafka: WARN

### 4d — Main Application class per service

Create services/{service}/src/main/java/com/brokerdealer/{servicename}/{ServiceName}Application.java:

@SpringBootApplication
@EnableAsync
public class {ServiceName}Application {
    public static void main(String[] args) {
        SpringApplication.run({ServiceName}Application.class, args);
    }
}

### 4e — Domain entities

Create JPA entities with all fields from CLAUDE.md.
Use Lombok: @Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table
Use @GeneratedValue(strategy = GenerationType.UUID) for UUID primary keys.
Use BigDecimal for ALL monetary fields — never float or double.
Use Instant for all timestamp fields.
Use enums for status fields — define enums in domain package.

auth-service entities:
  User.java — id(UUID), username(String), passwordHash(String),
    role(UserRole enum: TRADER/OPERATIONS/ADMIN), createdAt(Instant)

order-service entities:
  Order.java — id(UUID), clientOrderId(String), clientId(UUID),
    symbol(String), side(OrderSide enum: BUY/SELL),
    orderType(OrderType enum: MARKET/LIMIT),
    timeInForce(TimeInForce enum: DAY/GTC),
    quantity(Integer), limitPrice(BigDecimal),
    status(OrderStatus enum: all 11 states from CLAUDE.md),
    rejectionReason(String), expiryDate(LocalDate),
    expiryApplied(ExpiryApplied enum: DEFAULT/TRADER),
    createdAt(Instant), updatedAt(Instant), createdBy(UUID)

client-service entities:
  Client.java — id(UUID), name(String),
    status(ClientStatus enum: ACTIVE/BLOCKED),
    tradingEnabled(Boolean), kycStatus(KycStatus enum: VERIFIED/PENDING/EXPIRED),
    kycExpiryDate(LocalDate), marginCallActive(Boolean),
    cashBalance(BigDecimal), reservedCash(BigDecimal),
    tradingLimit(BigDecimal), dailyOrderCount(Integer),
    dailyOrderValue(BigDecimal), dailyOrderLimit(BigDecimal),
    maxOpenOrders(Integer), avgOrderQty(Integer)
  Holding.java — id(UUID), clientId(UUID), symbol(String),
    quantity(Integer), avgPrice(BigDecimal)

marketdata-service entities:
  Instrument.java — symbol(String PK), bidPrice(BigDecimal),
    askPrice(BigDecimal), lastPrice(BigDecimal),
    tradable(Boolean), tradingHalted(Boolean),
    minQuantity(Integer), maxQuantity(Integer),
    dataTimestamp(Instant)

risk-service entities:
  RiskRuleConfig.java — ruleCode(String PK), enabled(Boolean),
    thresholdValue(BigDecimal), description(String),
    updatedAt(Instant)

execution-service entities:
  Execution.java — id(UUID), orderId(UUID), symbol(String),
    side(OrderSide enum: BUY/SELL), executedQty(Integer),
    executedPrice(BigDecimal), executedAt(Instant)

audit-service entities:
  AuditLog.java — id(UUID), entityType(String), entityId(UUID),
    previousState(String), newState(String), actorId(UUID),
    actorType(ActorType enum: USER/SYSTEM),
    eventTimestamp(Instant), metadata(String stored as JSON)

### 4f — Flyway migrations

Create V1__schema.sql — DDL matching entities above.
All tables in correct schema.
All constraints from CLAUDE.md entity definitions.

Create V2__seed.sql — mock data:

auth-service seed — 4 users with bcrypt hashed passwords:
  trader.john — TRADER — password: Demo@1234
  trader.sarah — TRADER — password: Demo@1234
  ops.manager — OPERATIONS — password: Demo@1234
  admin.user — ADMIN — password: Demo@1234

client-service seed — 5 clients:
  CLIENT-001 John Smith ACTIVE trading_enabled=true kyc=VERIFIED
    cash_balance=500000.00 reserved_cash=0.00
    trading_limit=500000.00 daily_order_limit=1000000.00
    max_open_orders=10 avg_order_qty=100
  Holdings: AAPL qty=100 avg_price=150.00
            MSFT qty=50 avg_price=400.00

  CLIENT-002 Sarah Johnson ACTIVE trading_enabled=true kyc=VERIFIED
    cash_balance=50000.00 reserved_cash=0.00
    trading_limit=50000.00 daily_order_limit=100000.00
    max_open_orders=5 avg_order_qty=20
  Holdings: TSLA qty=20 avg_price=170.00

  CLIENT-003 Mike Davis ACTIVE trading_enabled=false kyc=VERIFIED
    cash_balance=200000.00 reserved_cash=0.00

  CLIENT-004 Emily Chen BLOCKED trading_enabled=true kyc=VERIFIED
    cash_balance=100000.00 reserved_cash=0.00
  Holdings: GOOGL qty=10 avg_price=160.00

  CLIENT-005 Robert Wilson ACTIVE trading_enabled=true
    kyc=EXPIRED kyc_expiry_date=2025-01-01
    cash_balance=75000.00 reserved_cash=0.00

marketdata-service seed — 10 instruments:
  AAPL  bid=174.95 ask=175.05 last=175.00 tradable=true halted=false min=1 max=10000
  MSFT  bid=414.90 ask=415.10 last=415.00 tradable=true halted=false min=1 max=10000
  GOOGL bid=164.90 ask=165.10 last=165.00 tradable=true halted=false min=1 max=5000
  TSLA  bid=174.80 ask=175.20 last=175.00 tradable=true halted=false min=1 max=5000
  AMZN  bid=184.90 ask=185.10 last=185.00 tradable=true halted=false min=1 max=5000
  NVDA  bid=874.50 ask=875.50 last=875.00 tradable=true halted=false min=1 max=2000
  META  bid=509.80 ask=510.20 last=510.00 tradable=true halted=false min=1 max=5000
  JPM   bid=194.90 ask=195.10 last=195.00 tradable=true halted=false min=1 max=10000
  BAC   bid=37.95  ask=38.05  last=38.00  tradable=true halted=false min=1 max=50000
  HALT  bid=49.90  ask=50.10  last=50.00  tradable=true halted=true  min=1 max=10000

risk-service seed — risk_rule_config:
  SINGLE_ORDER_QTY_LIMIT  enabled=true  threshold=10000.00
  ORDER_VALUE_LIMIT        enabled=true  threshold=500000.00
  PRICE_DEVIATION_CHECK    enabled=false threshold=10.00
  NOTIONAL_VALUE_CHECK     enabled=false threshold=1000000.00
  ORDER_QTY_SPIKE_CHECK    enabled=false threshold=10.00
  CLIENT_ACTIVE_CHECK      enabled=false threshold=null
  INSTRUMENT_TRADABLE_CHECK enabled=false threshold=null
  MARKET_HOURS_CHECK       enabled=false threshold=null
  QUANTITY_POSITIVE_CHECK  enabled=false threshold=null
  LIMIT_PRICE_CHECK        enabled=false threshold=null
  CASH_BALANCE_CHECK       enabled=false threshold=null
  HOLDINGS_CHECK           enabled=false threshold=null
  ORDER_VALUE_SPIKE_CHECK  enabled=false threshold=null

### 4g — openapi.yaml per service

Create services/{service}/openapi.yaml
OpenAPI 3.0.3 format.
Use standard response envelope from CLAUDE.md for all responses.
Include all error codes and descriptions.

auth-service endpoints:
  POST /api/v1/auth/login
    Request: { username: string, password: string }
    Response 200: { data: { token: string, expiresAt: string, role: string } }
    Response 401: error envelope — INVALID_CREDENTIALS

  POST /api/v1/auth/logout
    Header: Authorization Bearer token
    Response 200: { data: { message: string } }

  GET /api/v1/auth/me
    Header: Authorization Bearer token
    Response 200: { data: { userId: string, username: string, role: string } }
    Response 401: error envelope — UNAUTHORIZED

order-service endpoints:
  POST /api/v1/orders
    Request: { clientOrderId, clientId, symbol, side, orderType,
               timeInForce, quantity, limitPrice?, expiryDate? }
    Response 202: { data: { orderId, status, expiryDate, expiryApplied } }
    Response 400: error envelope — VALIDATION_ERROR
    Response 422: error envelopes for all Level 2 failures

  GET /api/v1/orders
    Query: status, clientId, page, size
    Response 200: { data: [ order summaries ] }

  GET /api/v1/orders/{id}
    Response 200: { data: full order object }
    Response 404: error envelope — ORDER_NOT_FOUND

client-service endpoints:
  GET /api/v1/clients/{id}
    Response 200: { data: { clientId, name, status, tradingEnabled, kycStatus } }
    Response 404: error envelope — CLIENT_NOT_FOUND

  GET /api/v1/clients/{id}/portfolio
    Response 200: { data: { cashBalance, reservedCash, buyingPower,
                            holdings: [ { symbol, quantity, avgPrice,
                                         currentPrice, marketValue } ] } }
    Response 404: error envelope — CLIENT_NOT_FOUND

  PATCH /api/v1/clients/{id}/reserved-cash
    Request: { adjustment: string (BigDecimal), reason: string }
    Response 200: { data: { clientId, reservedCash } }
    Response 404: error envelope — CLIENT_NOT_FOUND

marketdata-service endpoints:
  GET /api/v1/market/{symbol}
    Response 200: { data: { symbol, bidPrice, askPrice, lastPrice,
                            tradable, halted, minQuantity, maxQuantity,
                            dataTimestamp } }
    Response 404: error envelope — SYMBOL_NOT_FOUND

  GET /api/v1/market/hours
    Response 200: { data: { marketOpen: boolean, openTime, closeTime, timezone } }

  GET /api/v1/market/instruments
    Response 200: { data: [ { symbol, tradable, halted } ] }

risk-service endpoints:
  GET /api/v1/risk/rules
    Response 200: { data: [ { ruleCode, enabled, thresholdValue, description } ] }

  PATCH /api/v1/risk/rules/{ruleCode}
    Request: { enabled: boolean, thresholdValue?: string }
    Response 200: { data: updated rule }
    Response 404: error envelope — RULE_NOT_FOUND

execution-service endpoints:
  GET /api/v1/executions/{orderId}
    Response 200: { data: [ { executionId, executedQty, executedPrice, executedAt } ] }
    Response 404: error envelope — ORDER_NOT_FOUND

audit-service endpoints:
  GET /api/v1/audit/orders/{id}
    Response 200: { data: [ { entityId, previousState, newState,
                               actorId, actorType, eventTimestamp, metadata } ] }
    Response 404: error envelope — ORDER_NOT_FOUND

  GET /api/v1/audit/trail
    Query: clientId, from, to, entityType, page, size
    Response 200: { data: paginated audit records }

### 4h — Controller stubs per service

Create one controller per resource.
Return placeholder ResponseEntity with TODO comment.
Include @RestController @RequestMapping @RequiredArgsConstructor.
Inject service via constructor.
No business logic — stub methods only.

Example stub method:
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable UUID id) {
      // TODO: implement — JIRA story BD-XX
      return ResponseEntity.ok().build();
  }

### 4i — Service stubs per service

Create one service class per resource.
Annotate with @Service @RequiredArgsConstructor @Slf4j.
Inject repository via constructor.
Empty method stubs — return null or empty Optional.
No business logic.

### 4j — Repository interfaces per service

Create one repository per entity.
Extend JpaRepository<Entity, UUID>.
No custom queries yet — add in feature stories.

### 4k — Config classes per service

KafkaConfig.java — all services:
  @Configuration
  ProducerFactory<String, Object> bean
  ConsumerFactory<String, Object> bean
  KafkaTemplate<String, Object> bean
  Dead letter topic configuration using DeadLetterPublishingRecoverer

RedisConfig.java — all services:
  @Configuration
  RedisTemplate<String, Object> bean with Jackson serialization
  CacheManager bean

SecurityConfig.java — auth-service and api-gateway only:
  auth-service: full JWT filter chain
  api-gateway: JWT validation filter for Spring Cloud Gateway

### 4l — Exception handler per service

GlobalExceptionHandler.java — @RestControllerAdvice:
  Handle all custom exceptions
  Map to standard error envelope from CLAUDE.md
  Log exception with request ID
  Never expose stack trace in response

Exception classes to create per service:
  ValidationException
  ClientNotFoundException (client-service, order-service)
  InsufficientBuyingPowerException (order-service)
  InsufficientHoldingsException (order-service)
  DuplicateOrderException (order-service)
  SelfTradeException (order-service)
  MarketClosedException (order-service)
  SymbolHaltedException (order-service)
  ServiceUnavailableException (order-service)

### 4m — Kafka producer and consumer stubs

Producer stubs — one per service that publishes:
  @Component @RequiredArgsConstructor @Slf4j
  Inject KafkaTemplate
  One method per topic published
  Stub — log and return, no actual publishing yet

Consumer stubs — one per service that consumes:
  @Component @KafkaListener annotation
  One method per topic consumed
  Stub — log received message, no processing yet

### 4n — Test skeletons per service

Create empty test classes:
  {Resource}ControllerTest.java — @WebMvcTest annotation
  {Resource}ServiceTest.java — @ExtendWith(MockitoExtension.class)
  Both classes contain one placeholder test that passes:
    @Test void contextLoads() {}

---

## Step 5 — API Gateway

Create api-gateway/ module:

pom.xml — parent broker-dealer-parent
  Dependencies: spring-cloud-starter-gateway, spring-boot-starter-webflux

application.yml:
  spring:
    cloud:
      gateway:
        routes:
          - id: auth-service
            uri: http://auth-service:8081
            predicates: Path=/api/v1/auth/**
          - id: order-service
            uri: http://order-service:8082
            predicates: Path=/api/v1/orders/**
          - id: client-service
            uri: http://client-service:8083
            predicates: Path=/api/v1/clients/**
          - id: marketdata-service
            uri: http://marketdata-service:8084
            predicates: Path=/api/v1/market/**
          - id: risk-service
            uri: http://risk-service:8085
            predicates: Path=/api/v1/risk/**
          - id: execution-service
            uri: http://execution-service:8086
            predicates: Path=/api/v1/executions/**
          - id: audit-service
            uri: http://audit-service:8087
            predicates: Path=/api/v1/audit/**

JWT validation filter stub — validates token on every request

---

## Step 6 — Frontend Skeleton

Create frontend/ with React 18 + TypeScript + Vite:

package.json dependencies:
  react: ^18.2.0
  react-dom: ^18.2.0
  react-router-dom: ^6.22.0
  axios: ^1.6.7
  typescript: ^5.3.3

package.json devDependencies:
  vite: ^5.1.0
  @vitejs/plugin-react: ^4.2.1
  @types/react: ^18.2.55
  @types/react-dom: ^18.2.19

vite.config.ts:
  plugins: [react()]
  server:
    proxy:
      /api: http://localhost:8080

src/main.tsx — React 18 createRoot entry point

src/App.tsx — BrowserRouter with routes:
  / → LoginPage
  /dashboard → DashboardPage (protected)
  /orders → OrderBlotterPage (protected)
  /portfolio → PortfolioPage (protected)
  /audit → AuditTrailPage (protected)

src/pages/ — empty shell components:
  LoginPage.tsx
  DashboardPage.tsx
  OrderBlotterPage.tsx
  PortfolioPage.tsx
  AuditTrailPage.tsx
  Each returns: <div>{PageName} — coming soon</div>

src/api/ — stub functions returning Promise.resolve(null):
  authApi.ts — login(), logout(), me()
  orderApi.ts — getOrders(), submitOrder(), getOrder()
  clientApi.ts — getPortfolio(), getClient()
  marketApi.ts — getInstruments(), getMarketHours(), getInstrument()
  auditApi.ts — getAuditTrail(), getOrderAudit()

src/types/ — TypeScript interfaces matching openapi.yaml schemas:
  order.types.ts
  client.types.ts
  market.types.ts
  audit.types.ts

---

## Step 7 — Service CLAUDE.md files

Create services/{service}/CLAUDE.md for each of the 7 services.

Each service CLAUDE.md must contain:
  1. Service purpose — one paragraph
  2. Port number
  3. Database schema name
  4. Kafka topics this service PRODUCES
  5. Kafka topics this service CONSUMES with consumer group ID
  6. Redis cache keys this service owns
  7. REST endpoints this service exposes — reference openapi.yaml
  8. REST endpoints this service CALLS (downstream dependencies)
  9. Entities owned by this service
  10. What this service must NEVER do
  11. Key business rules specific to this service

---

## Step 8 — Final Commit and PR

```bash
git checkout -b bootstrap/project-skeleton
git add .
git commit -m "chore: complete project skeleton — architect bootstrap

What was created:
- Parent pom.xml — Java 21, Spring Boot 3.2.x, all dependencies locked
- docker-compose.yml — all 7 services + Kafka + Redis + PostgreSQL
- k8s/ — Kubernetes manifests for all services and infrastructure
- 7 service skeletons — package structure, entities, stubs, migrations
- api-gateway — Spring Cloud Gateway with route configuration
- frontend/ — React 18 + TypeScript + Vite shell
- openapi.yaml — API contracts for all 7 services
- CLAUDE.md — per service instructions

What is NOT implemented (by design):
- No business logic anywhere
- No Kafka publishing or consuming
- No Redis caching
- No JWT validation logic
- No risk rule implementations
- Controllers return placeholder responses only

What Dev 2 and Dev 3 must know:
- Read root CLAUDE.md before every session
- Read service CLAUDE.md before working on that service
- Use openapi.yaml as the contract — do not deviate
- Implement within existing package structure — do not create new packages
- All entities are defined — do not add or remove fields without architect approval
- Run docker compose up to verify before opening any PR"

git push origin bootstrap/project-skeleton
```

Then open PR to develop with the commit message as description.
