FROM golang:1.26 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
FROM build AS test
COPY config/ ./config/
RUN go test -race ./...
FROM build AS compile
RUN CGO_ENABLED=0 go build -trimpath -o /out/zt-policy-operator .
FROM gcr.io/distroless/static-debian12
COPY --from=compile /out/zt-policy-operator /zt-policy-operator
USER 65532:65532
ENTRYPOINT ["/zt-policy-operator"]
