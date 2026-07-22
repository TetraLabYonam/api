# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# 빈 문자열이면 client.ts의 BASE_URL이 ''가 되어, 같은 오리진(nginx)의 /api/를
# 상대경로로 호출한다 — 아래 nginx.conf가 그 /api/를 backend 컨테이너로 프록시한다.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---- Runtime stage ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
