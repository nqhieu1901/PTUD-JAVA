# Sao lưu và khôi phục SmartTask

SmartTask lưu một bộ dữ liệu hoàn chỉnh ở **hai nơi**: MySQL và thư mục tệp đính kèm. Hai nơi này phải luôn được sao lưu và khôi phục cùng nhau.

Bộ công cụ trong `ops/` dành cho Windows PowerShell 5.1 trở lên và MySQL 8:

- `Backup-SmartTask.ps1`: tạo một snapshot mới gồm `database.sql`, `attachments/` và `manifest.json`.
- `Restore-SmartTask.ps1`: mặc định chỉ kiểm tra; chỉ ghi dữ liệu khi có đủ ba cờ xác nhận.
- `Test-SmartTaskBackup.ps1`: drill tích hợp trên hai database thử nghiệm có tên ngẫu nhiên.

## Nguyên tắc an toàn

- Dừng **tất cả** backend SmartTask và mọi chương trình có thể ghi vào database hoặc thư mục đính kèm trước khi sao lưu hay khôi phục thật. Cờ `-BackendStopped` là lời xác nhận của người vận hành; kiểm tra cổng 8080 chỉ là lớp bảo vệ bổ sung.
- Mật khẩu MySQL được nhập bằng `PSCredential`, ghi tạm vào option file có ACL chỉ dành cho người dùng hiện tại, rồi bị xóa. Mật khẩu không nằm trong dòng lệnh, manifest hoặc log lỗi.
- Mỗi tệp trong snapshot có độ dài và SHA-256 trong manifest. Restore từ chối tệp thiếu, thừa, đổi nội dung, link/junction và đường dẫn không an toàn.
- Restore không bao giờ ghi vào database hoặc thư mục đính kèm đã tồn tại. Tên database đích phải mới, khác database nguồn, chỉ gồm chữ thường, chữ số và dấu gạch dưới, đồng thời không được là database hệ thống.
- Script không chạy `DROP DATABASE` và không tự dọn một lần restore lỗi. Dữ liệu dở dang được giữ lại để điều tra; lần thử sau phải dùng database cùng thư mục mới.
- Manifest bảo đảm tính toàn vẹn, không chứng minh nguồn gốc. Chỉ dùng `-TrustedBackup` cho snapshot do bạn kiểm soát. Các lệnh SQL chuyển/tạo/xóa database, truy cập tệp máy chủ hoặc thay đổi cấu hình máy chủ bị từ chối; vẫn nên dùng tài khoản MySQL có quyền tối thiểu cần thiết.

Snapshot chứa dữ liệu cá nhân và các hash mật khẩu lấy từ database. Hãy lưu snapshot ở ổ đĩa được mã hóa, hạn chế quyền truy cập và không đưa vào Git. Công cụ không đưa mật khẩu kết nối MySQL hay file cấu hình ứng dụng vào snapshot; tệp người dùng tải lên vẫn được sao lưu nguyên trạng.

## 1. Tạo bản sao

Ví dụ với Laragon và database `smart_task`:

```powershell
$mysqlBin = 'C:\laragon\bin\mysql\mysql-8.4.3-winx64\bin'
$credential = Get-Credential -UserName 'root'

.\ops\Backup-SmartTask.ps1 `
  -Database 'smart_task' `
  -AttachmentPath 'C:\smarttask-data\attachments' `
  -BackupRoot 'D:\smarttask-backups' `
  -MySqlBin $mysqlBin `
  -Credential $credential `
  -BackendStopped
```

`AttachmentPath` phải là thư mục thật đang được cấu hình bởi `ATTACHMENT_STORAGE_PATH`, kể cả khi thư mục đang rỗng. `BackupRoot` phải ở ngoài thư mục dữ liệu sống. Nếu tài khoản MySQL phát triển thực sự không có mật khẩu, có thể dùng `-Username root -EmptyPassword` thay cho `-Credential`; không dùng cách đó trên máy thật.

Script dùng dump một transaction, không thêm lệnh xóa bảng/database, sao chép tệp đính kèm và kiểm tra lại cây nguồn. Nếu nguồn thay đổi trong lúc chạy, snapshot không có manifest hoàn chỉnh và không thể restore.

## 2. Kiểm tra restore (mặc định, không ghi dữ liệu)

Chọn một database và một thư mục **chưa tồn tại**:

```powershell
$snapshot = 'D:\smarttask-backups\20260907T120000Z-a1b2c3d4'

.\ops\Restore-SmartTask.ps1 `
  -BackupPath $snapshot `
  -TargetDatabase 'smart_task_restore_20260907' `
  -TargetAttachmentPath 'C:\smarttask-data\restore-20260907\attachments' `
  -MySqlBin $mysqlBin `
  -Credential $credential
```

Kết quả `DRY RUN OK` nghĩa là manifest, tất cả checksum, phạm vi SQL và việc database đích chưa tồn tại đã được xác nhận. Chưa có database hay thư mục đích nào được tạo.

## 3. Thực hiện restore

Sau khi đọc và tin cậy snapshot, dừng mọi backend/writer rồi chạy lại đúng lệnh với ba cờ:

```powershell
.\ops\Restore-SmartTask.ps1 `
  -BackupPath $snapshot `
  -TargetDatabase 'smart_task_restore_20260907' `
  -TargetAttachmentPath 'C:\smarttask-data\restore-20260907\attachments' `
  -MySqlBin $mysqlBin `
  -Credential $credential `
  -Execute `
  -TrustedBackup `
  -BackendStopped
```

Chỉ sau thông báo `Restore complete` mới cấu hình **đồng thời**:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/smart_task_restore_20260907?serverTimezone=UTC'
$env:ATTACHMENT_STORAGE_PATH = 'C:\smarttask-data\restore-20260907\attachments'
```

Khởi động backend và kiểm tra đăng nhập, dự án, task, tệp tải xuống trước khi coi đây là bản phục hồi hợp lệ. Không trỏ ứng dụng vào một database hoặc thư mục bị giữ lại sau lỗi.

## 4. Drill phục hồi cô lập

Drill tạo hai database `smarttask_ops_src_<ngẫu nhiên>` và `smarttask_ops_dst_<ngẫu nhiên>`, kiểm tra Unicode, trigger, tệp nhị phân, dry-run, chống ghi đè và chống sửa snapshot:

```powershell
.\ops\Test-SmartTaskBackup.ps1 `
  -MySqlBin $mysqlBin `
  -TestRoot 'C:\smarttask-restore-drills' `
  -Credential $credential `
  -RunIntegration `
  -BackendStopped
```

Drill cố ý giữ lại cả hai database và thư mục thử nghiệm, đồng thời cố ý làm hỏng snapshot sau khi đã kiểm tra restore thành công. Tên và đường dẫn chính xác được in trước khi tạo; hãy rà lại chúng trước khi tự dọn. Script không bao giờ tự xóa database.
