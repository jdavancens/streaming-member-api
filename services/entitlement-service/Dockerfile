FROM node:20-alpine AS builder
WORKDIR /app
COPY services/entitlement-service/package*.json services/entitlement-service/tsconfig.json ./
RUN npm ci
COPY services/entitlement-service/src/ ./src/

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/src ./src
COPY services/entitlement-service/package.json services/entitlement-service/tsconfig.json ./
RUN npm run build
CMD ["node", "dist/index.js"]
