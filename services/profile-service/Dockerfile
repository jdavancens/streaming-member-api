FROM node:20-alpine AS builder
WORKDIR /app
COPY services/profile-service/package*.json services/profile-service/tsconfig.json ./
RUN npm ci
COPY services/profile-service/src/ ./src/

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/src ./src
COPY services/profile-service/package.json services/profile-service/tsconfig.json ./
RUN npm run build
CMD ["node", "dist/index.js"]
