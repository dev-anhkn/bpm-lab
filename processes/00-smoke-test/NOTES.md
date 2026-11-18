# 00 – Smoke test

## Mục đích
Kiểm tra toàn bộ chuỗi chạy được trước khi làm quy trình thật:
Modeler → deploy → engine tạo job → worker Java nhận job → trả biến → instance kết thúc.

## Model
`Start` → service task `smoke.say-hello` (input `name`, output `greeting`) → `End`.

## Cách chạy
1. `cd infra/camunda-8.10 && docker compose up -d`
2. `cd workers && ./gradlew bootRun`
3. Tạo instance:
   ```bash
   curl -X POST http://localhost:8080/v2/process-instances \
     -H 'Content-Type: application/json' \
     -d '{"processDefinitionId":"smoke-test","variables":{"name":"Anh"}}'
   ```
4. Mở Operate (http://localhost:8080, demo/demo): instance đã completed, có biến `greeting`.

Hoặc chỉ chạy `./gradlew test` trong `workers/` (Testcontainers tự dựng engine).
