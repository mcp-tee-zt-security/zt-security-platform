.PHONY: start stop reset fast-bundle benchmark
start:
	docker compose -f docker/docker-compose.yml up --build -d
stop:
	docker compose -f docker/docker-compose.yml down
reset:
	docker compose -f docker/docker-compose.yml down -v
fast-bundle:
	curl -s http://localhost:8091/v1/fast/policy-bundle
benchmark:
	k6 run benchmarks/k6-fast-path.js
