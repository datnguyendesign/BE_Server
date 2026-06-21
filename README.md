# DACN2 Backend Server

## 📖 Giới thiệu (Introduction)

Dự án này là hệ thống Backend Server được xây dựng bằng **Java Spring Boot**, phục vụ cho Đồ án Chuyên ngành 2 (DACN2).
Hệ thống được thiết kế để giải quyết vấn đề quản lý và vận hành dữ liệu tập trung, cung cấp RESTful API cho các ứng dụng
Client (Web/Mobile) tương tác.

**Vấn đề giải quyết:**

- Cung cấp nền tảng backend an toàn, hiệu quả để xử lý logic nghiệp vụ
- Xác thực và phân quyền người dùng với JWT
- Lưu trữ và quản lý dữ liệu bền vững
- Đóng vai trò "xương sống" kết nối giữa giao diện người dùng và cơ sở dữ liệu

---

## 🚀 Các tính năng chính (Key Features)

* **Xác thực & Phân quyền (Authentication & Authorization):** Đăng ký, đăng nhập, JWT token, phân quyền User/Admin với
  Spring Security.
* **Quản lý Tài nguyên (Resource Management):** CRUD (Tạo, Đọc, Cập nhật, Xóa) cho các đối tượng chính của hệ thống.
* **Xử lý Exception:** Hệ thống xử lý lỗi tập trung với custom exception handlers.
* **Validation:** Validate dữ liệu đầu vào với Bean Validation (JSR-380).
* **Bảo mật:** Mã hóa mật khẩu BCrypt, JWT Authentication, Spring Security filters.

---

## 📂 Cấu trúc thư mục (Project Structure)

```bash
DACN2_BEserver/
├── Dacn2BEserverApplication.java    # Entry point - Main class khởi chạy Spring Boot Application
├── config/                          # Cấu hình hệ thống (Security, CORS, Swagger, Database, JWT)
├── controller/                      # REST Controllers - Tiếp nhận HTTP request và trả về response
├── dto/                             # Data Transfer Objects - Định nghĩa cấu trúc request/response
├── exception/                       # Custom Exceptions và Global Exception Handler
├── filter/                          # Security Filters (JWT Authentication Filter)
├── model/                           # Entity classes - Ánh xạ với bảng trong Database (JPA Entities)
├── repository/                      # JPA Repositories - Tương tác với Database
├── security/                        # Security configurations và utilities (JWT Utils, UserDetails)
└── service/                         # Business Logic Layer - Xử lý logic nghiệp vụ
```

### Giải thích chi tiết:

| Thư mục       | Mô tả                                                                                   |
|:--------------|:----------------------------------------------------------------------------------------|
| `config/`     | Chứa các class cấu hình: SecurityConfig, CorsConfig, SwaggerConfig, v.v.                |
| `controller/` | REST API endpoints. Nhận request từ client, gọi service và trả response.                |
| `dto/`        | Request/Response objects. Tách biệt với Entity để bảo mật và linh hoạt.                 |
| `exception/`  | Custom exceptions (UserNotFoundException, BadRequestException...) và @ControllerAdvice. |
| `filter/`     | JWT Filter chặn request để xác thực token trước khi vào controller.                     |
| `model/`      | JPA Entities ánh xạ với các bảng trong database (User, Product, Order...).              |
| `repository/` | Interfaces extends JpaRepository để thực hiện các thao tác CRUD với database.           |
| `security/`   | JWT utilities, UserDetailsService implementation, PasswordEncoder config.               |
| `service/`    | Business logic layer. Xử lý nghiệp vụ, validation logic, gọi repository.                |

---

## 🛠 Cài đặt và Hướng dẫn chạy (Installation & Usage)

### 1. Yêu cầu (Prerequisites)

* **Java JDK 17+** (hoặc 11+)
* **Maven 3.6+** hoặc **Gradle**
* **MySQL / PostgreSQL** (hoặc H2 cho development)
* **Git**
* **Docker** (tùy chọn)

### 2. Cài đặt Local (Local Setup)

**Bước 1: Clone dự án**

```bash
git clone https://github.com/username/DACN2_BEserver.git
cd DACN2_BEserver
```

**Bước 2: Cấu hình Database**

Chỉnh sửa file `src/main/resources/application.properties` hoặc `application.yml`:

```properties
# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/dacn2_db
spring.datasource.username=root
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
# JWT Configuration
jwt.secret=your_jwt_secret_key
jwt.expiration=86400000
```

**Bước 3: Build và chạy dự án**

```bash
# Với Maven
./mvnw clean install
./mvnw spring-boot:run

# Hoặc chạy trực tiếp
java -jar target/dacn2-beserver-0.0.1-SNAPSHOT.jar
```

Server sẽ chạy tại: `http://localhost:8080`

### 3. Chạy với Docker

```bash
# Build image
docker build -t dacn2-server .

# Run container
docker run -p 3000:3000 --env-file .env dacn2-server
```

### 4. Triển khai

**Option 1: EC2**

```bash
# Cài đặt Java
sudo yum install java-17-amazon-corretto -y

# Clone và chạy
git clone https://github.com/username/DACN2_BEserver.git
cd DACN2_BEserver
./mvnw spring-boot:run
```

**Option 2: Elastic Beanstalk**

1. Tạo JAR file: `./mvnw clean package`
2. Upload file JAR lên Elastic Beanstalk
3. Chọn platform: Java 17

**Option 3: ECS với Docker**

1. Push Docker image lên ECR
2. Tạo ECS Task Definition
3. Deploy với ECS Service

---

## 📡 API Contract

### Base URL: `http://localhost:8080/api`

### 1. Authentication (Xác thực)

#### POST `/api/auth/register` - Đăng ký tài khoản

**Request Body:**

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "password123",
  "fullName": "John Doe"
}
```

**Response (201 Created):**

```json
{
  "message": "User registered successfully",
  "userId": 1
}
```

#### POST `/api/auth/login` - Đăng nhập

**Request Body:**

```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

**Response (200 OK):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "email": "john@example.com",
    "fullName": "John Doe",
    "role": "USER"
  }
}
```

#### POST `/api/auth/refresh-token` - Làm mới token

**Request Body:**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**

```json
{
  "accessToken": "new_access_token...",
  "tokenType": "Bearer"
}
```

#### POST `/api/auth/logout` - Đăng xuất

**Headers:** `Authorization: Bearer <accessToken>`
**Response (200 OK):**

```json
{
  "message": "Logged out successfully"
}
```

---

### 2. Users (Quản lý người dùng)

#### GET `/api/users/profile` - Lấy thông tin cá nhân

**Headers:** `Authorization: Bearer <accessToken>`
**Response (200 OK):**

```json
{
  "id": 1,
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "avatar": "https://...",
  "role": "USER",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

#### PUT `/api/users/profile` - Cập nhật thông tin

**Headers:** `Authorization: Bearer <accessToken>`
**Request Body:**

```json
{
  "fullName": "John Smith",
  "avatar": "https://new-avatar-url.com/img.jpg"
}
```

**Response (200 OK):**

```json
{
  "message": "Profile updated successfully",
  "user": {
    "id": 1,
    "username": "johndoe",
    "email": ""
  }
}
```

#### PUT `/api/users/change-password` - Đổi mật khẩu

**Headers:** `Authorization: Bearer <accessToken>`
**Request Body:**

```json
{
  "oldPassword": "password123",
  "newPassword": "newPassword456"
}
```

**Response (200 OK):**

```json
{
  "message": "Password changed successfully"
}
```

#### GET `/api/users` - Lấy danh sách users (Admin only)

**Headers:** `Authorization: Bearer <accessToken>`
**Query Params:** `?page=0&size=10&sort=createdAt,desc`
**Response (200 OK):**

```json
{
  "content": [
    {
      "id": 1,
      "email": "user1@example.com"
    },
    {
      "id": 2,
      "email": "user2@example.com"
    }
  ],
  "totalElements": 100,
  "totalPages": 10,
  "currentPage": 0
}
```

#### DELETE `/api/users/{id}` - Xóa user (Admin only)

**Headers:** `Authorization: Bearer <accessToken>`
**Response (200 OK):**

```json
{
  "message": "User deleted successfully"
}
```

---

### 3. Error Responses

Tất cả API trả về lỗi theo format thống nhất:

```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": [
    "Email is required",
    "Password must be at least 6 characters"
  ],
  "path": "/api/auth/register"
}
```

| Status Code | Mô tả                                            |
|:------------|:-------------------------------------------------|
| `400`       | Bad Request - Dữ liệu không hợp lệ               |
| `401`       | Unauthorized - Chưa đăng nhập hoặc token hết hạn |
| `403`       | Forbidden - Không có quyền truy cập              |
| `404`       | Not Found - Không tìm thấy resource              |
| `500`       | Internal Server Error - Lỗi server               |

---

## 🔄 Luồng xử lý hệ thống (System Flow)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT REQUEST                                  │
│                         (Web Browser / Mobile App)                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           1. FILTER LAYER                                    │
│  ┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐     │
│  │   CORS Filter   │ -> │ JWT Auth Filter  │ -> │ Security Filter     │     │
│  │   (Cross-Origin)│    │ (Token Validate) │    │ (Authorization)     │     │
│  └─────────────────┘    └──────────────────┘    └─────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         2. CONTROLLER LAYER                                  │
│  • Nhận HTTP Request (GET, POST, PUT, DELETE)                               │
│  • Validate Request Body với @Valid annotation                              │
│  • Gọi Service tương ứng                                                    │
│  • Trả về ResponseEntity với HTTP Status phù hợp                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          3. SERVICE LAYER                                    │
│  • Xử lý Business Logic (tính toán, validation nghiệp vụ)                   │
│  • Transform DTO <-> Entity                                                 │
│  • Gọi Repository để truy vấn Database                                      │
│  • Throw Exception khi có lỗi nghiệp vụ                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        4. REPOSITORY LAYER                                   │
│  • Extends JpaRepository<Entity, ID>                                        │
│  • Thực hiện CRUD operations                                                │
│  • Custom queries với @Query annotation                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            5. DATABASE                                       │
│                    (MySQL / PostgreSQL / H2)                                │
│  • Lưu trữ dữ liệu persistent                                               │
│  • Trả về kết quả query                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
                          Response trả về Client
                          (JSON format + HTTP Status)
```

### Chi tiết luồng xử lý Login:

```
1. Client gửi POST /api/auth/login với {email, password}
                    │
                    ▼
2. JwtAuthenticationFilter: Bỏ qua vì đây là public endpoint
                    │
                    ▼
3. AuthController.login(): Nhận request, validate input
                    │
                    ▼
4. AuthService.login():
   - Tìm user theo email trong DB
   - So sánh password với BCrypt
   - Nếu hợp lệ: Tạo JWT Access Token + Refresh Token
   - Nếu sai: Throw UnauthorizedException
                    │
                    ▼
5. Trả về Response với tokens và user info
```

---

## 🧪 Kiểm thử (Testing)

### Chạy Unit Tests

```bash
./mvnw test
```

### Chạy Integration Tests

```bash
./mvnw verify
```

### Test Coverage Report

```bash
./mvnw jacoco:report
# Report tại: target/site/jacoco/index.html
```

### Test với Postman

Import collection từ: `docs/postman_collection.json`

---

## 📚 Công nghệ sử dụng (Tech Stack)

| Công nghệ       | Phiên bản | Mô tả                               |
|:----------------|:----------|:------------------------------------|
| Java            | 17        | Ngôn ngữ lập trình chính            |
| Spring Boot     | 3.x       | Framework backend                   |
| Spring Security | 6.x       | Xác thực và phân quyền              |
| Spring Data JPA | 3.x       | ORM và database access              |
| MySQL           | 8.0       | Cơ sở dữ liệu chính                 |
| JWT             | 0.11.x    | JSON Web Token authentication       |
| Maven           | 3.9       | Build tool và dependency management |
| Docker          | 24.x      | Containerization                    |

---

## 👥 Tác giả (Authors)

- **Hồ Ngọc Bảo Long** - *Developer* - [GitHub](https://github.com/username)

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.