# Processes

Mỗi quy trình một folder `NN-ten-quy-trinh/`:

```
NN-ten-quy-trinh/
├── ten-quy-trinh.bpmn
├── *.dmn            # nếu có
├── *.form           # nếu có user task dùng Camunda Form
└── NOTES.md         # mục tiêu, các version, đổi gì và vì sao
```

Folder này chỉ là nơi lưu model. **Deploy tay bằng Desktop Modeler** (từ 15/11): `workers/` không còn copy file vào
classpath và không tự deploy, worker chỉ nhận job. Chiến lược deploy chính thức còn mở (xem ADR 0002 khi chốt).

Lưu ý khi deploy tay từng file: form binding `deployment` không dùng được (BPMN và form phải cùng một deployment),
nên user task đang dùng binding `latest`.

## Quy ước đặt tên

| Thứ | Quy ước | Ví dụ |
|-----|---------|-------|
| Process ID | kebab-case, ổn định, không đổi giữa các version | `leave-request` |
| Job type | `<process>.<hành-động>` | `leave-request.notify-manager` |
| Message name | `<Sự kiện>` dạng PascalCase | `LeaveCancelled` |
| Error code | `UPPER_SNAKE` | `INSUFFICIENT_BALANCE` |
| Variable | camelCase | `requestId`, `approvedDays` |
| Element name | Động từ + danh từ (task), trạng thái (event) | "Duyệt đơn", "Đơn đã huỷ" |

Vì sao: Process ID và job type là "hợp đồng" giữa model và code. Đổi tên tuỳ tiện sẽ làm instance cũ
không có worker nhận job, hoặc message không correlate được.

## Versioning

Không tạo file `-v2.bpmn`. Sửa thẳng file, Zeebe tự tăng version khi deploy.
Ghi lại thay đổi trong `NOTES.md`; cần so sánh thì dùng git diff / git tag.

## Mẫu NOTES.md

```markdown
# <Tên quy trình>

## Bối cảnh nghiệp vụ
Ai khởi tạo, ai tham gia, kết quả mong muốn, SLA.

## Version
### v1 – dựng lại bản gốc
- Mô tả ngắn, hạn chế đã biết.

### v2 – <cải tiến gì>
- Vấn đề của v1:
- Thay đổi:
- Vì sao chọn cách này (và phương án đã loại):

## Câu hỏi mở / ý tưởng tiếp
```
