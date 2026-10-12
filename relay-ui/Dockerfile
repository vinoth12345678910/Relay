# relay-ui: static build served by nginx, which also proxies /api to relay-server (same origin,
# so the backend needs no CORS change). Build context: this directory.
#   docker build -t relay-ui relay-ui
#   docker run -p 8081:8080 -e RELAY_API_UPSTREAM=http://relay-server:8080 relay-ui

FROM node:20-alpine AS build
WORKDIR /app
RUN npm install -g npm@11.6.2
COPY package.json package-lock.json .npmrc ./
RUN npm ci --ignore-scripts
COPY . .
# Typecheck + build. Tests and the contract check run in CI (they need ../relay-server).
RUN npm run build

FROM nginx:1.27-alpine
# The official image renders /etc/nginx/templates/*.template with envsubst at startup.
ENV RELAY_API_UPSTREAM=http://relay-server:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY security-headers.inc /etc/nginx/conf.d/security-headers.inc
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
# nginx:alpine runs workers as "nginx"; listen on an unprivileged port.
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
