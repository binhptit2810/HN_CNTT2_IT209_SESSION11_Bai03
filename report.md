# BÁO CÁO THỰC HÀNH: TỐI ƯU TỐC ĐỘ CI BẰNG GRADLE DEPENDENCY CACHING

- **Môn học**: IT209 - DevOps & CI/CD
- **Bài tập**: Bài 3 (Session 11) - Tối ưu tốc độ CI bằng Gradle Dependency Caching
- **Repository tham chiếu**: `https://github.com/minh-duy2608/abcd`

---

## 1. Mục tiêu thực hành

- Kích hoạt tính năng **Dependency Caching** cho Gradle trong quy trình GitHub Actions CI.
- Giảm thiểu việc phải tải lại Gradle Wrapper (~137 MB) và toàn bộ các thư viện Spring Boot dependencies (~47 MB) từ Internet trong mỗi lần luồng CI được kích hoạt.
- Đánh giá sự khác biệt về thời gian thực thi (execution time) giữa:
  - **Lần chạy thứ nhất (Cold Cache)**: Chưa có cache, toàn bộ dependencies và wrapper phải tải mới từ Internet, sau đó được lưu vào Cache Store của GitHub.
  - **Lần chạy thứ hai (Hot Cache)**: Đã có cache sẵn, GitHub Actions tự động khôi phục cache (Cache Hit), rút ngắn đáng kể thời gian biên dịch và kiểm thử.

---

## 2. Cấu hình kịch bản CI (`.github/workflows/ci.yml`)

Trong tệp `.github/workflows/ci.yml`, bổ sung tham số `cache: 'gradle'` vào bước `actions/setup-java@v5`:

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

### Cơ chế hoạt động của `cache: 'gradle'` trong `setup-java@v5`

Khi khai báo `cache: 'gradle'`:
1. **Trước khi build**: Action tự động tìm kiếm cache entries phù hợp với hash của các tệp `build.gradle`, `settings.gradle`, `gradle-wrapper.properties`.
2. **Nếu tìm thấy (Cache Hit)**: Tải và giải nén trực tiếp vào thư mục `~/.gradle/caches` và `~/.gradle/wrapper/dists`.
3. **Sau khi build thành công (Post step)**: Nếu có dependencies mới hoặc cache chưa tồn tại, action sẽ đóng gói thư mục cache và đẩy lên GitHub Actions Cache Storage.

---

## 3. Thực nghiệm & Dữ liệu đo lường thực tế

Quy trình thực hiện: Thực hiện push code 2 lần liên tiếp lên branch `main` của repository GitHub:

- **Lần Push 1 (Commit `1b115b5`)**: Kích hoạt cache lần đầu, cold run (Cache Miss & Store).
  - Workflow Run ID: `38243828858`
  - Trạng thái: `Success`
- **Lần Push 2 (Commit `2984487`)**: Kiểm tra khôi phục cache, hot run (Cache Hit).
  - Workflow Run ID: `38243972226`
  - Trạng thái: `Success`

### Dữ liệu các tệp Cache đã lưu trữ trên GitHub Actions

Thông qua GitHub Actions Cache API, hệ thống đã lưu trữ thành công 2 mục cache chính:

| Cache ID | Cache Key | Kích thước | Mô tả |
| :--- | :--- | :--- | :--- |
| `8831027497` | `setup-java-Linux-x64-gradle-wrapper-4550de4a...` | **137.38 MB** | Bộ cài đặt Gradle Wrapper (Gradle 8.8 Distribution) |
| `8831028835` | `setup-java-Linux-x64-gradle-6159612422...` | **46.88 MB** | Các thư viện Spring Boot dependencies từ Maven Central |
| **Tổng cộng** | | **~184.26 MB** | Dữ liệu được tái sử dụng qua cache |

---

## 4. Bảng so sánh thời gian thực thi

| Bước thực hiện (Step) | Lần Push 1 (Cold Cache) | Lần Push 2 (Hot Cache) | Chênh lệch (Thời gian tiết kiệm) |
| :--- | :---: | :---: | :---: |
| **Thiết lập môi trường Java JDK 17** | 1 giây (Cache Miss) | 4 giây (Tải & bung 184MB cache) | +3 giây |
| **Step 1 - Check syntax & Compile** (`./gradlew compileJava`) | **20 giây** | **10 giây** | **Giảm 10 giây (-50.0%)** |
| **Step 2 - Run Unit Tests** (`./gradlew test`) | **8 giây** | **6 giây** | **Giảm 2 giây (-25.0%)** |
| **Step 3 - Package Application** (`./gradlew bootJar`) | 2 giây | 1 giây | **Giảm 1 giây (-50.0%)** |
| **Step 4 - Upload Build Artifact** | 2 giây | 3 giây | Tương đương (~2-3s) |
| **Post - Setup Java (Cache Post step)** | 3 giây (Đóng gói & upload cache) | 0 giây (Cache giữ nguyên) | **Giảm 3 giây** |
| **TỔNG THỜI GIAN TOÀN BỘ JOB** | **39 giây** | **29 giây** | **Tiết kiệm 10 giây (~25.6%)** |

---

## 5. Trích xuất Log kiểm tra tính năng Caching

### 5.1. Lần Push 1: Giai đoạn lưu trữ Cache (Cache Miss & Save)
Trong bước `Post Thiết lập môi trường Java JDK 17` ở lần chạy đầu tiên:
```text
Post Thiết lập môi trường Java JDK 17
Received 137384655 of 137384655 bytes (100.0%) for setup-java-Linux-x64-gradle-wrapper-...
Received 46882154 of 46882154 bytes (100.0%) for setup-java-Linux-x64-gradle-...
Cache saved successfully
```

### 5.2. Lần Push 2: Giai đoạn khôi phục Cache thành công (Cache Hit)
Trong bước `Thiết lập môi trường Java JDK 17` ở lần chạy thứ hai:
```text
Run actions/setup-java@v5
with:
  java-version: 17
  distribution: temurin
  cache: gradle

Resolved gradle-wrapper cache key: setup-java-Linux-x64-gradle-wrapper-4550de4abfdb58a557145eb8ab8202a60e3724fc70547c4eac9e6a3dc5b7fb99
Cache restored successfully from key: setup-java-Linux-x64-gradle-wrapper-4550de4abfdb58a557145eb8ab8202a60e3724fc70547c4eac9e6a3dc5b7fb99

Resolved gradle cache key: setup-java-Linux-x64-gradle-6159612422e0b48397e499f62a1958f70c0587d23d1842613b16ba0d65766cd7
Cache restored successfully from key: setup-java-Linux-x64-gradle-6159612422e0b48397e499f62a1958f70c0587d23d1842613b16ba0d65766cd7
```

---

## 6. Kết luận & Đánh giá

1. **Hiệu quả tối ưu vượt trội**:
   - Bước biên dịch `./gradlew compileJava` giảm một nửa thời gian (từ 20s xuống 10s).
   - Tổng thời gian chạy toàn bộ luồng CI giảm từ **39s xuống 29s**, tiết kiệm **~26%** thời gian execution. Đối với các dự án lớn với hàng trăm dependencies và nhiều microservices, mức tiết kiệm có thể lên tới 5 - 10 phút cho mỗi build.
2. **Tiết kiệm tài nguyên & Chi phí**:
   - Không còn tải lại hơn 184 MB dữ liệu từ Gradle/Maven qua mạng mỗi lần trigger workflow.
   - Giúp tiết kiệm số phút GitHub Actions runner (Action execution minutes) của tài khoản.
3. **Trải nghiệm phát triển (Dev Experience)**:
   - Các lập trình viên nhận được kết quả lint/compile/test phản hồi từ CI nhanh hơn đáng kể sau mỗi lượt commit / pull request.
