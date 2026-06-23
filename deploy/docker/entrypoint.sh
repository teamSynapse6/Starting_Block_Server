#!/bin/sh
set -eu

cleanup() {
  if [ -n "${SPRING_PID:-}" ]; then
    kill "${SPRING_PID}" 2>/dev/null || true
  fi
  if [ -n "${XVFB_PID:-}" ]; then
    kill "${XVFB_PID}" 2>/dev/null || true
  fi
}

trap cleanup INT TERM EXIT

rm -f /tmp/.X99-lock
Xvfb :99 -ac &
XVFB_PID="$!"

cd /app
java -jar /app/app.jar &
SPRING_PID="$!"

wait "${SPRING_PID}"
