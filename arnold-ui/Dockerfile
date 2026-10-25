ARG NODE_IMAGE=node:22-bookworm-slim
FROM ${NODE_IMAGE} AS build

WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci --include=dev
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
COPY --from=build /app/src/app/fonts/space-grotesk/OFL.txt ./licenses/space-grotesk/OFL.txt
COPY --from=build /app/src/app/fonts/ibm-plex-mono/OFL.txt ./licenses/ibm-plex-mono/OFL.txt
COPY --from=build /app/server.js /app/auth.js /app/next.config.mjs ./
COPY --from=build /app/scripts/load-config.cjs ./scripts/load-config.cjs
RUN mkdir -p /app/config
EXPOSE 3000
CMD ["sh", "-c", "node scripts/load-config.cjs && exec node server.js"]
