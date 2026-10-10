FROM python:3.13-slim
WORKDIR /app
COPY connector.py /app/connector.py
RUN mkdir /state && chown -R 10001:10001 /state /app
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
CMD ["python", "/app/connector.py"]
