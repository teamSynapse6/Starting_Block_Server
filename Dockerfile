# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk AS builder

WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x ./gradlew \
    && ./gradlew generateOpenApiDocs -x test \
    && mkdir -p src/main/resources/static \
    && cp build/openapi/openapi.json src/main/resources/static/openapi.json \
    && ./gradlew bootJar -x test

# Debian 기반의 JRE 런타임 준비
FROM eclipse-temurin:17-jre-jammy AS runtime-base

WORKDIR /app

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    apt-get update && apt-get install -y --no-install-recommends wget unzip curl fonts-liberation \
    python3 python3-pip python3-venv build-essential antiword \
    libasound2 libatk-bridge2.0-0 libatk1.0-0 libcups2 libdrm2 libgbm1 \
    libgtk-3-0 libnspr4 libnss3 libx11-xcb1 libxcomposite1 libxdamage1 \
    libxrandr2 xdg-utils xvfb \
    && wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get install -y --no-install-recommends ./google-chrome-stable_current_amd64.deb \
    && rm ./google-chrome-stable_current_amd64.deb \
    && rm -rf /var/lib/apt/lists/*

RUN CHROME_MAJOR="$(google-chrome --product-version | cut -d. -f1)" \
    && CHROMEDRIVER_VERSION="$(curl -fsSL "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_${CHROME_MAJOR}")" \
    && wget -O /tmp/chromedriver.zip "https://storage.googleapis.com/chrome-for-testing-public/${CHROMEDRIVER_VERSION}/linux64/chromedriver-linux64.zip" \
    && unzip /tmp/chromedriver.zip -d /tmp/chromedriver \
    && mv /tmp/chromedriver/chromedriver-linux64/chromedriver /usr/local/bin/chromedriver \
    && chmod +x /usr/local/bin/chromedriver \
    && rm -rf /tmp/chromedriver /tmp/chromedriver.zip

ENV DISPLAY=:99
ENV PYTHONPATH=/app/ai-rag
ENV UV_HTTP_TIMEOUT=300
ENV UV_LINK_MODE=copy
ENV CHROME_BIN=/usr/bin/google-chrome
ENV CHROME_DRIVER=/usr/local/bin/chromedriver

FROM runtime-base AS ai-rag-deps

COPY ai-rag/requirements.txt /app/ai-rag/requirements.txt
RUN --mount=type=cache,target=/root/.cache/uv,sharing=locked \
    python3 -m pip install --no-cache-dir uv \
    && python3 -m venv --system-site-packages /opt/ai-rag-venv \
    && uv pip install --python /opt/ai-rag-venv/bin/python --index-strategy unsafe-best-match -r /app/ai-rag/requirements.txt

FROM ai-rag-deps AS runtime

COPY ai-rag /app/ai-rag

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
COPY deploy/docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
