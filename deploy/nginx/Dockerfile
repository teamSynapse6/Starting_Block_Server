FROM nginx:alpine

ENV TZ=Asia/Seoul

RUN apk add --no-cache redis tzdata \
    && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone

COPY nginx.conf /etc/nginx/nginx.conf

CMD ["sh", "-c", "mkdir -p /data/redis && redis-server --bind 0.0.0.0 --protected-mode no --port 6379 --appendonly yes --save 60 1000 --dir /data/redis --daemonize yes && nginx -g 'daemon off;'"]
