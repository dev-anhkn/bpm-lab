# ADR 0001 – Chọn stack cho BPM Lab

- Ngày: 2026-11-09
- Trạng thái: accepted

## Bối cảnh
Lab dùng để học sâu BPM trên Camunda 8, chạy local trên MacBook (Apple Silicon), một người dùng.

## Quyết định

1. **Camunda 8.10 Self-Managed qua Docker Compose (bản lightweight, H2)**, không dùng SaaS.
   - Có thể xem log, config, data của engine; reset tuỳ ý; không phụ thuộc trial SaaS.
   - Từ 8.8, Zeebe + Operate + Tasklist gộp thành **Orchestration Cluster** (1 container, REST API `/v2`).
     Dùng bản mới nhất để học kiến trúc hiện tại, không học lại kiến trúc tách rời của 8.5–8.7.
   - H2 thay vì Elasticsearch: nhẹ (~1.5 GB RAM), đủ cho Operate/Tasklist. Bật `docker-compose-full.yaml` khi cần Optimize/Identity.
2. **Dùng nguyên bản compose chính thức** thay vì tự viết: config 8.10 thay đổi nhiều (secondary storage, secrets, mount YAML),
   tự viết dễ lệch. Nâng version = tải bản mới.
3. **Model tách khỏi code** (`processes/` vs `workers/`): model là thứ chính để học/review;
   Gradle (`processResources`) copy model vào classpath lúc build để worker auto-deploy.
4. **Spring Boot 4.1 + `camunda-spring-boot-starter` 8.10** (Java 25 LTS, máy user đã có sẵn; starter hỗ trợ JDK 17+, server Camunda hỗ trợ JDK 21–25): đúng bộ Camunda dùng để build 8.10.
5. **Camunda Process Test (CPT)** để test quy trình bằng JUnit thay vì test tay trên Operate.

6. **Chính sách version: luôn dùng bản GA (stable) mới nhất** của Camunda và Spring Boot, không dùng alpha/RC/milestone.
   - Kiểm tra tại thời điểm 2026-11-09: Camunda **8.10.9** (mới nhất là 8.11.0-alpha1, chưa GA), Spring Boot **4.1.2** (4.2.0-RC2 chưa GA).
   - Vì sao không dùng alpha/RC: lỗi của bản pre-release lẫn với lỗi của chính mình, mất thời gian debug sai chỗ;
     docs và ví dụ thường chưa theo kịp.
   - Khi có minor mới (Camunda ra tháng 4 và tháng 10, Spring Boot ra tháng 5 và tháng 11): nâng sau khi có bản patch đầu tiên,
     và kiểm tra `camunda-spring-boot-starter` đã hỗ trợ Spring Boot mới chưa (xem `version.spring-boot` trong `parent/pom.xml` của repo camunda/camunda).

7. **Gradle 9.9 (Groovy DSL, có wrapper) thay cho Maven** (user chọn Gradle 2026-11-10, đổi sang Groovy DSL 2026-11-12).
   - Dùng `platform(SpringBootPlugin.BOM_COORDINATES)` thay cho plugin `io.spring.dependency-management`: ít plugin hơn, là cách native của Gradle.
   - Groovy DSL (`build.gradle`, `settings.gradle`): user quen cú pháp này hơn; đây cũng vẫn là mặc định của start.spring.io
     và là dạng gặp nhiều nhất trong các dự án Java Spring Boot dùng Gradle.
   - Ban đầu (2026-11-10) dùng Kotlin DSL vì IDE gợi ý và kiểm tra kiểu tốt hơn, và là mặc định của `gradle init`.
     Đổi lại vì với lab một người, sự quen thuộc quan trọng hơn. Hai bản cho ra cùng dependency và cùng file jar.

## Phương án đã loại
| Phương án | Lý do loại |
|-----------|-----------|
| Camunda SaaS | Hết trial là mất môi trường, không xem được nội bộ engine |
| Camunda 8.7 trở xuống (giống công ty?) | Kiến trúc cũ, sắp/đã hết maintenance; khác biệt với công ty ghi vào NOTES khi gặp |
| Kubernetes (KIND + Helm) | Nặng và chưa cần; để dành khi học vận hành/scale |
| Spring Boot 3.5 + `camunda-spring-boot-3-starter` | Chỉ cần nếu muốn khớp code công ty |

## Hệ quả
- Cần Docker Desktop chạy cả khi `./gradlew test` (CPT dùng Testcontainers).
- Nếu công ty đang ở 8.5–8.7: API client (`io.camunda.client.*`, REST `/v2`) khác với `io.camunda.zeebe.client.*` cũ.

## Ghi chú: triển khai lên server (cài tay + systemd)
Kiểm tra docs 2026-11-09 (Camunda 8.10):
- Manual installation trên Linux VM/bare metal **được hỗ trợ**, docs có sẵn mẫu `camunda.service` và `camunda-connectors.service` (systemd, đọc log bằng `journalctl`).
- Cài tay được: **Orchestration Cluster** (`camunda-zeebe-8.10.x.tar.gz`) và **Connectors** (`connector-runtime-bundle-8.10.x-with-dependencies.jar`). Hai thành phần phải cùng minor.
- Không cài tay được: Management Identity, Optimize, Camunda Hub/Web Modeler (phải dùng Docker/Helm). Thay bằng Desktop Modeler.
- JDK 21–25. Dùng distro glibc (Ubuntu/Debian), tránh Alpine (musl chậm hơn).
- Secondary storage: PostgreSQL 15–18 (driver có sẵn) hoặc Elasticsearch 8.19+/9.4+. **H2 chỉ dùng cho dev/test.**
- Production self-managed cần license key (`CAMUNDA_LICENSE_KEY`).
- Hệ quả cho lab: để môi trường local giống server, chuyển local từ H2 sang PostgreSQL (`ORCHESTRATION_CONFIG_FILE=application-postgresql.yaml`).
