# Sổ kiến thức BPM Lab

Ghi những gì đã **hiểu chắc** (chỉ ghi khi user bảo "note lại").
Mỗi mục: ngày · chủ đề · ý chính · bẫy hay gặp. Review hằng tuần: tóm tắt tuần + câu hỏi tự kiểm tra.

---

## 2026-11-11 → 11-15 · Nền tảng (giai đoạn smoke-test)

**Mô hình hoạt động**
- **Pull model:** engine chỉ tạo job, worker tự đến lấy. Không có worker → job nằm chờ, engine không báo lỗi.
- Modeler và worker **không nói chuyện trực tiếp**, cả hai đi qua engine. "localhost" = container Docker nhờ port mapping.
- Nút Run trong Modeler chạy trên **engine thật** (Docker) + worker local, không phải giả lập.
- Từ 8.8: Zeebe + Operate + Tasklist + REST gộp thành **Orchestration Cluster** (1 container), REST `/v2`, client package `io.camunda.client`.

**Worker (Spring starter)**
- `@JobWorker` trả `Map` → mặc định **tự complete job** + merge biến vào process (autoComplete = true).
- `@Variable` chỉ **fetch đúng biến khai báo**, không kéo toàn bộ biến process.

**Deploy & version**
- Zeebe so **tên + nội dung** với version mới nhất: file không đổi → không lên version.
- Từ 8.6: một deployment nhiều file, **một file đổi → mọi file cùng lên version** → lý do tách deploy khỏi worker.

**Storage & dữ liệu**
- **Primary storage** (Zeebe log + RocksDB) = nơi engine chạy. **Secondary storage** (H2/Postgres/ES) = cho Operate, Tasklist, search API.
- Operate chỉ đọc secondary storage → **có độ trễ**; mặc định chỉ hiện instance đang chạy.
- **History TTL** (RDBMS mặc định 30 ngày): quá hạn → lịch sử trong Operate bị xóa → dấu vết nghiệp vụ phải lưu bảng riêng. (Lab đặt P365D.)
- No-secondary-storage mode → **mất Operate, Tasklist, search API**.
- Nguyên tắc: **process state ở Camunda, business data ở bảng riêng**; process chỉ giữ ID + biến cần cho routing.

**Test**
- Camunda Process Test (`@CamundaSpringProcessTest`) dựng **engine riêng bằng Docker** mỗi lần chạy, độc lập engine local.

## 2026-11-15 · Job type & cách worker nhận job

- Có 3 loại id tách biệt:
  - **Element id** (`Task_ApplyLimit`): định danh phần tử trong BPMN. Dùng cho test, migration, Operate. Đặt có nghĩa, không đổi tùy tiện.
  - **Process id** (`hello-approval`): start instance + quản lý version.
  - **Job type** (`hello-approval.apply-limit`): chuỗi để worker nhận job.
- Worker **chỉ lọc theo job type** (+ tenant), **không lọc được theo process id**, dù tách bao nhiêu worker/app.
- Nhiều worker cùng type = **competing consumers** (chia tải), không phải routing. Giống consumer của cùng một queue.
- Check `job.getBpmnProcessId()` trong code để lọc là anti-pattern: lúc đó job đã bị lock.
- Job type là namespace chung toàn engine → đặt `<process>.<action>` để tránh trùng. Logic dùng chung thì cố ý dùng chung type theo domain (`notification.send-email`). Các version của cùng process dùng chung type là đúng ý.
- Dấu `.` chỉ là quy ước tách namespace/action (vì process id có `-`); engine coi type là chuỗi bất kỳ.
- **Bẫy:** type trong BPMN và worker lệch nhau → không lỗi, không incident, instance đứng im ở service task.
- Form id cũng là namespace chung (resource có version): trùng id → thành version mới của form khác, binding `latest` lấy nhầm form.

## 2026-11-15 · User task & công cụ

- Modeler = start instance · Tasklist = làm user task · Operate = giám sát/vận hành (không start instance, không complete task).
- User task là **wait state**: engine dừng chờ người, không tốn tài nguyên.
- Camunda 8.10 hiển thị **wait states** (user task, job chờ activation/đang xử lý, timer, message…) → phân biệt chờ đúng thiết kế với instance bị kẹt.
- Form: ô read-only hiển thị biến truyền lúc start; dữ liệu chỉ xem thì Text view hợp hơn.
- Nhánh REJECT không chạm worker; chỉ nhánh APPROVE chạy service task.
- Deploy tay từng file → không dùng được form binding `deployment` (cần BPMN + form cùng một lần deploy).

## 2026-11-15 · Retry & incident (lỗi kỹ thuật)

- Hiểu đơn giản: **Camunda tự retry**, không cần tự viết vòng lặp thử lại.
- Thực chất: method throw → **SDK tự gửi lệnh fail** (retries − 1) → **engine** đưa job về hàng chờ → vòng lặp polling của SDK lấy lại → method chạy lại từ đầu.
- `retries="3"` trong BPMN = **tổng 3 lần thử** (không phải 1 + 3). Về 0 → engine tạo **incident**, dừng giao job.
- Xử lý incident: sửa nguyên nhân (code hoặc biến) trong Operate → bấm **Retry** → engine cấp lại retries và giao job tiếp.
- Worker pull job (hỏi và lấy về), engine không đẩy job đi.
- Cần nhớ:
  - Số retries lưu ở **engine** → restart worker không mất/không reset.
  - Lần thử lại có thể rơi vào **worker khác** → worker không giữ state giữa các lần.
  - Mỗi lần thử chạy **lại toàn bộ method** → bước đã làm (gọi BO) có thể bị làm lại → cần **idempotent**.
  - Worker chết giữa chừng (không kịp fail) → engine chờ hết **job timeout** rồi giao lại, **không trừ** retries.
- Mặc định không có backoff giữa các lần thử → 3 lần hết trong chưa tới 1 giây. (Câu hỏi mở: cấu hình backoff thế nào.)

## 2026-11-16 · Hai loại lỗi: nghiệp vụ vs kỹ thuật · Error boundary · Backoff

**Nguyên tắc:** mỗi loại lỗi có một đường đi riêng, **worker là bên chọn đường**.

| Tình huống | Worker làm | Đường đi | Ai xử lý |
|---|---|---|---|
| Hệ thống ngoài chập chờn (timeout, mất mạng) | exception / `CamundaError.jobError(...)` | retry (có backoff) → hết lượt → **incident** | Người vận hành (Operate) |
| Nghiệp vụ không cho phép (vượt hạn mức) | `CamundaError.bpmnError("LIMIT_EXCEEDED", msg)` | **error boundary** → nhánh đã vẽ sẵn | Chính quy trình |
| Ném BPMN error mà model không bắt | `bpmnError` | **incident** "not caught… No error events are available in the scope" | Người vận hành (model thiếu nhánh) |

**Lỗi nghiệp vụ (BPMN error)**
- Không retry: dữ liệu không đổi thì thử bao nhiêu lần cũng sai → gọi 1 lần rồi đi nhánh khác.
- Nhánh lỗi **không nhất thiết là end**: có thể kết thúc, chuyển người khác xử lý (cấp cao duyệt, task vận hành), hoặc quay lại bước trước.
- Chỉ đi vào boundary khi engine nhận lệnh **throw error**: từ worker dùng `CamundaError.bpmnError` (bên dưới là `newThrowErrorCommand`); hoặc error end event trong sub-process. Exception thường = lệnh **fail job** → không vào boundary.
- Giống `throw`/`catch` trong Java nhưng **không có compiler kiểm tra** worker ↔ BPMN khớp nhau → ghi "hợp đồng" error code trong NOTES + test nhánh lỗi.

**Error boundary event**
- Ký hiệu: vòng tròn đôi (sự kiện giữa luồng) + tia sét (error), gắn trên viền task (chỉ nghe khi task đang chạy).
- Bắt theo **error code** khớp; luôn **interrupting** (task bị hủy, token đi theo mũi tên ra).
- Để trống error code = bắt mọi BPMN error (như `catch (Exception e)`) → nên đặt code cụ thể.
- `bpmn:error` khai báo riêng ở cấp definitions (dùng chung được); `errorMessageVariable` lưu message lỗi vào biến cho nhánh sau dùng.
- Engine tìm boundary/event sub-process **đi ngược ra ngoài theo scope** (task → sub-process → process).

**Lỗi kỹ thuật & incident**
- Retries về 0 → incident `JOB_NO_RETRIES`, message = chuỗi worker gửi.
- Incident **không phải instance chết**: vẫn active, đứng tại task, biến còn nguyên → sửa nguyên nhân (code hoặc biến) → **Retry** trong Operate, hoặc Cancel.

**Backoff**
- = khoảng **chờ trước khi thử lại** (như máy bận thì đợi một lúc mới gọi lại).
- `CamundaError.jobError(msg, vars, job.getRetries() - 1, Duration.ofSeconds(10))`: engine giữ job 10 giây, không giao cho worker nào; **tự trừ retries** (khác exception thường: SDK tự trừ, backoff = 0).
- Không backoff → 3 lần thử dồn trong < 1 giây, vô ích khi hệ thống bên kia đang khởi động/quá tải.
- Quy tắc: **số lần thử × backoff > thời gian sự cố thường gặp** của hệ thống bên kia (vd core restart 30s → 5 lần × 10s).
- Kiểu: cố định (10s, 10s…) hoặc tăng dần/exponential (10s, 20s, 40s…). Camunda nhận 1 con số mỗi lần fail → muốn tăng dần thì worker tự tính theo retries còn lại.
- Áp vào luồng 01: BO timeout → retry + backoff → incident; BO từ chối → BPMN error `BO_REJECTED` → task vận hành. (Câu hỏi mở: BO thường down bao lâu, nhất là lúc EOD?)

## 2026-11-17 · Timer boundary: interrupting vs non-interrupting · Token · SLA

**Vì sao cần:** user task có thể chờ mãi → cần SLA. Boundary timer = **khai báo SLA trong model** thay vì tự viết `@Scheduled` cron (tự quét DB, tự nhớ đã nhắc chưa, tự dừng khi xong, lo chạy trùng nhiều node). Engine lưu timer, restart không mất, task xong thì tự hủy timer, Operate ghi lại lúc bắn.

**Token**
- Sequence flow = con đường; **token** = "quân cờ" đánh dấu instance đang ở đâu (khái niệm của chuẩn BPMN, không riêng Camunda).
- 1 instance có thể có **nhiều token**; instance kết thúc khi **hết token** → end event của nhánh phụ chỉ kết thúc token phụ.
- Token chỉ đứng yên ở **wait state** (user task, service task chờ worker, timer, message). Thiếu end event → implicit end, token tự mất.
- Timer đang chờ **không phải token**, chỉ là hẹn giờ gắn với task; bắn mới sinh/chuyển token.
- Camunda 8 không lưu "token" mà lưu **element instance** → số đếm active/completed trên sơ đồ Operate.

| | Non-interrupting | Interrupting |
|---|---|---|
| Hình | viền đôi **nét đứt** | viền đôi **nét liền** |
| XML | `cancelActivity="false"` | mặc định (`true`) |
| Token gốc | ở lại task | bị rút khỏi task |
| Token mới | sinh thêm | không, token gốc đổi đường |
| Timer khác trên task | vẫn chạy | bị hủy hết |
| Dùng khi | việc đang làm **còn giá trị** → *làm thêm* (nhắc, báo cấp trên) | **hết giá trị** → *đổi hướng* (chuyển người duyệt, hủy, hết hạn OTP) |

**Bẫy**
- Nhánh non-interrupting phải **tự kết thúc**, không nhập lại luồng chính (token phụ chạy tiếp phần còn lại → duyệt/apply 2 lần).
- Dùng sai loại: nhắc mà interrupting → hồ sơ kết thúc không có quyết định; hết hạn mà non-interrupting → 2 người cùng duyệt.
- **Timer sống theo task**: task xong (MG duyệt phút 4) → mọi timer trên task bị hủy, phút 5 không có gì xảy ra.
- Việc đã làm không rút lại được (đã báo cấp trên thì engine không tự báo "đã xử lý").
- Cycle `R/…` chỉ có nghĩa với non-interrupting.
- Interrupting bắn → phần MG gõ dở trên form mất; MG bấm đúng lúc timer bắn → lệnh tới engine trước thì thắng, web duyệt phải bắt lỗi "task không còn".
- ISO 8601: `PT2M` duration · `R3/PT2M` / `R/PT2M` cycle · `2026-11-18T09:00:00+07:00` date. **`P1M` = 1 tháng, `PT1M` = 1 phút.** Mốc tính từ lúc token vào task.

**SLA:** BPMN **không có khái niệm SLA**, chỉ có viên gạch. SLA = phản ứng (timer) + tính hạn (worker/DMN theo lịch làm việc → `dueAt`) + đo/báo cáo (bảng nghiệp vụ, vì Operate xóa theo TTL). `dueDate` trên user task chỉ để hiển thị, engine không làm gì khi quá hạn. Escalation event của BPMN ≠ "báo cấp trên" (là cơ chế sub-process báo lên cha).

**Escalate là câu hỏi nghiệp vụ:** chỉ đôn đốc / chuyển quyền / thêm cấp trên vào cùng task (1 task → không thể duyệt 2 lần).

**Đã làm:** `hello-approval-v3.bpmn` — nhắc `R/PT2M` + báo cấp trên `PT3M` (non-interrupting), hết hạn `PT5M` (interrupting) → "Cấp trên duyệt thay" → gộp nhánh → gateway. Đã chạy K2 (chờ 5') và K3 (duyệt phút 4).

## 2026-11-18 · Service task vs execution listener cho việc đi kèm

**Execution listener (Camunda 8.6+):** job gắn vào vòng đời một element — `start` chạy **trước** khi element được xử lý, `end` chạy **sau** khi xong. Worker vẫn là `@JobWorker` như thường. Listener **chặn luồng**: element chỉ đi tiếp khi listener complete.

| | Service task | Execution listener |
|---|---|---|
| Sơ đồ | thấy rõ một bước | ẩn trong properties panel |
| Ý nghĩa | "đây là **một việc** của quy trình" | "đây là **việc đi kèm** element" |
| Lỗi nghiệp vụ | ném BPMN error, gắn boundary được | **không** ném BPMN error được → chỉ retry → incident |
| Operate | có ô, có số đếm | không thành bước riêng → khó đếm |

**Giới hạn cần nhớ (docs 8.10)**
- Boundary event: **không có start listener**, chỉ có **end listener** (logic "start" đặt lên start listener của activity được gắn).
- Gateway: không có end listener → đặt start listener lên element tiếp theo. Start event: dùng listener của process/sub-process.
- Lỗi khi tính expression → sau khi sửa, **mọi listener cùng loại đã chạy trước đó chạy lại** → worker listener phải idempotent.

**Cách chọn:** "Nghiệp vụ có coi đây là một bước không?"
- Có (nhắc duyệt, báo cấp trên, thông báo KH) → **service task**: SLA là cam kết nghiệp vụ, phải thấy và đếm được.
- Không, là việc kỹ thuật đi kèm (ghi audit, đồng bộ bảng, tính biến cho timer/gateway) → **listener**: giữ sơ đồ gọn.

**Bẫy:** listener trên đường interrupting bị incident → task MG đã bị hủy nhưng task cấp trên chưa tạo → **không ai giữ hồ sơ** cho tới khi xử lý incident.

**Chưa kiểm chứng (Claude hiểu, docs chưa nói rõ):** end listener chỉ chạy khi element **hoàn thành**, không chạy khi bị hủy bởi interrupting → đừng gắn end listener lên "MG duyệt" để bắt lúc hết hạn.

**Bài thử để thấy khác biệt (mức 2, làm trên v3)**
- Bài L1 — so sánh hiển thị: thêm **end listener** `hello-approval.mark-expired` lên `Timer_ApprovalExpired` (worker chỉ log "approval_step EXPIRED"). Chạy K2. *Xong khi:* log có dòng mark-expired; trong Operate "Nhắc duyệt" có ô + số đếm, còn listener không có ô riêng.
- Bài L2 — listener lỗi làm kẹt hồ sơ: cho worker `mark-expired` ném exception (cờ `simulateListenerFailure`), retries hết. *Xong khi:* phút 5 task MG mất, task cấp trên **chưa có**, Operate báo incident; sửa cờ → Retry → task cấp trên xuất hiện.
- Bài L3 — kiểm chứng điều chưa chắc: thêm end listener (log) lên `Task_Approve`. Chạy K1 (duyệt sớm) và K2 (để hết hạn). *Xong khi:* biết listener có chạy khi task bị hủy hay không → ghi kết quả vào đây.
- Bài L4 (tùy chọn): listener ném `CamundaError.bpmnError` → xác nhận ra incident chứ không vào boundary.

**Để sau (mức 3–4)**
- Start listener trên user task tính `remindAt`/`dueAt` theo business calendar → timer dùng biến (pattern SLA luồng 01).
- Ghi `approval_step` thật vào DB qua listener + idempotent khi chạy lại.
- Task listener của user task (sự kiện giao việc / hoàn thành) — khác execution listener, tìm hiểu riêng.
