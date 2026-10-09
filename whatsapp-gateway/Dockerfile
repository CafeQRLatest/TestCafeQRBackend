FROM node:18-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY server.js ./

# Session persistence
RUN mkdir -p /app/auth_sessions

EXPOSE 3005

VOLUME ["/app/auth_sessions"]

CMD ["node", "server.js"]
