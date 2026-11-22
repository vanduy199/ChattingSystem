FROM eclipse-temurin:25-jre
WORKDIR /app

# Cài đặt FFmpeg trên môi trường container
RUN apt-get update \
 && apt-get install -y --no-install-recommends ffmpeg \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app app

# Đảm bảo thư mục tạm /tmp/media-work có quyền ghi cho app user
RUN mkdir -p /tmp/media-work && chown -R app:app /tmp/media-work

COPY target/*.jar app.jar

USER app

EXPOSE 8080
# Dành 40% RAM cho tiến trình FFmpeg ngoài heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
