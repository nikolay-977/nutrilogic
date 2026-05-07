# Первый этап: сборка приложения
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon -x test

# Второй этап: запуск
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# Создаём папку для данных (если ещё нет)
RUN mkdir -p /app/data/full_products
# Открываем порт приложения
EXPOSE 8080
# Запуск
ENTRYPOINT ["java", "-jar", "app.jar"]