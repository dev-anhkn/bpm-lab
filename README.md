# BPM Lab

Lab nghiên cứu BPM (BPMN, DMN, Camunda 8) qua việc xây hệ thống quy trình cho một **doanh nghiệp mẫu**:
dựng lại các quy trình từng làm, rồi cải tiến dần lên mức cao hơn.

## Doanh nghiệp mẫu

> TODO: mô tả ngắn (ngành, quy mô, phòng ban, hệ thống liên quan).

## Danh sách quy trình

| # | Quy trình | Folder | Trạng thái | Mức hiện tại |
|---|-----------|--------|------------|--------------|
| 00 | Smoke test (kiểm tra stack) | `processes/00-smoke-test` | done | - |
| 01 | TODO | | | |

## Stack

| Thành phần | Version | Ghi chú |
|------------|---------|---------|
| Camunda 8 Self-Managed | 8.10.9 | Docker Compose lightweight (Orchestration + Connectors, H2) |
| Java | 25 (LTS) | Toolchain trong `build.gradle` |
| Spring Boot | 4.1.x | |
| `camunda-spring-boot-starter` | 8.10.9 | Job worker + auto deploy |
| `camunda-process-test-spring` | 8.10.9 | Test quy trình (cần Docker) |
| Gradle (Groovy DSL, wrapper) | 9.9.0 | Build `workers/` |
| Camunda Desktop Modeler | mới nhất | Vẽ BPMN/DMN/Form |

Lý do chọn: xem `docs/adr/0001-stack.md`.

## Cấu trúc

```
bpm-lab/
├── infra/camunda-8.10/   # bản Docker Compose chính thức của Camunda (không sửa trực tiếp, chỉ chỉnh .env)
├── processes/<NN-tên>/    # .bpmn, .dmn, .form + NOTES.md (v1 → v2 đổi gì, vì sao)
├── workers/               # Spring Boot app: job worker + test, tự deploy mọi file trong processes/
└── docs/adr/              # các quyết định kiến trúc (vì sao chọn A thay vì B)
```

## Chạy nhanh

```bash
# 1. Bật Camunda
cd infra/camunda-8.10 && docker compose up -d orchestration   # không cần Connectors
#    Operate/Tasklist: http://localhost:8080  (demo / demo)

# 2. Chạy worker (tự deploy BPMN trong processes/)
cd workers && ./gradlew bootRun

# 3. Test quy trình (Testcontainers, không cần bước 1)
cd workers && ./gradlew test
```

Chi tiết: `infra/README.md`, `processes/README.md`.
