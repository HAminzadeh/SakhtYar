# راهنمای اجرای محلی SakhtYar

این سند مراحل لازم برای بالا آوردن نسخه توسعه‌ای SakhtYar روی Windows با IntelliJ IDEA را توضیح می‌دهد.

> مسیر نمونه پروژه:
>
> `D:\ChatGPT_Projects\SakhtYar\source`

## 1. پیش‌نیازها

برای اجرای پروژه این ابزارها لازم‌اند:

- **Java 21**
- **IntelliJ IDEA**
- **Apache Maven 3.9.x**
- **Docker Desktop**
- **Node.js 24** و npm
- Git

### نکته درباره Java

ممکن است Java پیش‌فرض Windows روی Java 8 باقی بماند چون پروژه‌های قدیمی به آن نیاز دارند. برای SakhtYar لازم نیست Java سیستم را تغییر دهید؛ کافی است خود پروژه و Run Configuration در IntelliJ روی Java 21 باشند.

در IntelliJ:

```text
File → Project Structure → Project
Project SDK: Java 21
Language level: 21
```

برای ماژول backend:

```text
Project Structure → Modules → backend
Module SDK: Project SDK (21)
```

برای Maven:

```text
Settings
→ Build, Execution, Deployment
→ Build Tools
→ Maven
→ Runner
→ JRE = Project JDK (21)
```

## 2. بالا آوردن سرویس‌های زیرساخت

قبل از اجرای Backend باید PostgreSQL، Redis و MinIO بالا باشند.

از ریشه پروژه:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source
docker compose up -d db redis minio
```

وضعیت سرویس‌ها:

```powershell
docker compose ps
```

هر سه سرویس باید در وضعیت `Up` و در حالت عادی `healthy` باشند.

پورت‌های توسعه:

| سرویس | آدرس / پورت |
|---|---|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |

### تست PostgreSQL

```powershell
docker compose exec db pg_isready -U sakhtyar -d sakhtyar
```

### تست Redis

```powershell
docker compose exec redis redis-cli ping
```

پاسخ مورد انتظار:

```text
PONG
```

### MinIO

کنسول MinIO:

```text
http://localhost:9001
```

مقادیر پیش‌فرض توسعه:

```text
Username: sakhtyar
Password: change-me-minio
```

اگر این مقادیر در `.env` تغییر کرده‌اند، از مقادیر همان فایل استفاده شود.

## 3. تنظیم Run Configuration برای Backend

در IntelliJ فایل زیر را اجرا کنید:

```text
backend/src/main/java/com/sakhtyar/SakhtYarApplication.java
```

Run Configuration باید تقریباً این تنظیمات را داشته باشد:

```text
Name: SakhtYarApplication
Run on: Local machine
JRE: Java 21
Classpath: sakhtyar-backend
Main class: com.sakhtyar.SakhtYarApplication
Active profiles: خالی
```

برای اجرای Local فعلاً Active Profile را خالی بگذارید.

### درباره فایل `.env`

وقتی Backend مستقیماً از IntelliJ اجرا می‌شود، فایل `.env` به‌صورت خودکار به Environment Variables برنامه تزریق نمی‌شود.

تنظیمات `application.yml` برای Local دارای مقادیر پیش‌فرض است. از جمله:

```text
Database: jdbc:postgresql://localhost:5432/sakhtyar
DB username: sakhtyar
DB password: change-me-postgres

MinIO endpoint: http://localhost:9000
MinIO access key: sakhtyar
MinIO secret key: change-me-minio
```

اگر لازم است مقادیر `.env` هنگام اجرای IntelliJ استفاده شوند، متغیرها را در:

```text
Run → Edit Configurations → SakhtYarApplication
→ Environment variables
```

به صورت `KEY=value` اضافه کنید.

قرار دادن صرف مسیر فایل `.env` در فیلد Environment variables کافی نیست.

## 4. اجرای Backend

روی `SakhtYarApplication` دکمه Run یا Debug را بزنید.

در اولین اجرا Flyway migrationهای دیتابیس را اعمال می‌کند:

```text
V1__extensions.sql
V2__phase0_schema.sql
```

در اجرای موفق باید در Log خطوطی مشابه موارد زیر دیده شوند:

```text
Successfully validated 2 migrations
Schema "public" is up to date
Initialized JPA EntityManagerFactory
Tomcat started on port 8080
Started SakhtYarApplication
```

Backend روی این آدرس در دسترس خواهد بود:

```text
http://localhost:8080
```

Health Check:

```text
http://localhost:8080/actuator/health
```

## 5. حساب Admin اولیه

اگر هنوز کاربر Admin در دیتابیس وجود نداشته باشد، Backend هنگام startup آن را ایجاد می‌کند.

مقادیر پیش‌فرض:

```text
Username: admin
Password: ChangeMeNow_123!
```

نکته مهم: Bootstrap فقط زمانی Admin را می‌سازد که آن Username از قبل وجود نداشته باشد. بنابراین تغییر `APP_BOOTSTRAP_ADMIN_PASSWORD` بعد از ساخته شدن کاربر، پسورد Admin موجود را تغییر نمی‌دهد.

## 6. اجرای Frontend

یک Terminal جدید در IntelliJ باز کنید:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\frontend
npm install
npm run dev
```

Vite در حالت توسعه روی پورت 5173 اجرا می‌شود:

```text
http://localhost:5173
```

در `vite.config.ts` درخواست‌های `/api` به Backend روی پورت 8080 Proxy می‌شوند.

صفحه Login:

```text
http://localhost:5173
```

برای ورود اولیه:

```text
Username: admin
Password: ChangeMeNow_123!
```

## 7. ترتیب پیشنهادی اجرای روزانه

هر بار که قصد توسعه SakhtYar را دارید:

1. Docker Desktop را اجرا کنید.
2. از ریشه پروژه اجرا کنید:

   ```powershell
   docker compose up -d db redis minio
   ```

3. وضعیت سرویس‌ها را بررسی کنید:

   ```powershell
   docker compose ps
   ```

4. `SakhtYarApplication` را با Java 21 در IntelliJ اجرا کنید.
5. Health Check را بررسی کنید:

   ```text
   http://localhost:8080/actuator/health
   ```

6. Frontend را اجرا کنید:

   ```powershell
   cd frontend
   npm run dev
   ```

7. مرورگر را باز کنید:

   ```text
   http://localhost:5173
   ```

## 8. توقف محیط توسعه

Backend را از پنجره Run/Debug در IntelliJ متوقف کنید.

Frontend را با `Ctrl+C` متوقف کنید.

برای توقف سرویس‌های Docker:

```powershell
docker compose stop
```

برای حذف Containerها بدون حذف Volumeهای داده:

```powershell
docker compose down
```

> از `docker compose down -v` فقط زمانی استفاده کنید که عمداً می‌خواهید داده‌های Local شامل PostgreSQL، Redis و MinIO پاک شوند.

## 9. خطاهای مشاهده‌شده و راه‌حل‌ها

### Maven از Java 8 استفاده می‌کند

اگر در Terminal:

```powershell
mvn -version
```

Java 8 نشان داده شد، Java سراسری Windows را تغییر ندهید. Run Configuration و Maven Runner پروژه را روی Java 21 تنظیم کنید.

### خطای `Schema validation: missing table [app_user]`

Flyway باید قبل از Hibernate اجرا شود. در Spring Boot 4 پروژه باید dependencyهای مربوط به Flyway را داشته باشد:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>

<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

سپس Maven Project را Reload و Backend را مجدداً اجرا کنید.

### خطای ObjectMapper در Spring Boot 4

Spring Boot 4 از Jackson 3 استفاده می‌کند. در کدهایی مانند `AuditService` باید از packageهای Jackson 3 استفاده شود:

```java
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
```

نه importهای قدیمی `com.fasterxml.jackson.databind.ObjectMapper`.

### Login با رمز جدید `.env` انجام نمی‌شود

اگر Admin قبلاً ساخته شده باشد، تغییر مقدار زیر:

```text
APP_BOOTSTRAP_ADMIN_PASSWORD
```

رمز کاربر موجود را تغییر نمی‌دهد. در محیط توسعه تا زمان اضافه شدن قابلیت Change Password، از رمز ذخیره‌شده فعلی استفاده کنید.

## 10. بررسی سریع محیط

برای یک Smoke Test ساده:

- PostgreSQL: healthy
- Redis: healthy
- MinIO: healthy
- Backend: `/actuator/health` پاسخ سالم
- Frontend: `http://localhost:5173` باز می‌شود
- Login با Admin موفق است

پس از عبور از این موارد، محیط Local برای ادامه توسعه SakhtYar آماده است.
