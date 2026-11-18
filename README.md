# BPM Lab

Lab nghiên cứu BPM (BPMN, DMN, Camunda 8) qua việc xây hệ thống quy trình cho một **doanh nghiệp mẫu**:
dựng lại các quy trình từng làm, rồi cải tiến dần lên mức cao hơn.

## Doanh nghiệp mẫu

> TODO: mô tả ngắn (ngành, quy mô, phòng ban, hệ thống liên quan). Gợi ý từ luồng 01: công ty chứng khoán.

## Danh sách quy trình

| # | Quy trình | Folder | Trạng thái | Mức |
|---|-----------|--------|------------|-----|
| 00 | Smoke test (kiểm tra stack) | `processes/00-smoke-test` | done | 1 |
| 00 | Hello approval – đề nghị hạn mức: duyệt, BPMN error vs incident, SLA timer | `processes/00-hello-approval` | v3 (đang học mức 2) | 2 |
| 01 | Điều chỉnh hạn mức Margin (online S24/M24, 4–9 cấp duyệt) | – | đã phân tích AS-IS/TO-BE, chưa build | – |

Mỗi quy trình có `NOTES.md`: các version, đổi gì và vì sao.
Thang mức: (1) chạy được → (2) xử lý ngoại lệ → (3) model theo nghiệp vụ → (4) kiến trúc & vận hành.

## Stack

| Thành phần | Version | Ghi chú |
|------------|---------|---------|
| Camunda 8 Self-Managed | 8.10.9 | Docker Compose lightweight, chỉ chạy `orchestration` (không Connectors), H2 |
| Java | 25 (LTS) | Toolchain trong `build.gradle` |
| Spring Boot | 4.1.x | |
| `camunda-spring-boot-starter` | 8.10.9 | Job worker (chỉ nhận job, **không deploy**) |
| `camunda-process-test-spring` | 8.10.9 | Test quy trình (cần Docker) |
| Gradle (Groovy DSL, wrapper) | 9.9.0 | Build `workers/` |
| Camunda Desktop Modeler | 5.53+ | Vẽ BPMN/DMN/Form, **deploy tay** |

Chính sách version: luôn dùng bản GA mới nhất, không alpha/RC. Lý do chọn: `docs/adr/0001-stack.md`.

## Cấu trúc

```
bpm-lab/
├── infra/camunda-8.10/   # Docker Compose chính thức của Camunda; chỉnh riêng nằm trong docker-compose.override.yaml
├── processes/<NN-tên>/   # .bpmn, .dmn, .form + NOTES.md — chỉ là nơi lưu model
├── workers/              # Spring Boot app: job worker (@JobWorker) + test
└── docs/
    ├── adr/              # quyết định kiến trúc (vì sao chọn A thay vì B)
    └── knowledge.md      # sổ kiến thức: ngày · chủ đề · ý chính · bẫy
```

## Chạy nhanh

```bash
# 1. Bật Camunda (chỉ engine, không Connectors)
cd infra/camunda-8.10 && docker compose up -d orchestration
#    Operate/Tasklist: http://localhost:8080  (demo / demo)

# 2. Deploy model: mở file trong processes/ bằng Desktop Modeler → Deploy
#    Connection: Self-Managed, http://localhost:8080/v2, auth None

# 3. Chạy worker (chỉ nhận job)
cd workers && ./gradlew bootRun

# 4. Test quy trình (Testcontainers, không cần bước 1)
cd workers && ./gradlew test
```

Lưu ý:
- Deploy tay từ 15/11 (đã gỡ `@Deployment` khỏi worker). Chiến lược deploy chính thức còn mở → ADR 0002.
- `SmokeTestProcessTest` đang `@Disabled` vì engine test chưa có model; bật lại khi chốt cách deploy.
- Version **file** (git) khác version **process** (engine): sửa thẳng `hello-approval.bpmn`, không tạo `-v2.bpmn`;
  engine tự tăng version khi deploy nội dung mới.

## Git

Mô hình **git-flow** (classic) bằng [git-flow-next](https://github.com/gittower/git-flow-next):
`main` = bản đã chốt, `develop` = đang làm, nhánh `feature/<tên>` cho từng bài.

```bash
brew install git-flow-next
git flow init --preset=classic --defaults   # chạy lại trên mỗi máy: cấu hình nằm trong .git/config, không push
git flow feature start mdc                  # ... commit ...
git flow feature finish mdc                 # merge về develop
```

Khi sửa model: cập nhật `NOTES.md` của quy trình trong cùng commit (diff BPMN là XML, không đọc được "vì sao").

Chi tiết: `infra/README.md`, `processes/README.md`.
