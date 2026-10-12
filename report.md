# BÁO CÁO THỰC HÀNH: TỐI ƯU TỐC ĐỘ CI BẰNG GRADLE DEPENDENCY CACHING

## 1. Thông Tin Chung
- **Môn học:** IT209 - DevOps & Cloud Computing / CI/CD
- **Bài tập:** Bài 3 (Session 11) - Tối ưu tốc độ CI bằng Gradle Dependency Caching
- **Họ và tên sinh viên:** Đặng Thanh Bình
- **Mã sinh viên:** PTIT-HN-255
- **Lớp:** HN_KS24_CNTT2
- **GitHub Repository:** [HN_CNTT2_IT209_SESSION11_Bai03](https://github.com/binhptit2810/HN_CNTT2_IT209_SESSION11_Bai03)
- **Thư mục bài làm:** `homework/session_11/ex3/` (đồng thời hỗ trợ `homework/session_11/ex03/`)

---

## 2. Mục Tiêu Thực Hành
- Kích hoạt tính năng **Dependency Caching** cho Gradle trong quy trình tự động hóa GitHub Actions CI.
- Khắc phục hiện tượng lãng phí băng thông và thời gian khi GitHub Actions runner phải tải lại phân phối Gradle Wrapper (~131.02 MB) và toàn bộ các gói thư viện Spring Boot dependencies (~44.71 MB) từ Internet trong mỗi lần kích hoạt luồng CI.
- Đo lường và đối sánh định lượng sự chênh lệch thời gian thực thi (execution time) giữa:
  - **Lần Push 1 (Cold Cache - Cache Miss)**: Chưa có dữ liệu bộ nhớ đệm, hệ thống phải tải toàn bộ từ Maven Central / Gradle Services qua Internet, sau đó nén và lưu trữ vào GitHub Actions Cache Storage.
  - **Lần Push 2 (Hot Cache - Cache Hit)**: Đã tồn tại cache key hợp lệ, GitHub Actions tự động khôi phục dữ liệu bộ nhớ đệm, giúp rút ngắn thời gian biên dịch và chạy kiểm thử JUnit.

---

## 3. Cấu Hình Kịch Bản CI (`.github/workflows/ci.yml`)

Trong bước thiết lập môi trường Java `actions/setup-java@v5`, cấu hình tham số `cache: 'gradle'`:

```yaml
name: Java Spring Boot Gradle CI

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]

jobs:
  build:
    name: Lint, Test, Build & Package
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Thiết lập môi trường Java JDK 17
        uses: actions/setup-java@v5
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Grant execute permission for gradlew
        run: |
          if [ -f "./gradlew" ]; then
            chmod +x ./gradlew
          fi

      - name: Step 1 - Check syntax & Compile
        run: |
          if [ -f "./gradlew" ]; then
            ./gradlew compileJava
          else
            gradle compileJava
          fi

      - name: Step 2 - Run Unit Tests
        run: |
          if [ -f "./gradlew" ]; then
            ./gradlew test
          else
            gradle test
          fi

      - name: Step 3 - Package Application (bootJar)
        run: |
          if [ -f "./gradlew" ]; then
            ./gradlew bootJar
          else
            gradle bootJar
          fi

      - name: Step 4 - Upload Build Artifact
        uses: actions/upload-artifact@v4
        with:
          name: spring-boot-gradle-api-jar
          path: build/libs/*.jar
          retention-days: 7
```

### Phân tích cơ chế hoạt động của `cache: 'gradle'`
1. **Quét và khởi tạo Cache Key**:
   - `actions/setup-java@v5` phân tích mã băm (hash) của các tệp quản lý cấu hình và phụ thuộc: `gradle/wrapper/gradle-wrapper.properties`, `build.gradle`, `settings.gradle`.
   - Sinh ra 2 Cache Key độc lập:
     - `setup-java-Linux-x64-gradle-wrapper-<hash>`: Dành cho bộ phân phối Gradle Wrapper.
     - `setup-java-Linux-x64-gradle-<hash>`: Dành cho toàn bộ thư viện dependencies tải từ kho Maven Central.
2. **Khôi phục bộ nhớ đệm (Pre-execution)**:
   - Trước khi bất kỳ lệnh Gradle nào được thực thi, action truy vấn GitHub Actions Cache API.
   - Nếu Cache Key khớp (**Cache Hit**), dữ liệu được tải về và bung trực tiếp vào `~/.gradle/wrapper/dists` và `~/.gradle/caches`.
3. **Lưu trữ bộ nhớ đệm (Post-execution step)**:
   - Sau khi hoàn thành toàn bộ các bước của job, bước `Post Thiết lập môi trường Java JDK 17` kiểm tra xem cache đã tồn tại chưa.
   - Nếu ở lần chạy đầu tiên (**Cache Miss**), runner tự động đóng gói thư mục cache thành các gói `.tzst` (nén bằng thuật toán Zstandard) và tải lên kho lưu trữ GitHub Actions Cache.

---

## 4. Dữ Liệu Thực Nghiệm Trên GitHub Actions

Tiến hành push mã nguồn 2 lần liên tiếp lên nhánh `main` của repository `HN_CNTT2_IT209_SESSION11_Bai03`:

| Thông số | Lần Push 1 (Cold Cache) | Lần Push 2 (Hot Cache) |
| :--- | :--- | :--- |
| **Commit SHA** | `7bdad77` (*feat: setup Spring Boot Gradle project...*) | `c71ca0b` (*feat: add /api/status endpoint...*) |
| **Workflow Run ID** | `38257952459` | `38258406462` |
| **URL Kiểm tra** | [Run 38257952459](https://github.com/binhptit2810/HN_CNTT2_IT209_SESSION11_Bai03/actions/runs/38257952459) | [Run 38258406462](https://github.com/binhptit2810/HN_CNTT2_IT209_SESSION11_Bai03/actions/runs/38258406462) |
| **Trạng thái (Conclusion)** | `success` | `success` |
| **Trạng thái Cache** | Cache Miss ➔ Save Cache | Cache Hit ➔ Restored Successfully |

### Dữ liệu các tệp Cache lưu trữ trên GitHub Actions
Sau Lần Push 1, hệ thống đã lưu trữ thành công 2 mục cache chính:

| Cache Key | Kích thước lưu trữ | Thành phần lưu trữ |
| :--- | :---: | :--- |
| `setup-java-Linux-x64-gradle-wrapper-4550de4abfdb58a557145eb8ab8202a60e3724fc70547c4eac9e6a3dc5b7fb99` | **131.02 MB** (137,384,750 bytes) | Bộ cài đặt Gradle 8.8 Distribution |
| `setup-java-Linux-x64-gradle-6159612422e0b48397e499f62a1958f70c0587d23d1842613b16ba0d65766cd7` | **44.71 MB** (46,877,266 bytes) | Thư viện Spring Boot 3.2.4 & Test dependencies |
| **TỔNG CỘNG** | **175.73 MB** (184,262,016 bytes) | Tái sử dụng trọn vẹn ở các lần chạy sau |

---

## 5. Bảng So Sánh Thời Gian Thực Thi Chi Tiết

| Bước thực thi trong CI Job | Lần Push 1 (Cold Cache) | Lần Push 2 (Hot Cache) | Chênh lệch (Thời gian tiết kiệm) | Tỷ lệ giảm |
| :--- | :---: | :---: | :---: | :---: |
| **Thiết lập môi trường Java JDK 17** | 1.0 giây | 6.0 giây | +5.0 giây (Tải & bung 175.7MB) | - |
| **Step 1 - Check syntax & Compile** (`./gradlew compileJava`) | **27.0 giây** | **5.0 giây** | **Tiết kiệm 22.0 giây** | **-81.5%** |
| **Step 2 - Run Unit Tests** (`./gradlew test`) | **8.0 giây** | **4.0 giây** | **Tiết kiệm 4.0 giây** | **-50.0%** |
| **Step 3 - Package Application** (`./gradlew bootJar`) | 2.0 giây | 1.0 giây | **Tiết kiệm 1.0 giây** | **-50.0%** |
| **Step 4 - Upload Build Artifact** | 2.0 giây | 2.0 giây | 0.0 giây (Bằng nhau) | 0.0% |
| **Post Thiết lập môi trường Java JDK 17** | 4.0 giây | 0.0 giây | **Tiết kiệm 4.0 giây** | **-100.0%** |
| **TỔNG THỜI GIAN TOÀN BỘ JOB** | **48.0 giây** | **20.0 giây** | **TIẾT KIỆM 28.0 GIÂY** | **-58.3%** |

> **Nhận xét then chốt:**
> - Bước **biên dịch mã nguồn** (`compileJava`) có tốc độ tăng trưởng vượt trội nhất: từ **27.0s giảm xuống còn 5.0s (nhanh hơn gấp 5.4 lần)** do toàn bộ trình biên dịch và phụ thuộc đã nằm sẵn trong disk cache của runner.
> - Bước **chạy Unit Tests** (`./gradlew test`) giảm một nửa thời gian (từ **8.0s xuống 4.0s**).
> - Tổng thời gian chạy toàn bộ luồng CI giảm từ **48.0s xuống 20.0s (tiết kiệm 58.3% thời gian thực thi)**.

---

## 6. Trích Xuất Log Kiểm Tra Từ GitHub Actions Runner

### 6.1. Lần Push 1: Giai đoạn lưu trữ Cache (Post Job Save)
Trích xuất từ log bước `Post Thiết lập môi trường Java JDK 17` (Job ID: `114840790883`):
```text
Post job cleanup.
[command]/usr/bin/tar --posix -cf cache.tzst --exclude cache.tzst -P -C /home/runner/work/HN_CNTT2_IT209_SESSION11_Bai03/HN_CNTT2_IT209_SESSION11_Bai03 --files-from manifest.txt --use-compress-program zstdmt
Sent 3167022 of 137384750 (2.3%), 3.0 MBs/sec
Sent 137384750 of 137384750 (100.0%), 74.7 MBs/sec
gradle-wrapper cache saved with the key: setup-java-Linux-x64-gradle-wrapper-4550de4abfdb58a557145eb8ab8202a60e3724fc70547c4eac9e6a3dc5b7fb99
[command]/usr/bin/tar --posix -cf cache.tzst --exclude cache.tzst -P -C /home/runner/work/HN_CNTT2_IT209_SESSION11_Bai03/HN_CNTT2_IT209_SESSION11_Bai03 --files-from manifest.txt --use-compress-program zstdmt
Sent 46877266 of 46877266 (100.0%), 44.7 MBs/sec
Cache saved with the key: setup-java-Linux-x64-gradle-6159612422e0b48397e499f62a1958f70c0587d23d1842613b16ba0d65766cd7
```

### 6.2. Lần Push 2: Giai đoạn khôi phục Cache thành công (Cache Restored)
Trích xuất từ log bước `Thiết lập môi trường Java JDK 17` (Job ID: `114842345371`):
```text
##[group]Run actions/setup-java@v5
with:
  java-version: 17
  distribution: temurin
  cache: gradle
  check-latest: false
  mvn-toolchains-xml: null
  mvn-toolchains-id: null
Cache restored successfully
Cache restored from key: setup-java-Linux-x64-gradle-6159612422e0b48397e499f62a1958f70c0587d23d1842613b16ba0d65766cd7
Cache restored successfully
gradle-wrapper cache restored from key: setup-java-Linux-x64-gradle-wrapper-4550de4abfdb58a557145eb8ab8202a60e3724fc70547c4eac9e6a3dc5b7fb99
```

Log xác nhận rõ ràng:
- Thông báo: **`Cache restored successfully`**
- Khôi phục đầy đủ cả **Gradle Wrapper** và **Gradle Dependencies** từ key tương ứng.

---

## 7. Kết Luận & Đánh Giá

1. **Hiệu quả tối ưu hóa:**
   - Việc bổ sung tham số `cache: 'gradle'` trong action `setup-java@v5` giúp thời gian build của ứng dụng Java Spring Boot giảm tới **58.3%** (từ 48 giây xuống còn 20 giây).
   - Với các dự án thực tế quy mô lớn (doanh nghiệp lớn với hàng trăm thư viện và phụ thuộc), việc caching giúp tiết kiệm từ 5 - 15 phút cho mỗi commit / PR.
2. **Tiết kiệm tài nguyên và chi phí:**
   - Không còn tải lại hơn 175 MB dữ liệu qua mạng mỗi lần luồng CI chạy.
   - Giảm đáng kể số phút tiêu hao GitHub Actions Runner (tiết kiệm ngân sách tài nguyên CI).
3. **Trải nghiệm lập trình viên:**
   - Vòng phản hồi (feedback loop) từ hệ thống CI đến lập trình viên diễn ra gần như tức thì, phát hiện lỗi biên dịch hoặc test hỏng nhanh chóng.
