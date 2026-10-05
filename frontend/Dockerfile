# syntax=docker/dockerfile:1

# ---- build: static bundle with Vite ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- runtime: unprivileged nginx serving the bundle and proxying /api ----
FROM nginxinc/nginx-unprivileged:1.27-alpine
ENV PORT=8080 BACKEND_URL=http://backend:8080
# The official image renders /etc/nginx/templates/*.template with envsubst at startup.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
