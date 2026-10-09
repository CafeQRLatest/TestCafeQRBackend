FROM public.ecr.aws/docker/library/node:20-alpine

WORKDIR /app

# Install git and tools required by Baileys npm dependencies
RUN apk add --no-cache git python3 make g++

COPY package*.json ./
RUN npm install --omit=dev

COPY server.js ./

# Session persistence
RUN mkdir -p /app/auth_sessions

EXPOSE 3005

VOLUME ["/app/auth_sessions"]

CMD ["node", "server.js"]
