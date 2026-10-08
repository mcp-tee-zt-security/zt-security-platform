FROM golang:1.22 AS build
WORKDIR /src
COPY go.mod ./
COPY main.go ./
RUN go build -o /out/zt-policy-operator main.go
FROM gcr.io/distroless/static-debian12
COPY --from=build /out/zt-policy-operator /zt-policy-operator
ENTRYPOINT ["/zt-policy-operator"]
