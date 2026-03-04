# ==============================================================================
# Dockerfile multi-stage para Platform
# Stage 1: Build con Maven + JDK 17
# Stage 2: Runtime con JRE 17 (distroless-like)
# ==============================================================================

# ── Stage 1: Build ──
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copiar POMs primero para cachear dependencias
COPY pom.xml .
COPY shared-libs/pom.xml shared-libs/
COPY shared-libs/domain-core/pom.xml shared-libs/domain-core/
COPY shared-libs/common-dto/pom.xml shared-libs/common-dto/
COPY shared-libs/mapping/pom.xml shared-libs/mapping/
COPY shared-libs/utils/pom.xml shared-libs/utils/
COPY core/pom.xml core/
COPY core/tenancy/pom.xml core/tenancy/
COPY core/auth-security/pom.xml core/auth-security/
COPY core/catalogs/pom.xml core/catalogs/
COPY core/files/pom.xml core/files/
COPY core/audit/pom.xml core/audit/
COPY core/events/pom.xml core/events/
COPY core/form/pom.xml core/form/
COPY core/workflow/pom.xml core/workflow/
COPY modules/pom.xml modules/
COPY modules/customers/pom.xml modules/customers/
COPY modules/employees/pom.xml modules/employees/
COPY modules/documents/pom.xml modules/documents/
COPY modules/sales/pom.xml modules/sales/
COPY ui/pom.xml ui/
COPY ui/zk-app/pom.xml ui/zk-app/
COPY platform-app/pom.xml platform-app/

# Descargar dependencias (layer cache)
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B -q 2>/dev/null || true

# Copiar código fuente completo
COPY . .

# Build (sin tests)
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──
FROM eclipse-temurin:17-jre-alpine AS runtime

# Metadata
LABEL maintainer="Platform Team <platform@lreyes.com>"
LABEL description="Platform multi-tenant (Spring Boot + ZK + Flowable)"

# Usuario no-root
RUN addgroup -S platform && adduser -S platform -G platform

WORKDIR /app

# Copiar JAR del stage anterior
COPY --from=builder /build/platform-app/target/platform-app-*.jar app.jar

# Directorio para archivos temporales y logs
RUN mkdir -p /app/logs /app/tmp && chown -R platform:platform /app

USER platform

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM flags optimizados para contenedores
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-local}", \
    "-jar", "app.jar"]
