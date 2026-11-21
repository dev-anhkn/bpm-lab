# hello-approval – Đề nghị hạn mức (bài khởi động)

## Bối cảnh nghiệp vụ
- Khởi tạo: có đề nghị hạn mức cho khách hàng (biến `customerId`, `requestedLimit`).
- Tham gia: MG duyệt (user task, form `hello-approval-form`, candidateUsers `demo`); cấp trên duyệt thay khi MG quá hạn (từ v3).
- Kết quả: APPROVE → cập nhật hạn mức (job `hello-approval.apply-limit`); REJECT → kết thúc "Bị từ chối".
- SLA (demo, rút ngắn để thử): nhắc mỗi 2', báo cấp trên sau 3', hết hạn sau 5'.

Process id `hello-approval` giữ nguyên qua mọi version. Mỗi version là một file riêng
`hello-approval-v1.bpmn` … `hello-approval-v4.bpmn` (21/11 quay lại cách tách file, bỏ file gộp `hello-approval.bpmn`).
Deploy file nào thì version đó thành bản mới nhất trên engine.

## Version

### v1 – luồng tối thiểu (15/11)
- Start → `Task_Approve` (MG duyệt) → `Gateway_Decision` (`decision` APPROVE/REJECT) → `Task_ApplyLimit` → end.
- Bài a (lỗi kỹ thuật): cờ `simulateFailure` → worker ném exception → retries giảm (3 → 0) → incident trong Operate.
- Hạn chế: lỗi nghiệp vụ (vượt ngưỡng) cũng chỉ có thể thành incident; MG không duyệt thì token nằm chờ mãi.

### v2 – tách lỗi nghiệp vụ khỏi lỗi kỹ thuật (16/11)
- Vấn đề của v1: "vượt ngưỡng" là kết quả nghiệp vụ hợp lệ, retry bao nhiêu lần cũng vậy → incident là sai chỗ.
- Thay đổi:
  - Error `LIMIT_EXCEEDED` + error boundary `Boundary_LimitExceeded` trên `Task_ApplyLimit` → `End_LimitExceeded`;
    `errorMessageVariable = limitErrorMessage`. Worker ném `CamundaError.bpmnError(...)` khi `requestedLimit` > 1 tỷ.
  - Lỗi kỹ thuật thêm backoff: `CamundaError.jobError(..., retries - 1, 10s)` – engine chờ 10s mới cho lấy lại job.
- Vì sao: lỗi nghiệp vụ → model phải có nhánh xử lý (BPMN error); lỗi kỹ thuật → retry rồi incident cho vận hành.
  Không dùng biến kết quả + gateway vì phải thêm gateway sau mọi service task có thể lỗi, và lỗi bị lẫn với kết quả bình thường.

### v3 – SLA cho bước duyệt (17/11)
- Vấn đề của v2: không có gì xảy ra nếu MG không duyệt.
- Thay đổi, đều là boundary timer trên `Task_Approve`:
  - `Timer_RemindApproval` `R/PT2M`, non-interrupting → `Task_RemindApproval` (job `hello-approval.remind`) → `End_Reminded`.
  - `Timer_EscalateApproval` `PT3M`, non-interrupting → `Task_EscalateApproval` (job `hello-approval.escalate`) → `End_Escalated`.
  - `Timer_ApprovalExpired` `PT5M`, interrupting → hủy task MG → `Task_ApproveBySupervisor` (cùng form)
    → `Gateway_MergeApproval` → `Gateway_Decision`.
- Vì sao:
  - Timer gắn vào task (không dùng cron ngoài): timer sống và chết theo task, task xong thì timer tự hủy, không cần dọn.
  - Nhắc/báo cấp trên dùng non-interrupting vì task MG vẫn phải còn; nhánh phụ có end event riêng – end event ấy chỉ
    kết thúc token của nhánh đó, không kết thúc instance.
  - Hết hạn thì giao cấp trên duyệt thay, không end luồng: end khi quá hạn là sai thực tế (đề nghị bị bỏ không ai xử lý).
- Đã kiểm chứng trên engine: K2 (chờ 5' → cấp trên duyệt thay), K3 (MG duyệt ở phút 4, timer hết hạn bị hủy).
- Hạn chế đã biết:
  - `Task_ApproveBySupervisor` chưa có SLA.
  - Chưa ghi lưu vết (`approval_step`) khi hết hạn / ai duyệt thay.
  - Sau escalate chưa báo "đã xử lý" cho cấp trên.
  - Web duyệt (thay Tasklist) phải bắt lỗi "task không còn" khi timer hết hạn bắn trước lệnh complete.

## Câu hỏi mở / ý tưởng tiếp
- Error event sub-process + message hủy đề nghị (khách rút đơn ở bất kỳ bước nào).
- Worker idempotent + job timeout (job `apply-limit` chạy lại không được cập nhật hạn mức hai lần).
- SLA thật: `dueAt` tính theo business calendar (worker tính, timer đọc biến) thay vì hằng số trong model.
- Các câu hỏi nghiệp vụ về escalate (chuyển quyền hay chỉ đôn đốc, quá hạn lần 2 thì sao) – xem luồng 01.
