# Infra

`camunda-8.10/` là bản Docker Compose chính thức của Camunda
(https://github.com/camunda/camunda-distributions, release `docker-compose-8.10`), đã bỏ thư mục test Playwright.
Không sửa file compose gốc; mọi chỉnh riêng của lab nằm trong `docker-compose.override.yaml`.
Khi nâng version: tải bản mới vào folder `camunda-8.xx/` mới, giữ bản cũ đến khi chạy ổn.

## Hai cấu hình

| File | Gồm | RAM | Khi nào dùng |
|------|-----|-----|--------------|
| `docker-compose.yaml` (mặc định) | Orchestration (Zeebe + Operate + Tasklist, REST/gRPC) + Connectors, H2 | ~1.5 GB | Hằng ngày: model, chạy, debug |
| `docker-compose-full.yaml` | + Elasticsearch, Optimize, Identity, Keycloak, Hub (Web Modeler), Postgres | 8 GB+ | Khi học Optimize, Identity/RBAC, multi-tenancy |

```bash
cd infra/camunda-8.10
docker compose up -d orchestration   # mặc định của lab: chỉ engine, không Connectors (tích hợp viết bằng Java worker)
docker compose up -d                 # lightweight đầy đủ (+ Connectors) khi cần học REST connector / inbound webhook
docker compose logs -f orchestration
docker compose down           # dừng, giữ data
docker compose down -v        # dừng và xoá sạch data
```

## Chỉnh riêng của lab (`docker-compose.override.yaml`)

| Cấu hình | Giá trị | Mặc định Camunda | Vì sao |
|----------|---------|------------------|--------|
| `camunda.data.secondary-storage.rdbms.history.default-history-ttl` | `P365D` | `P30D` | Xem lại instance đã kết thúc trong Operate đến 1 năm. Lưu vết nghiệp vụ lâu dài vẫn phải nằm ở bảng riêng, không dựa vào Operate. |

Kiểm tra sau khi start: `docker compose exec orchestration env | grep HISTORY`

## Endpoint (lightweight)

| Dịch vụ | URL |
|---------|-----|
| Operate / Tasklist | http://localhost:8080 (user `demo` / `demo`) |
| REST API (Orchestration Cluster API) | http://localhost:8080/v2 |
| gRPC (Zeebe) | localhost:26500 |
| Health / metrics | http://localhost:9600/actuator/health |
| Connectors | http://localhost:8086 |

Desktop Modeler: deploy tới **Camunda 8 Self-Managed**, cluster endpoint `http://localhost:8080`, auth `None`.

## Secrets

- Cách mới (8.10): tạo file trong `secrets/` (tên file = tên secret, nội dung = giá trị), dùng `=camunda.secrets.TEN_SECRET` trong FEEL.
- Cách cũ cho Connectors: `connector-secrets.txt` + `{{secrets.TEN}}`.
- `secrets/` đã nằm trong `.gitignore`; `connector-secrets.txt` thì không (compose cần file này tồn tại), nên chỉ để giá trị giả trong đó. Không bao giờ đưa secret vào BPMN.
