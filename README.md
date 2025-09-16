# 🏦 account-service

[![Quarkus](https://img.shields.io/badge/Framework-Quarkus-blue?logo=quarkus)](https://quarkus.io/)
[![Java](https://img.shields.io/badge/Java-21-green?logo=java)](https://adoptium.net/)
[![MongoDB](https://img.shields.io/badge/Database-MongoDB-brightgreen?logo=mongodb)](https://www.mongodb.com/)
[![Build](https://img.shields.io/badge/Build-Maven-red?logo=apache-maven)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Container-Docker-blue?logo=docker)](https://www.docker.com/)

## 📋 Descripción

**account-service** es un microservicio bancario desarrollado con [Quarkus](https://quarkus.io/) para la gestión de cuentas bancarias y productos de crédito. Permite crear, consultar, listar y eliminar cuentas, integrándose con otros servicios mediante REST y utilizando MongoDB como base de datos.

---

## 🚀 Características

- 📄 API RESTful para gestión de cuentas bancarias y créditos.
- 🔗 Integración con microservicio de clientes vía REST Client.
- 🗄️ Persistencia reactiva con MongoDB y Panache.
- 🛡️ Validaciones de negocio para productos activos y pasivos.
- 📑 Documentación OpenAPI/Swagger UI lista para usar.
- 🐳 Listo para despliegue en Docker (JVM y nativo).

---

## 🛠️ Tecnologías

- ![Java](https://img.shields.io/badge/-Java%2021-informational?logo=java)
- ![Quarkus](https://img.shields.io/badge/-Quarkus-informational?logo=quarkus)
- ![MongoDB](https://img.shields.io/badge/-MongoDB-informational?logo=mongodb)
- ![MapStruct](https://img.shields.io/badge/-MapStruct-informational?logo=mapstruct)
- ![Lombok](https://img.shields.io/badge/-Lombok-informational?logo=lombok)
- ![Docker](https://img.shields.io/badge/-Docker-informational?logo=docker)

---

## 📦 Instalación y Ejecución

### 🖥️ Modo desarrollo

```bash
./mvnw quarkus:dev
```
Accede a la Dev UI en: [http://localhost:8081/q/dev/](http://localhost:8081/q/dev/)

### 🏗️ Empaquetado

```bash
./mvnw package
```
El archivo ejecutable estará en `target/quarkus-app/`.

### 🐳 Docker

#### JVM

```bash
docker build -f src/main/docker/Dockerfile.jvm -t quarkus/account-service-jvm .
docker run -i --rm -p 8081:8081 quarkus/account-service-jvm
```

#### Nativo

```bash
./mvnw package -Dnative
docker build -f src/main/docker/Dockerfile.native -t quarkus/account-service .
docker run -i --rm -p 8081:8081 quarkus/account-service
```

---

## 📚 Endpoints principales

- `POST /accounts` — Crear cuenta bancaria o de crédito
- `GET /accounts/{accountId}` — Consultar cuenta por ID
- `GET /accounts?customerId=...` — Listar cuentas por cliente
- `DELETE /accounts/{accountId}` — Eliminar (inactivar) cuenta

Consulta la documentación interactiva en:  
[http://localhost:8081/swagger-ui](http://localhost:8081/swagger-ui)

---

## ⚙️ Configuración

Edita [`src/main/resources/application.properties`](src/main/resources/application.properties) para ajustar:

- Conexión a MongoDB
- Puertos HTTP
- URLs de microservicios
- Nivel de logs

---

## 🧑‍💻 Estructura del proyecto

```
src/
  main/
    java/com/bancario/account/
      resource/        # Controladores REST
      service/         # Lógica de negocio
      repository/      # Acceso a datos
      dto/             # Objetos de transferencia
      enums/           # Enumeraciones de dominio
      exception/       # Manejo global de errores
      mapper/          # MapStruct mappers
    resources/         # Configuración y recursos
    docker/            # Dockerfiles
test/
  java/                # Pruebas unitarias y de integración
```

---