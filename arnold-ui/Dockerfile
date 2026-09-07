ARG NODE_IMAGE=registry.example.com/image
FROM ${NODE_IMAGE} AS build

WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm config set registry https://registry.example.com \
    && npm config set @phonepe:registry https://registry.example.com \
    && npm ci --include=dev
COPY . .
RUN npm run lint && npm run test:deployment && npm run build && npm prune --omit=dev

FROM ${NODE_IMAGE} AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NODE_CONFIG_DIR=/app/config \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=build /app/package.json /app/package-lock.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/server.js /app/next.config.mjs ./
COPY --from=build /app/scripts/load-config.cjs ./scripts/load-config.cjs
RUN mkdir -p /app/config
EXPOSE 3000
CMD ["sh", "-c", "node scripts/load-config.cjs && exec node server.js"]
