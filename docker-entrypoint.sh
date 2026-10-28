#!/bin/sh
set -eu

mkdir -p /data
rm -f /data/transaction-dispute.db

exec java -jar /app/app.jar
