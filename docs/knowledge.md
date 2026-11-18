# Sổ kiến thức BPM Lab

Chỉ ghi điều đã **hiểu chắc** (khi user bảo "note lại"). Mỗi mục: ngày · chủ đề · ý chính · bẫy.

---

## 11–15/11 · Nền tảng

- **Pull model:** engine tạo job, worker tự đến lấy. Không có worker → job nằm chờ, không báo lỗi.
- Modeler và worker không nói chuyện trực tiếp, đều đi qua engine. Nút Run của Modeler chạy trên engine thật.
- Từ 8.8: Zeebe + Operate + Tasklist + REST = **Orchestration Cluster** (1 container), REST `/v2`, package `io.camunda.client`.
- `@JobWorker` trả `Map` → tự complete + merge biến. `@Variable` chỉ fetch biến khai báo.
- Deploy: Zeebe so tên + nội dung với bản mới nhất. Từ 8.6, một deployment nhiều file: **một file đổi → mọi file lên version**.
- **Primary** (Zeebe log + RocksDB) = engine chạy. **Secondary** (H2/Postgres/ES) = Operate, Tasklist, search API → có độ trễ.
- History TTL (mặc định 30 ngày) xóa lịch sử Operate → **lưu vết nghiệp vụ ở bảng riêng**. Process state ở Camunda, business data ở DB.
- CPT (`@CamundaSpringProcessTest`) dựng engine riêng bằng Docker mỗi lần test.

## 15/11 · Job type

- 3 loại id: **element id** (`Task_ApplyLimit` – test, migration, Operate) · **process id** (`hello-approval` – start, version) · **job type** (`hello-approval.apply-limit` – worker nhận job).
- Worker chỉ lọc theo job type, không theo process id. Nhiều worker cùng type = chia tải, không phải routing.
- Job type là namespace chung → `<process>.<action>`; việc dùng chung thì theo domain (`notification.send-email`).
- **Bẫy:** type trong BPMN lệch worker → không lỗi, không incident, instance đứng im. Form id trùng → thành version của form khác.

## 15/11 · User task & công cụ

- Modeler = start · Tasklist = làm task · Operate = giám sát/vận hành.
- User task là **wait state**: engine dừng chờ, không tốn tài nguyên. 8.10 hiển thị wait states → phân biệt chờ đúng thiết kế với bị kẹt.
- Deploy tay từng file → không dùng được form binding `deployment`.

## 15/11 · Retry & incident

- Method throw → SDK gửi **fail** (retries − 1) → engine trả job về hàng chờ → method chạy lại từ đầu.
- `retries="3"` = tổng 3 lần thử. Về 0 → **incident**. Sửa nguyên nhân → **Retry** trong Operate.
- Retries lưu ở engine (restart worker không reset). Lần sau có thể rơi vào worker khác → worker không giữ state, phải **idempotent**.
- Worker chết giữa chừng → chờ hết **job timeout** rồi giao lại, không trừ retries.

## 16/11 · Lỗi nghiệp vụ vs kỹ thuật · Error boundary · Backoff

| Tình huống | Worker | Đường đi | Ai xử lý |
|---|---|---|---|
| Hệ thống ngoài chập chờn | exception / `CamundaError.jobError` | retry → **incident** | Vận hành |
| Nghiệp vụ không cho phép | `CamundaError.bpmnError("CODE", msg)` | **error boundary** | Quy trình |
| BPMN error không ai bắt | `bpmnError` | **incident** | Vận hành (model thiếu nhánh) |

- Lỗi nghiệp vụ không retry (dữ liệu không đổi thì vẫn sai). Nhánh lỗi không nhất thiết là end.
- Error boundary bắt theo error code, luôn interrupting, tìm ngược ra ngoài theo scope. Để trống code = bắt mọi lỗi → nên đặt cụ thể.
- Exception thường = fail job, **không** vào boundary. Không có compiler kiểm tra code worker ↔ BPMN → ghi trong NOTES + test.
- Incident không phải instance chết: vẫn active, biến còn → sửa → Retry hoặc Cancel.
- **Backoff:** `jobError(msg, vars, retries - 1, Duration.ofSeconds(10))` – engine giữ job 10s, phải tự trừ retries. Không backoff → 3 lần hết trong < 1s.
- Quy tắc: số lần thử × backoff > thời gian sự cố thường gặp. Muốn tăng dần thì worker tự tính theo retries còn lại.

## 17/11 · Timer boundary · Token · SLA

- Timer boundary = khai báo SLA trong model thay cho cron: engine lưu timer, task xong tự hủy, Operate ghi lại.
- **Token** = vị trí instance trên sơ đồ; có thể nhiều token; instance kết thúc khi hết token → end event nhánh phụ chỉ kết thúc token phụ.
- Timer đang chờ không phải token. Camunda 8 lưu **element instance** (số đếm trên Operate).

| | Non-interrupting | Interrupting |
|---|---|---|
| Hình / XML | nét đứt / `cancelActivity="false"` | nét liền / mặc định |
| Task gốc | còn, sinh thêm token | bị hủy, token đổi đường |
| Dùng khi | *làm thêm* (nhắc, báo cấp trên) | *đổi hướng* (duyệt thay, hủy, hết hạn) |

**Bẫy**
- Nhánh non-interrupting phải tự kết thúc, không nhập lại luồng chính (duyệt/apply 2 lần).
- Timer sống theo task: task xong → mọi timer trên task bị hủy.
- Interrupting bắn đúng lúc user bấm duyệt → lệnh nào tới trước thắng; web duyệt phải bắt lỗi "task không còn".
- ISO 8601: `PT2M` (2 phút) · `R/PT2M` (lặp) · **`P1M` = 1 tháng, `PT1M` = 1 phút**. Mốc tính từ lúc vào task.
- BPMN **không có SLA**: SLA = timer (phản ứng) + `dueAt` theo lịch làm việc (tính hạn) + bảng nghiệp vụ (đo). `dueDate` chỉ để hiển thị. Escalation event ≠ "báo cấp trên".
- Escalate là câu hỏi nghiệp vụ: đôn đốc / chuyển quyền / thêm cấp trên vào cùng task.

## 18/11 · Service task vs execution listener

- Execution listener (8.6+): job chạy `start` (trước) / `end` (sau) element, **chặn luồng** tới khi complete.
- Chọn: nghiệp vụ coi là một bước (nhắc, báo cấp trên) → **service task** (thấy, đếm, ném BPMN error được). Việc kỹ thuật đi kèm (audit, tính biến) → **listener**.
- Listener **không ném được BPMN error** → chỉ retry → incident. Boundary event chỉ có end listener; gateway không có end listener.
- Expression lỗi → sửa xong, listener cùng loại chạy lại → phải idempotent.
- **Bẫy:** listener trên đường interrupting bị incident → task cũ đã hủy, task mới chưa tạo → không ai giữ hồ sơ.
- **Chưa kiểm chứng:** end listener có chạy khi element bị interrupting hủy không (bài L3).
- Bài thử (mức 2, trên v3): **L1** end listener trên `Timer_ApprovalExpired`, so hiển thị Operate · **L2** listener lỗi → kẹt hồ sơ → Retry · **L3** end listener trên `Task_Approve`, chạy K1/K2 · **L4** listener ném `bpmnError` → incident.
- Để sau (mức 3–4): start listener tính `dueAt`; ghi `approval_step` qua listener; task listener.

## 18/11 · Git cho lab

- **Version file ≠ version process:** git đếm lần lưu file, engine đếm lần deploy nội dung mới. Không tạo `-v2.bpmn`, sửa thẳng một file.
- Diff BPMN là XML, khó đọc → "vì sao" ghi trong `NOTES.md`, cùng commit với model.
- `git init <tên>` = tạo repo trong thư mục `<tên>`. `git flow init` mới là git-flow. Repo lồng repo → `git add .` ghi gitlink (lỗi nếu repo con chưa có commit).
- `git status` trước `git add .`.
- git-flow-next, preset classic: `main` + `develop` + `feature/`. Cấu hình và hook nằm trong `.git/` → không push, máy khác phải init lại.
- **Lỡ commit thông tin nhạy cảm:** commit sửa không đủ (còn trong lịch sử) → `commit --amend` + `push --force-with-lease`. Force-push không xóa ngay trên GitHub (vẫn mở được qua SHA). Chặn bằng pre-commit hook.
- **Bẫy:** không đưa tên hệ thống công ty vào repo (README, NOTES, sổ này, message commit).
