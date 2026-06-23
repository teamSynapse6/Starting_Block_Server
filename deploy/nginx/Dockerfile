FROM falkordb/falkordb-server:latest AS falkordb

FROM debian:trixie-slim

ENV TZ=Asia/Seoul

RUN set -eux; \
    export DEBIAN_FRONTEND=noninteractive; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      libgomp1 \
      libssl3t64 \
      libstdc++6 \
      libzstd1 \
      nginx \
      tzdata \
      zlib1g; \
    rm -rf /var/lib/apt/lists/*; \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime; \
    echo "Asia/Seoul" > /etc/timezone; \
    mkdir -p /run/nginx /var/lib/falkordb/data

COPY --from=falkordb /usr/local/bin/redis-server /usr/local/bin/redis-server
COPY --from=falkordb /usr/local/bin/redis-cli /usr/local/bin/redis-cli
COPY --from=falkordb /var/lib/falkordb/bin/falkordb.so /var/lib/falkordb/bin/falkordb.so
COPY nginx.conf /etc/nginx/nginx.conf
COPY start-proxy-falkordb.sh /usr/local/bin/start-proxy-falkordb.sh
RUN chmod +x /usr/local/bin/start-proxy-falkordb.sh

ENTRYPOINT []
CMD ["/usr/local/bin/start-proxy-falkordb.sh"]
