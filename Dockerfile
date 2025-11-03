# syntax=docker/dockerfile:1

FROM clojure:temurin-21-tools-deps-bookworm AS build

RUN apt-get update && apt-get install -y curl ca-certificates gnupg && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY deps.edn shadow-cljs.edn tailwind.config.js ./
COPY src ./src
COPY public ./public

RUN npm run build
# -P is Prepare: prefetch dependencies
RUN clojure -P -M:backend

FROM clojure:temurin-21-tools-deps-bookworm AS runtime

ENV ACME_DISABLE_RELOAD=1
ENV PORT=8080

WORKDIR /app

COPY --from=build /root/.m2 /root/.m2
COPY --from=build /app/deps.edn /app/deps.edn
COPY --from=build /app/src /app/src
COPY --from=build /app/public /app/public

EXPOSE 8080

CMD ["clojure", "-M:backend"]
