# Первый этап: сборка приложения
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon -x test

# Второй этап: запуск
FROM amazoncorretto:17-alpine
WORKDIR /app
RUN mkdir -p /app/data/full_products
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]