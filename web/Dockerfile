# Bahçeden web imajı: çok aşamalı derleme.
#
# Derleme bağlamı DEPO KÖKÜDÜR: sözleşme (shared/contracts) derleme sırasında
# okunur ve tipler ondan üretilir (ADR-009). Üretilen tipler commit'lenmediği
# için imaj onları kendi üretmek zorundadır.

# ── Aşama 1: bağımlılıklar ──────────────────────────────────
FROM node:22-alpine AS deps
WORKDIR /app
COPY web/package.json web/package-lock.json ./
RUN npm ci

# ── Aşama 2: derleme ────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY web/ ./
# Sözleşme dosyası, gen:api betiğinin beklediği göreli yola konur.
COPY shared/contracts/ ../shared/contracts/
RUN npm run gen:api && npm run build

# ── Aşama 3: çalışma zamanı ─────────────────────────────────
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000

RUN addgroup -S bahceden && adduser -S -G bahceden bahceden
COPY --from=build --chown=bahceden:bahceden /app/.next/standalone ./
COPY --from=build --chown=bahceden:bahceden /app/.next/static ./.next/static
COPY --from=build --chown=bahceden:bahceden /app/public ./public
USER bahceden

EXPOSE 3000
CMD ["node", "server.js"]
