# Construcción
FROM maven:3.8.5-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests

# Ejecución
FROM openjdk:17.0.1-jdk-slim

# No Prompts Interactivos
ENV DEBIAN_FRONTEND=noninteractive

# Instalación Dependencias
RUN apt-get update && apt-get install -y \
    curl \
    ca-certificates \
    gnupg \
    lsb-release \
    && rm -rf /var/lib/apt/lists/*

# POSTGRESQL CLIENT 17
RUN install -d /usr/share/postgresql-common/pgdg && \
    curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg && \
    echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg] http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list && \
    apt-get update && apt-get install -y postgresql-client-17

# MONGODB DATABASE TOOLS
RUN curl -fsSL https://www.mongodb.org/static/pgp/server-7.0.asc | gpg --dearmor -o /usr/share/keyrings/mongodb-server-7.0.gpg && \
    echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] http://repo.mongodb.org/apt/debian $(lsb_release -cs)/mongodb-org/7.0 main" | tee /etc/apt/sources.list.d/mongodb-org-7.0.list && \
    apt-get update && apt-get install -y mongodb-database-tools

# Limpiar Archivos Temporales
RUN rm -rf /var/lib/apt/lists/*

# Aplicación
COPY --from=build /target/backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
RUN pg_dump --version && mongodump --version

ENTRYPOINT ["java","-jar","app.jar"]
