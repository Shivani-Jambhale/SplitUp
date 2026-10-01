FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY src ./src
COPY web ./web

RUN mkdir out && javac -d out src/splitter/*.java

EXPOSE 8080

CMD ["java", "-cp", "out", "splitter.Server"]