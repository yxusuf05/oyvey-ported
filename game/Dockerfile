# One container, one port. The Node server serves both the built client and the WebSocket,
# so there is nothing to configure beyond a URL — which is the whole point: your friend
# opens a link and types a room code.

FROM node:22-alpine AS build
WORKDIR /app

RUN corepack enable

# Manifests first, so a dependency install is cached across source-only changes.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml .npmrc ./
COPY packages/shared/package.json packages/shared/
COPY packages/client/package.json packages/client/
COPY packages/server/package.json packages/server/
RUN pnpm install --frozen-lockfile

COPY tsconfig.base.json ./
COPY packages ./packages
RUN pnpm run build


FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=8787
ENV HOST=0.0.0.0

# The server bundle is self-contained (esbuild bundles the shared package and ws), so the
# runtime image carries no node_modules at all.
COPY --from=build /app/packages/server/dist ./packages/server/dist
COPY --from=build /app/packages/client/dist ./packages/client/dist

RUN addgroup -S prisma && adduser -S prisma -G prisma
USER prisma

EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -qO- "http://127.0.0.1:${PORT}/healthz" || exit 1

CMD ["node", "packages/server/dist/index.js"]
