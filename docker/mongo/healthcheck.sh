#!/bin/bash
set -e
if mongosh --quiet --eval 'quit(rs.status().ok ? 0 : 1)' >/dev/null 2>&1; then
  exit 0
fi
mongosh --quiet --eval 'rs.initiate({_id:"rs0", members:[{_id:0, host:"mongo:27017"}]})' >/dev/null 2>&1 || true
exit 1
