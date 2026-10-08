FROM rust:1.90-alpine AS build
RUN apk add --no-cache musl-dev
WORKDIR /src
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release --locked
FROM alpine:3.22
RUN adduser -D -H -s /sbin/nologin zt
COPY --from=build /src/target/release/zt-policy-data-plane /usr/local/bin/zt-policy-data-plane
USER zt
EXPOSE 8091
ENTRYPOINT ["/usr/local/bin/zt-policy-data-plane"]
