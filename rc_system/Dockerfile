# Stage 1: Build the application
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Cai dat Maven truc tiep tren nen Java 25
RUN apt-get update && apt-get install -y maven

COPY pom.xml .
# Tai truoc cac dependency de toi uu hoa cache Docker
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/rc_system-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
