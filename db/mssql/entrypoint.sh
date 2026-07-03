#!/bin/bash
/opt/mssql/bin/sqlservr &
MSSQL_PID=$!

echo ">>> 等待 SQL Server 啟動..."
for i in $(seq 1 30); do
    /opt/mssql-tools18/bin/sqlcmd \
        -S localhost -U sa -P "$SA_PASSWORD" \
        -Q "SELECT 1" -C -l 2 > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo ">>> SQL Server 就緒"
        break
    fi
    echo ">>> 等待中... ($i/30)"
    sleep 2
done

echo ">>> 建立資料庫 taskflow（若已存在則跳過）"
/opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P "$SA_PASSWORD" -C \
    -Q "IF DB_ID('taskflow') IS NULL CREATE DATABASE taskflow COLLATE Chinese_Taiwan_Stroke_CI_AS"

echo ">>> 執行 schema..."
/opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P "$SA_PASSWORD" \
    -d taskflow -i /init/01-schema.sql -C

echo ">>> 執行 seed..."
/opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P "$SA_PASSWORD" \
    -d taskflow -i /init/02-seed.sql -C

echo ">>> 初始化完成"
wait $MSSQL_PID
