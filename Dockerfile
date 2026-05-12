# Estágio de build
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copiar arquivos do projeto
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY src ./src

# Fazer o build do projeto (ignorando testes para ser mais rápido na VM)
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

# Estágio de execução
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar o JAR gerado do estágio anterior
COPY --from=build /app/target/*.jar app.jar

# Expor a porta que a aplicação usa
EXPOSE 8443

# Comando para iniciar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
