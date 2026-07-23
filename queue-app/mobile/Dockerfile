FROM ghcr.io/cirruslabs/flutter:stable AS build
WORKDIR /app
COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get
COPY . .
ARG API_BASE_URL=
ARG DOMAIN_TOKEN=
RUN flutter build web --release --dart-define=API_BASE_URL=${API_BASE_URL} --dart-define=DOMAIN_TOKEN=${DOMAIN_TOKEN}

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build/web /usr/share/nginx/html
EXPOSE 80
