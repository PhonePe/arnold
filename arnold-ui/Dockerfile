ARG NODE_IMAGE=registry.example.com/image
FROM ${NODE_IMAGE} AS build

WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm install -g pnpm@11.25.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm lint && pnpm test:deployment && pnpm build && pnpm prune --prod

FROM ${NODE_IMAGE} AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NODE_CONFIG_DIR=/app/config \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=build /app/package.json /app/pnpm-lock.yaml ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/src/app/fonts/space-grotesk/OFL.txt ./licenses/space-grotesk/OFL.txt
COPY --from=build /app/src/app/fonts/ibm-plex-mono/OFL.txt ./licenses/ibm-plex-mono/OFL.txt
COPY --from=build /app/server.js /app/next.config.mjs ./
COPY --from=build /app/scripts/load-config.cjs ./scripts/load-config.cjs
RUN mkdir -p /app/config
EXPOSE 3000
CMD ["sh", "-c", "node scripts/load-config.cjs && exec node server.js"]
