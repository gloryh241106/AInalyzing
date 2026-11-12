# Triển khai lên VPS (việc 10) và kiểm tra từ ngoài (việc 11)

Một VPS Ubuntu chạy Docker Compose gồm ba container: `db` (PostgreSQL 18.6, chỉ trong mạng nội bộ Docker, volume `ainalyzing_db-data`), `app` (Spring Boot, chứa sẵn bản build React, chỉ mở `127.0.0.1:8080`) và `caddy` (2.11.9, nhận HTTPS ở cổng 80/443 rồi chuyển tới `app:8080`). Flyway tạo bảng và nạp 4 ca demo khi app khởi động. Khóa Groq và mật khẩu DB chỉ nằm trong `.env` trên VPS.

```
Internet ──443──> caddy ──> app:8080 ──> db:5432
                              └──HTTPS──> api.groq.com
```

## 0. Cần có

- VPS Ubuntu 22.04 hoặc 24.04, tối thiểu 1 vCPU và 2 GB RAM (build Maven + Node trên VPS; nếu chỉ có 1 GB thì thêm swap ở bước 1).
- Quyền SSH với user có `sudo`.
- Một tên miền có bản ghi A trỏ về IP của VPS. Nếu chưa có tên miền, dùng `<ip-viết-bằng-gạch-ngang>.sslip.io` (ví dụ IP `203.0.113.10` → `203-0-113-10.sslip.io`); Caddy vẫn lấy được chứng chỉ Let's Encrypt cho tên này.
- Commit đã duyệt trên `main` (sau khi merge PR backend và PR deploy).

## 1. Chuẩn bị VPS (làm một lần)

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo apt-get install -y ca-certificates curl git python3
```

Cài Docker Engine và plugin Compose từ kho chính thức của Docker:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"     # đăng xuất rồi SSH lại để có quyền docker
docker --version && docker compose version
```

Tường lửa (Docker tự mở các cổng được publish; ở đây chỉ 80/443 công khai, app chỉ ở loopback, DB không publish):

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw enable
```

Nếu VPS chỉ có 1 GB RAM, thêm 2 GB swap để build không bị thiếu bộ nhớ:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Kiểm DNS trước khi tiếp tục (kết quả phải là IP của VPS):

```bash
getent hosts your-domain.example
```

## 2. Lấy mã và tạo `.env`

```bash
git clone https://github.com/gloryh241106/AInalyzing.git ~/ainalyzing
cd ~/ainalyzing
git checkout <commit-hoặc-tag-đã-duyệt>        # ví dụ v0.1.0
cp deploy/env.vps.example .env
chmod 600 .env
openssl rand -hex 24                            # dán kết quả vào DB_PASSWORD
nano .env                                       # điền DOMAIN, DB_PASSWORD, GROQ_API_KEY, APP_VERSION
```

Không đưa `.env` vào Git, ảnh chụp màn hình, slide hay log. Repo private hay public cũng vậy.

## 3. Build và chạy

```bash
docker compose config --quiet && echo "compose OK"
docker compose run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose up -d --build
docker compose ps
docker compose logs -f app        # chờ dòng "Started ApiApplication", Ctrl+C để thoát
```

Lần build đầu mất vài phút (tải image, npm, Maven). Log của app chỉ có mã lỗi, thời gian, số token và request id của Groq; không có câu hỏi, prompt, CSV hay khóa.

## 4. Kiểm tra (NFR-04, NFR-05, AC-07)

Trên VPS:

```bash
curl -s http://127.0.0.1:8080/api/health            # {"db":"UP","status":"UP"}
curl -sI https://$(grep ^DOMAIN= .env | cut -d= -f2)/api/health | head -1    # HTTP/2 200
```

Từ máy ngoài VPS (mạng khác), trong thư mục repo:

```bash
python3 scripts/smoke_test.py https://your-domain.example
```

Kết quả mong đợi: `RESULT: PASS (0 failed)` và dòng "groq answered N of 6". Mở URL bằng trình duyệt, kiểm ổ khóa HTTPS. Kiểm thêm từ ngoài rằng cổng 5432 và 8080 không truy cập được:

```bash
nc -vz -w 3 your-domain.example 5432; nc -vz -w 3 your-domain.example 8080   # cả hai phải thất bại
```

Ghi vào `docs/qa.md` (mục "Kết quả trên VPS"): URL, commit (`git rev-parse --short HEAD`), thời điểm, người chạy, người rà, kết quả smoke test.

Kiểm NFR-05 (khởi động lại vẫn chạy):

```bash
docker compose restart app db && sleep 20 && curl -s http://127.0.0.1:8080/api/health
```

## 5. Cập nhật phiên bản

```bash
cd ~/ainalyzing
git fetch --all --tags
git checkout <commit-hoặc-tag-mới>
sed -i "s/^APP_VERSION=.*/APP_VERSION=$(git rev-parse --short HEAD)/" .env
docker compose up -d --build app
```

Volume DB được giữ nguyên. Migration Flyway chỉ thêm (V1, V2, ...), không sửa file migration đã chạy.

## 6. Quay lại bản ổn định (rollback)

```bash
git checkout <tag-hoặc-commit-trước>     # ví dụ v0.1.0
sed -i "s/^APP_VERSION=.*/APP_VERSION=$(git rev-parse --short HEAD)/" .env
docker compose up -d --build app
curl -s http://127.0.0.1:8080/api/health
```

Image cũ vẫn còn trên VPS với tag `ainalyzing-app:<APP_VERSION>`; có thể sửa `APP_VERSION` về tag cũ rồi chạy `docker compose up -d --no-build app` để quay lại nhanh mà không build. Nếu bản mới đã thêm migration mà bản cũ không biết, Flyway của bản cũ sẽ báo lỗi validate: khi đó khôi phục DB từ bản sao lưu (bước 7) hoặc giữ bản mới.

## 7. Sao lưu và khôi phục DB

DB chỉ chứa ca demo giả lập, nhưng vẫn nên sao lưu trước khi đổi phiên bản:

```bash
docker compose exec -T db pg_dump -U ainalyzing ainalyzing > ~/backup-$(date +%F-%H%M).sql
# khôi phục:
docker compose exec -T db psql -U ainalyzing ainalyzing < ~/backup-YYYY-MM-DD-HHMM.sql
```

## 8. Đổi khóa Groq hoặc cấu hình

```bash
nano .env                         # đổi GROQ_API_KEY / GROQ_MODEL / GROQ_EGRESS_SOURCES ...
docker compose up -d app          # tạo lại container app với biến mới
```

Khóa Groq hiện tại đã được gửi qua chat khi chuẩn bị; nên tạo khóa mới trên console.groq.com và xóa khóa cũ sau buổi demo.

## 9. Sự cố thường gặp

| Hiện tượng | Nguyên nhân hay gặp | Cách xử lý |
|---|---|---|
| Caddy log `challenge failed` / trình duyệt báo chứng chỉ sai | DNS chưa trỏ đúng IP, cổng 80 bị chặn, hoặc `DOMAIN` sai | Sửa DNS/ufw/`.env`, rồi `docker compose restart caddy`. Let's Encrypt giới hạn số lần thử, nên kiểm DNS trước. |
| HTTPS trả 502 | App đang khởi động hoặc lỗi | `docker compose logs app`; chờ "Started ApiApplication". |
| App log `password authentication failed` | Đổi `DB_PASSWORD` sau khi volume đã tạo | Đặt lại mật khẩu cũ, hoặc `docker compose exec db psql -U ainalyzing -c "ALTER USER ainalyzing PASSWORD '...'"`. Chỉ khi chấp nhận mất dữ liệu: `docker compose down -v`. |
| Build bị `Killed` | Thiếu RAM | Thêm swap (bước 1). |
| Chat luôn `fallback` với `RATE_LIMITED` | Hết hạn mức 8.000 token/phút của khóa free tier | Chờ khoảng 1 phút; bảng tính không bị ảnh hưởng. |
| Chat luôn `fallback` với `DISABLED` | `GROQ_API_KEY` trống trong `.env` | Điền khóa, `docker compose up -d app`. |
| `/api/health` trả 503 | DB chưa sẵn sàng hoặc dừng | `docker compose ps`, `docker compose logs db`. |

## 10. Dừng hệ thống

```bash
docker compose down          # giữ volume DB và chứng chỉ Caddy
```
