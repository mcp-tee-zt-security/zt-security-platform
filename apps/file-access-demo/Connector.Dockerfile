FROM python:3.13-slim
WORKDIR /app
COPY apps/file-access-demo/connector.py /app/connector.py
RUN mkdir -p /state && chown -R 10001:10001 /state /app
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
CMD ["python", "/app/connector.py"]
