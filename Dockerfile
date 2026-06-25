# ──────────────────────────────────────────────────────────────
# Stage 1 – Builder: compila o Kotlin/JS em produção
#
# O repositório não inclui gradlew nem gradle-8.5/ (ambos estão
# no .gitignore). Instalamos o Gradle 8.5 via SDKMAN no builder.
# ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder

# Instala curl e zip (necessários para SDKMAN)
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl zip unzip \
    && rm -rf /var/lib/apt/lists/*

# Instala SDKMAN e Gradle 8.5
ENV GRADLE_VERSION=8.5
ENV SDKMAN_DIR=/root/.sdkman
RUN curl -s "https://get.sdkman.io" | bash && \
    bash -c "source $SDKMAN_DIR/bin/sdkman-init.sh && sdk install gradle $GRADLE_VERSION"
ENV PATH="$SDKMAN_DIR/candidates/gradle/current/bin:$PATH"

WORKDIR /app

# Copia configuração Gradle (camada de cache)
COPY settings.gradle.kts build.gradle.kts ./

# Copia o código-fonte
COPY src/ src/

# Pré-aquece o cache de dependências
RUN gradle dependencies --no-daemon --quiet 2>/dev/null || true

# Build de produção
RUN gradle jsBrowserProductionWebpack --no-daemon

# ──────────────────────────────────────────────────────────────
# Stage 2 – Runner: Nginx servindo os arquivos estáticos
# ──────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runner

RUN rm /etc/nginx/conf.d/default.conf

COPY <<'EOF' /etc/nginx/conf.d/sonix.conf
server {
    listen       80;
    server_name  _;
    root         /usr/share/nginx/html;
    index        index.html;

    gzip on;
    gzip_types text/plain text/css application/javascript application/json
               application/wasm font/woff2;
    gzip_min_length 1024;

    # Assets com hash → cache imutável
    location ~* \.(js|css|wasm|woff2|png|svg|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Service Worker → sem cache
    location = /sw.js {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Health check
    location /health {
        return 200 'OK';
        add_header Content-Type text/plain;
    }
}
EOF

COPY --from=builder /app/build/dist/js/productionExecutable/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/health || exit 1

LABEL org.opencontainers.image.title="Sonix" \
      org.opencontainers.image.description="Open-source music player — Kotlin/JS served by Nginx" \
      org.opencontainers.image.source="https://github.com/zequinha-taveira/sonix" \
      org.opencontainers.image.licenses="MIT"

CMD ["nginx", "-g", "daemon off;"]
