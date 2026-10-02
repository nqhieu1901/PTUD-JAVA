# Smart Task

Website quản lý công việc gồm hai ứng dụng độc lập:

- `backend/`: REST API Spring Boot, Spring Security, Spring Data JPA và MySQL.
- `frontend/`: giao diện React chạy bằng Vite.

## Chức năng hiện có

- Đăng ký, đăng nhập bằng email hoặc Google, chỉnh sửa hồ sơ, đổi mật khẩu,
  quên/đặt lại mật khẩu qua liên kết một lần, xác minh email, phiên cookie và CSRF.
  Token khôi phục chỉ được lưu dưới dạng SHA-256, có hạn dùng, bị xoay khi gửi lại;
  đổi hoặc đặt lại mật khẩu làm mất hiệu lực mọi phiên và liên kết khôi phục cũ. Xác minh
  email hiện cung cấp trạng thái tin cậy nhưng không chặn người dùng vào workspace.
  Tài khoản Google được liên kết bằng định danh ổn định của
  Google và chỉ dùng email đã được xác minh. Không tự ghép Google vào tài khoản
  mật khẩu trùng email. Sau khi đăng nhập bằng mật khẩu, người dùng có thể
  liên kết đúng tài khoản Google có cùng email đã xác minh hoặc hủy liên kết;
  cả hai thao tác đều yêu cầu nhập lại mật khẩu và thu hồi các phiên cũ.
  Yêu cầu liên kết được giữ phía server, gắn với phiên/người dùng/phiên bản
  thông tin xác thực, chỉ dùng một lần và mặc định hết hạn sau 5 phút.
  Tài khoản Google-only mới không có chức năng đăng nhập/đổi mật khẩu nội bộ.
  Mật khẩu nội bộ được kiểm tra giới hạn 72 byte UTF-8 trước khi dùng BCrypt.
- Quản trị tài khoản hệ thống: Admin được tìm kiếm, phân trang, khóa và mở khóa
  người dùng; tài khoản bị khóa mất quyền truy cập ngay cả khi còn phiên cũ.
  Admin dùng workspace quản trị riêng và không được truy cập dữ liệu dự án hoặc
  thông báo nghiệp vụ.
- Tạo, sửa, đóng và mở lại dự án; thêm, gỡ và đổi quyền thành viên.
  Không thêm Admin hoặc tài khoản bị khóa vào dự án; không phân công task cho Admin.
- Ba vai trò dự án: `OWNER`, `MEMBER` và `VIEWER`; Viewer chỉ được xem và không thể được giao task.
- Tạo, sửa, xóa, phân công và chia task cha–con.
- Bình luận theo task; Member/Owner được trao đổi, tác giả hoặc Owner được xóa bình luận.
- Tệp đính kèm theo task; Member/Owner được tải lên, mọi thành viên được tải xuống,
  người tải lên hoặc Owner được xóa. Mỗi tệp tối đa 10 MB. File được dọn sau khi
  transaction xóa thành công; xóa task cũng dọn toàn bộ tệp liên quan và backend
  đối soát định kỳ để loại metadata hỏng hoặc file mồ côi.
- Kanban kéo-thả có quy trình riêng theo từng dự án và lưu `boardOrder`; Owner được quản lý 2–7 cột, đổi tên, sắp xếp và chọn cột hoàn tất.
- Calendar theo deadline, mở chi tiết task và mở rộng ngày có nhiều công việc;
  Dashboard tiến độ theo dự án.
- Thông báo trong ứng dụng khi được thêm vào dự án, được giao task, task sắp/quá hạn hoặc đổi trạng thái; có thể mở trực tiếp task từ thông báo.
  Deadline được quét nền mỗi phút, chống trùng theo từng mốc hạn và được nhắc lại
  khi đổi deadline. Giao diện đồng bộ thông báo và danh sách dự án khi tab đang hiển thị;
  thông báo thêm thành viên mở được dự án mới mà không cần đăng nhập lại.
- Trợ lý chia nhỏ task với luồng tạo gợi ý, chấp nhận hoặc từ chối; hỗ trợ
  provider `DEMO`, OpenAI và Groq Responses API.
  Lời gọi AI không giữ transaction/khóa dự án trong thời gian chờ nhà cung cấp;
  yêu cầu thất bại vẫn lưu lịch sử. Quyền người dùng và trạng thái dự án/task được
  kiểm tra lại trước khi lưu kết quả. Chấp nhận gợi ý kiểm tra người nhận task
  và phát thông báo phân công như thao tác tạo task thông thường.
- Giới hạn tần suất theo cửa sổ trượt cho đăng nhập/đăng ký, email tài khoản,
  thao tác nhập lại mật khẩu và AI; đồng thời giới hạn số lời gọi AI đang chạy
  trên mỗi tài khoản. Phản hồi
  `429` có `Retry-After`; CSRF vẫn được kiểm tra trước khi tính lượt.
- Bộ công cụ PowerShell sao lưu/khôi phục offline đồng bộ database MySQL và thư
  mục tệp đính kèm, có manifest SHA-256, dry-run và các chốt chống ghi đè.

Provider `DEMO` là mặc định để dự án chạy local mà không cần khóa API. Khi bật
provider `OPENAI`, backend dùng Structured Outputs để nhận đúng cấu trúc gợi ý,
không lưu Response ở phía nhà cung cấp (`store=false`) và vẫn giữ nguyên bước
Owner rà soát trước khi tạo task thật.

## Cấu trúc dự án

```text
.
├── backend/
│   ├── src/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
├── frontend/
│   ├── src/
│   ├── package.json
│   ├── package-lock.json
│   └── vite.config.js
├── ops/                    # backup/restore MySQL + attachments
├── docs/
│   └── BACKUP_RESTORE.md
└── README.md
```

## Yêu cầu môi trường

- Java 21
- Node.js và npm
- MySQL đang chạy tại `localhost:3306`, hoặc một MySQL server khác được cấu hình qua biến môi trường

Backend mặc định kết nối tới database `smart_task` bằng tài khoản `root` và mật khẩu rỗng. Tài khoản MySQL cần có quyền tạo database nếu `smart_task` chưa tồn tại.

## Cấu hình database

Các biến môi trường được backend hỗ trợ:

| Biến | Giá trị mặc định | Ý nghĩa |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/smart_task?createDatabaseIfNotExist=true&serverTimezone=UTC` | JDBC URL của MySQL |
| `DB_USERNAME` | `root` | Tên đăng nhập MySQL |
| `DB_PASSWORD` | rỗng | Mật khẩu MySQL |
| `SESSION_COOKIE_SECURE` | `false` | Cho phép cookie phiên chỉ đi qua HTTPS; profile `prod` bắt buộc đặt `true` |
| `ATTACHMENT_STORAGE_PATH` | `./data/attachments` | Thư mục lưu nội dung tệp đính kèm |
| `ATTACHMENT_CLEANUP_SCAN_MS` | `3600000` | Chu kỳ đối soát database và thư mục tệp |
| `ATTACHMENT_CLEANUP_INITIAL_DELAY_MS` | `300000` | Thời gian chờ trước lần đối soát đầu tiên |
| `ATTACHMENT_ORPHAN_GRACE_MS` | `3600000` | Tuổi tối thiểu trước khi một file không có metadata được xem là mồ côi |
| `NOTIFICATION_DEADLINE_SCAN_MS` | `60000` | Chu kỳ quét deadline ở backend, tính bằng mili giây |
| `NOTIFICATION_DEADLINE_INITIAL_DELAY_MS` | `60000` | Thời gian chờ trước lần quét deadline đầu tiên |
| `SMARTTASK_ADMIN_EMAIL` | rỗng | Email Admin mới khi khởi động; từ chối nâng quyền USER trùng email |
| `SMARTTASK_ADMIN_PASSWORD` | rỗng | Mật khẩu ban đầu, tối thiểu 8 ký tự và tối đa 72 byte UTF-8; chỉ dùng khi tạo mới Admin |
| `SMARTTASK_ADMIN_FULL_NAME` | `Quản trị viên SmartTask` | Tên hiển thị của Admin được tạo mới |
| `GOOGLE_OAUTH_ENABLED` | `false` | Bật đăng nhập Google khi đặt thành `true` |
| `GOOGLE_CLIENT_ID` | rỗng | OAuth 2.0 Client ID của ứng dụng Web trên Google Cloud |
| `GOOGLE_CLIENT_SECRET` | rỗng | OAuth 2.0 Client Secret; chỉ lưu ở môi trường backend |
| `GOOGLE_REDIRECT_URI` | `http://localhost:8080/login/oauth2/code/google` | Redirect URI backend nhận callback từ Google |
| `GOOGLE_LOGIN_SUCCESS_URL` | `http://localhost:5173/` | URL frontend sau khi đăng nhập thành công |
| `GOOGLE_LOGIN_FAILURE_URL` | `http://localhost:5173/?authError=google` | URL frontend khi Google từ chối hoặc đăng nhập thất bại |
| `GOOGLE_LINK_INTENT_TTL_SECONDS` | `300` | Hạn dùng yêu cầu liên kết Google một lần; cho phép 30–900 giây |
| `AI_PROVIDER` | `DEMO` | Chọn `DEMO`, `OPENAI` hoặc `GROQ` để tạo gợi ý chia nhỏ task |
| `OPENAI_API_KEY` | rỗng | API key bắt buộc khi `AI_PROVIDER=OPENAI`; không lưu khóa vào source code |
| `OPENAI_MODEL` | `gpt-5-mini` | Model OpenAI dùng cho Structured Outputs |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Base URL của OpenAI Responses API |
| `OPENAI_TIMEOUT_MS` | `30000` | Thời gian chờ tối đa cho một lần gọi AI |
| `GROQ_API_KEY` | rỗng | API key bắt buộc khi `AI_PROVIDER=GROQ`; không lưu khóa vào source code |
| `GROQ_MODEL` | `openai/gpt-oss-20b` | Model Groq hỗ trợ Structured Outputs nghiêm ngặt |
| `GROQ_BASE_URL` | `https://api.groq.com/openai/v1` | Base URL của Groq Responses API |
| `GROQ_TIMEOUT_MS` | `30000` | Thời gian chờ tối đa cho một lần gọi Groq |
| `SMARTTASK_RATE_LIMIT_ENABLED` | `true` | Bật giới hạn tần suất cục bộ trong mỗi tiến trình backend |
| `SMARTTASK_AUTH_RATE_LIMIT` | `10` | Tổng lượt đăng nhập/đăng ký mỗi IP trong một phút |
| `SMARTTASK_EMAIL_RATE_LIMIT` | `5` | Tổng lượt yêu cầu email tài khoản mỗi IP trong 15 phút |
| `SMARTTASK_REAUTH_RATE_LIMIT` | `5` | Tổng lượt đổi mật khẩu/liên kết/hủy Google mỗi tài khoản trong 15 phút |
| `SMARTTASK_AI_RATE_LIMIT` | `5` | Lượt tạo gợi ý AI mỗi tài khoản trong một phút |
| `SMARTTASK_AI_CONCURRENT_LIMIT` | `2` | Số lời gọi AI được chạy đồng thời trên mỗi tài khoản |
| `SMARTTASK_MAIL_MODE` | `DISABLED` | Chọn `DISABLED`, `FILE` hoặc `SMTP`; profile `local` mặc định dùng `FILE` |
| `SMARTTASK_MAIL_FROM` | `no-reply@smarttask.local` | Địa chỉ người gửi email tài khoản |
| `SMARTTASK_MAIL_OUTBOX` | `data/mail-outbox` | Thư mục `.eml` riêng tư khi dùng chế độ `FILE` |
| `SMARTTASK_FRONTEND_URL` | `http://localhost:5173/` | URL gốc để tạo liên kết xác minh/đặt lại; production bắt buộc dùng HTTPS |
| `SMARTTASK_RESET_TOKEN_TTL_MINUTES` | `30` | Hạn dùng liên kết đặt lại mật khẩu |
| `SMARTTASK_VERIFY_TOKEN_TTL_MINUTES` | `1440` | Hạn dùng liên kết xác minh email |
| `SMARTTASK_MAIL_RESEND_COOLDOWN_SECONDS` | `60` | Khoảng nghỉ trước khi xoay và gửi lại cùng loại liên kết |
| `SMARTTASK_MAIL_WORKER_COUNT` | `2` | Số worker gửi email tài khoản |
| `SMARTTASK_MAIL_QUEUE_CAPACITY` | `100` | Số email chờ tối đa trong tiến trình backend |
| `SMTP_HOST`, `SMTP_PORT` | rỗng, `587` | Máy chủ và cổng SMTP khi dùng chế độ `SMTP` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | rỗng | Thông tin SMTP; chỉ đặt ở môi trường backend hoặc tệp secrets bị bỏ qua |

Ví dụ cấu hình trong PowerShell trước khi chạy backend:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/smart_task?createDatabaseIfNotExist=true&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-password"
```

Nếu các giá trị mặc định phù hợp với máy local thì không cần khai báo các biến trên.

### Bật đăng nhập bằng Google

Trong Google Cloud Console, tạo OAuth 2.0 Client loại **Web application** và thêm
chính xác Authorized redirect URI sau:

```text
http://localhost:8080/login/oauth2/code/google
```

Khai báo cấu hình trong đúng terminal sẽ chạy backend:

```powershell
$env:GOOGLE_OAUTH_ENABLED = "true"
$env:GOOGLE_CLIENT_ID = "your-google-client-id"
$env:GOOGLE_CLIENT_SECRET = "your-google-client-secret"
```

Sau đó build và khởi động lại backend, đồng thời mở frontend bằng
`http://localhost:5173`. Nút Google sẽ ở trạng thái tắt khi tính năng chưa được
cấu hình. Cùng redirect URI phục vụ cả đăng nhập và liên kết, vì vậy
không cần khai báo callback Google Cloud thứ hai. Tài khoản
mật khẩu quản lý liên kết trong **Cài đặt tài khoản** và phải chọn Google
có email trùng khớp. Backend bỏ OAuth access/refresh token khỏi phiên ngay sau
khi xác minh danh tính vì ứng dụng không gọi API Google. Hủy liên kết chỉ gỡ
phương thức đăng nhập khỏi SmartTask; người dùng có thể thu hồi quyền đã cấp ở
Google Account nếu muốn. Không đưa Client Secret vào Git hoặc mã frontend.

Có thể cấu hình riêng trên máy trong
`backend/config/application-secrets.properties`. Tệp này được import khi chạy
từ thư mục gốc hoặc `backend/`, đã loại khỏi Git và không được đóng vào JAR.
Ví dụ (chỉ dùng giá trị giả dưới đây làm mẫu):

```properties
smarttask.oauth.google.enabled=${GOOGLE_OAUTH_ENABLED:true}
smarttask.oauth.google.client-id=${GOOGLE_CLIENT_ID:your-google-client-id}
smarttask.oauth.google.client-secret=${GOOGLE_CLIENT_SECRET:your-google-client-secret}
```

Cú pháp `${TEN_BIEN:gia-tri-mac-dinh}` tra biến môi trường rồi dùng giá trị sau
dấu `:` nếu chưa khai báo. Không đặt khóa thật thành tên biến như `${khoa-that}`.
Nếu chia sẻ cả thư mục/ZIP để nộp bài, phải loại tệp bí mật này và dữ liệu local;
`.gitignore` không bảo vệ thao tác gửi ZIP. Thay các khóa đã lộ trong chat hoặc log.

### Bật mô hình AI thật

Khai báo cấu hình trong đúng terminal sẽ chạy backend:

```powershell
$env:AI_PROVIDER = "OPENAI"
$env:OPENAI_API_KEY = "your-api-key"
$env:OPENAI_MODEL = "gpt-5-mini"
```

Sau đó build và khởi động lại backend. Nếu `AI_PROVIDER=OPENAI` nhưng thiếu API
key, backend sẽ dừng ngay khi khởi động thay vì âm thầm dùng dữ liệu DEMO. Không
đưa `OPENAI_API_KEY` vào Git hoặc frontend; khóa chỉ được đọc ở backend.

Để dùng Groq, tạo một API key mới rồi cấu hình trong terminal chạy backend:

```powershell
$env:AI_PROVIDER = "GROQ"
$env:GROQ_API_KEY = "your-new-groq-api-key"
$env:GROQ_MODEL = "openai/gpt-oss-20b"
```

Không sử dụng lại khóa đã từng được gửi trong chat, log hoặc commit vào source code.

### Email xác minh và đặt lại mật khẩu

Profile `local` dùng `SMARTTASK_MAIL_MODE=FILE`: mỗi email thử được ghi thành một
tệp `.eml` trong `backend/data/mail-outbox/` khi chạy backend từ thư mục
`backend`. Thư mục này đã bị loại khỏi Git, không được phục vụ qua HTTP và chỉ
dùng để thử giao diện local.

Để gửi thật, đặt cấu hình trong môi trường backend hoặc
`backend/config/application-secrets.properties` rồi khởi động lại:

```powershell
$env:SMARTTASK_MAIL_MODE = "SMTP"
$env:SMARTTASK_MAIL_FROM = "no-reply@example.com"
$env:SMARTTASK_FRONTEND_URL = "https://app.example.com/"
$env:SMTP_HOST = "smtp.example.com"
$env:SMTP_PORT = "587"
$env:SMTP_USERNAME = "smtp-user"
$env:SMTP_PASSWORD = "smtp-secret"
```

Không đặt mật khẩu SMTP ở frontend hoặc commit vào source. Backend production
từ chối tạo liên kết tài khoản bằng URL HTTP không phải localhost. Email được
xếp hàng sau khi transaction thành công nên lỗi SMTP không hoàn tác việc đổi
mật khẩu; cần giám sát log cảnh báo và thử gửi trước khi triển khai.

### Nâng cấp database MySQL đã tồn tại

Flyway tự động quản lý schema bằng các migration trong
`backend/src/main/resources/db/migration/mysql/`. Database mới sẽ chạy lần lượt từ V1;
database phiên bản cũ chưa có lịch sử Flyway được baseline tại V1 rồi tự động áp dụng
V2–V10 để mở rộng thông báo, thêm vai trò Viewer, tạo bảng bình luận và tệp đính
kèm task, chuyển trạng thái thành quy trình riêng theo từng dự án và hỗ trợ liên
kết tài khoản Google, xác minh email và khôi phục tài khoản.

Nên sao lưu database MySQL trước lần khởi động đầu tiên sau khi cập nhật. Không cần
chạy file SQL thủ công; profile H2 `local` cũng dùng migration tương đương riêng.

V1 dành cho MySQL dùng trigger để cấm task làm cha của chính nó, do MySQL 8.4
không cho CHECK tham chiếu cột AUTO_INCREMENT. H2 giữ nguyên V1 trong `common/`.
Nếu database cũ có V1 được ghi nhận bằng checksum của bản trước hoặc từng chạy
migration thất bại, cần kiểm tra schema/lịch sử trên bản sao trước khi nâng cấp.
Không tự xóa database, chỉnh `flyway_schema_history` hoặc chạy `repair` để bỏ qua lỗi.

### Sao lưu và khôi phục

SmartTask phải sao lưu database và thư mục `ATTACHMENT_STORAGE_PATH` thành cùng
một bộ. Các script trong `ops/` yêu cầu dừng toàn bộ backend/writer, tạo manifest
SHA-256 và mặc định restore ở chế độ dry-run vào database cùng thư mục mới. Xem
quy trình, câu lệnh và các chốt an toàn tại
[`docs/BACKUP_RESTORE.md`](docs/BACKUP_RESTORE.md).

## Chạy backend

Từ thư mục gốc của dự án:

```powershell
cd backend
.\mvnw.cmd clean package
java -jar target\smart-task-0.0.1-SNAPSHOT.jar
```

API chạy tại `http://localhost:8080`.

Trên Windows, lệnh `spring-boot:run` có thể tạo classpath sai khi đường dẫn dự án chứa ký tự Unicode. Quy trình `clean package` rồi `java -jar` ở trên là cách chạy ổn định cho vị trí hiện tại của dự án.

Nếu OneDrive/Windows không cho Maven dọn `target`, dừng backend đang chạy rồi
có thể build vào thư mục đầu ra mới, không cần xóa thư mục cũ:

```powershell
.\mvnw.cmd "-Dsmarttask.build-directory=target-validation" verify
java -jar target-validation\smart-task-0.0.1-SNAPSHOT.jar
```

Khi cần một bản build hoàn toàn mới, chọn tên `target-...` chưa tồn tại.

Để chạy thử nhanh mà không cần bật MySQL, dùng profile H2 local sau khi đã package:

```powershell
java -jar target\smart-task-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Profile `local` lưu dữ liệu nghiệp vụ trong bộ nhớ và sẽ xóa dữ liệu khi backend
dừng. Tệp tải lên vẫn nằm trong `data/attachments`, nên chỉ dùng profile này để
thử nhanh. Cách chạy mặc định phía trên dùng MySQL để lưu dữ liệu lâu dài.

Profile `local` tự tạo tài khoản quản trị để kiểm thử giao diện:

- Email: `admin@smarttask.local`
- Mật khẩu: `Admin@123`

Có thể thay hai giá trị này bằng `SMARTTASK_ADMIN_EMAIL` và
`SMARTTASK_ADMIN_PASSWORD`. Ở profile mặc định dùng MySQL, bootstrap Admin bị tắt
nếu không cấu hình đủ hai biến, vì vậy không có mật khẩu quản trị mặc định trong
môi trường lưu dữ liệu thật.

## Chạy frontend

Mở terminal thứ hai, trở lại thư mục gốc của dự án rồi chạy:

```powershell
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:5173` trong trình duyệt.

Trong môi trường phát triển, Vite chuyển tiếp các request `/api`, `/oauth2` và
`/login/oauth2` tới `http://localhost:8080`. Vì vậy backend phải được chạy trước
hoặc đồng thời với frontend. Frontend dùng đường dẫn tương đối để cookie phiên
đăng nhập và CSRF hoạt động cùng origin qua proxy phát triển.

## Kiểm thử và build

Kiểm thử backend:

```powershell
cd backend
.\mvnw.cmd clean verify
```

Kiểm thử frontend:

```powershell
cd frontend
npm ci
npm test
```

Bộ kiểm thử hiện gồm 204 test backend và 88 test frontend. Mặc định một test
MySQL thật được bỏ qua khi chưa khai báo server kiểm thử; các test còn lại dùng
H2, mock HTTP/provider hoặc kiểm thử hàm. Để chạy đủ cả kiểm tra MySQL:

```powershell
cd backend
$env:SMARTTASK_MYSQL_TEST_URL = "jdbc:mysql://localhost:3306/?serverTimezone=UTC"
$env:SMARTTASK_MYSQL_TEST_USERNAME = "root"
$env:SMARTTASK_MYSQL_TEST_PASSWORD = ""
.\mvnw.cmd clean verify
```

URL này trỏ tới server, không chứa tên database. Test tự tạo database có tiền tố
`smarttask_it_` và UUID, chạy V1–V10, chạy lại để kiểm tra không áp dụng trùng,
khởi động Spring Boot/JPA, kiểm tra khóa ngoại, Unicode và truy vấn nhắc hạn.
Database thử được xóa khi test kết thúc; không dùng hoặc xóa database `smart_task`.
Tài khoản kiểm thử cần quyền CREATE/DROP DATABASE và CREATE TRIGGER.

Tạo bản build production của frontend:

```powershell
cd frontend
npm run build
```

Kết quả build frontend được tạo trong `frontend/dist/`. Khi triển khai production,
cần cấu hình web server hoặc reverse proxy để phục vụ thư mục này và chuyển tiếp
`/api`, `/oauth2`, `/login/oauth2` tới backend, đồng thời đặt URL callback phù hợp.
Backend production phải chạy với profile `prod`, sau HTTPS và cookie phiên bảo mật:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:SESSION_COOKIE_SECURE = "true"
```

Nếu `SESSION_COOKIE_SECURE` chưa bật, profile `prod` sẽ từ chối khởi động để tránh
vô tình gửi `JSESSIONID` qua HTTP. Các URL frontend, callback Google và liên kết
trong email cũng phải dùng origin HTTPS thật của môi trường triển khai.

## Tình trạng kiểm chứng và giới hạn trước khi viết báo cáo

Đợt kiểm chứng ngày 08/09/2026: backend **204/204**, frontend **88/88** đạt;
build JAR và frontend production thành công. Test backend đầy đủ thực hiện trên
H2 và MySQL 8.4.3 với database thử có UUID; migration V1–V10, khởi động JPA,
khóa ngoại, Unicode và truy vấn deadline đều đạt. Do OneDrive từng khóa tạm một
file build, lượt xác nhận cuối được tạo trong thư mục output mới ngoài OneDrive;
JAR bàn giao sau đó được build tại `backend/target-release-20260908-final/`.

Drill backup/restore mới cũng đạt trên MySQL thật: dry-run không ghi dữ liệu,
Unicode, trigger và tệp nhị phân phục hồi đúng; snapshot bị sửa bị từ chối.
Smoke test HTTP qua Vite proxy xác nhận đăng ký tạo email xác minh local, đăng
nhập, xác minh, yêu cầu và dùng liên kết đặt lại, phiên cũ nhận `401`, rồi đăng
nhập lại bằng mật khẩu mới. Dữ liệu smoke dùng H2 `local`, không phải database
nghiệp vụ MySQL của người dùng.

Những tình huống đã bổ sung kiểm thử: Admin dưới React StrictMode; phản hồi chậm
sau khi chuyển dự án/đổi phiên; mở dự án mới từ thông báo; Calendar hơn ba task/ngày;
Google trùng email, tài khoản Google-only, guard chặn entry link công khai, xoay session,
liên kết/hủy liên kết có CSRF và nhập lại mật khẩu, email sai, subject xung
đột, hết hạn/replay intent, hủy consent, thu hồi phiên, redirect an toàn và rate
limit dùng chung; loại OAuth access token khỏi phiên sau callback và profile
production từ chối cookie không có cờ `Secure`; mật khẩu Unicode quá giới hạn BCrypt;
bootstrap Admin trùng USER; xóa cột chỉ còn task đã xóa mềm; nhắc lại khi đổi hạn;
AI thất bại được lưu, kiểm tra quyền sau thời gian chờ và kiểm tra người nhận khi
chấp nhận; token tài khoản một lần/hết hạn/sai mục đích, thu hồi phiên, gửi mail
sau commit, URL không làm lộ token, cùng rate limit sau CSRF và giới hạn AI đồng thời.

Không diễn giải kết quả trên thành hệ thống production đã hoàn thiện:

- Đăng nhập và liên kết Google thật từ consent đến callback chưa được kiểm thử
  E2E bằng tài khoản của người dùng trong đợt này; các ca tự động dùng MockMvc
  và provider giả lập. Cấu hình đọc được không chứng minh credentials/redirect đã
  hợp lệ trên Google Cloud.
- AI đang dùng `DEMO`; test provider dùng phản hồi giả lập, chưa xác nhận gọi
  OpenAI/Groq thật bằng khóa mới hoặc đánh giá chất lượng gợi ý trên dữ liệu thực.
- SMTP thật chưa được kiểm thử với nhà cung cấp và tên miền của người dùng; lượt
  smoke chỉ dùng hộp thư `.eml` local.
- Tài khoản Google đã tạo bởi phiên bản cũ giữ nguyên hash mật khẩu vì không thể
  tự phân biệt hash ngẫu nhiên với mật khẩu người dùng từng đặt; chính sách Google-only
  mới không tự chuyển đổi các tài khoản cũ.
- Chưa kiểm chứng tải lớn, vận hành nhiều instance, HTTPS/reverse proxy production
  và giám sát. Rate limiter hiện nằm trong từng process và reset khi backend khởi
  động lại; nhiều instance cần gateway hoặc shared store. Công cụ backup/restore
  đã có nhưng lịch chạy, mã hóa và lưu bản sao ngoài máy vẫn là việc vận hành.

Có thể bắt đầu báo cáo với phạm vi **MVP quản lý công việc cá nhân/nhóm**, mô tả
chức năng đã triển khai, bằng chứng kiểm thử và đưa các giới hạn trên vào phần
hạn chế/hướng phát triển. Không mô tả DEMO là mô hình AI thật.
