FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json tsconfig.json ./
COPY index.html ./
COPY src ./src
ARG VITE_OIDC_ISSUER=http://localhost:8089/realms/zt-mcp-demo
ARG VITE_OIDC_CLIENT_ID=zt-dashboard
ENV VITE_OIDC_ISSUER=$VITE_OIDC_ISSUER VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
RUN npm ci && npm run build
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
