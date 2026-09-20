# SakhtYar Professional Mobile-First UI v1

این نسخه UI فقط «Responsive» نیست؛ معماری بصری آن از ابتدا برای هم‌راستایی با Mobile App طراحی شده است.

## اصول

### 1. Design Tokens
فایل:

`frontend/src/ui/tokens.ts`

مقادیر پایه رنگ، spacing، radius و اندازه کنترل‌ها را متمرکز کرده است.
در آینده همین tokenها می‌توانند به:
- React Native theme
- Flutter ThemeData
- Design Tokens JSON
منتقل شوند.

### 2. Shared Semantic Catalogs
فایل:

`frontend/src/domain/propertyOptions.ts`

مقدارهای استاندارد Property از UI جدا شده‌اند:
- property type
- deed type
- orientation
- ownership status
- land use
- building condition
- corner position

مقدار ذخیره‌شده انگلیسی/استاندارد است و label فارسی است.
در Mobile App باید همین contractها مصرف شوند.

### 3. Mobile-first Layout
- xs: تک‌ستونه
- sm: دو ستون
- lg: سه ستون
- CTA ذخیره در موبایل sticky و thumb-friendly است.
- Bottom Navigation در موبایل استفاده شده است.
- Tabs پرونده scrollable هستند.
- min touch target حدود 44-48px نگه داشته شده است.

### 4. Component Primitives
کامپوننت‌های پایه:
- SectionCard
- FieldGroup
- ResponsiveFieldGrid / FieldCell

این الگوها باید در Mobile App معادل native داشته باشند.

### 5. API مستقل از Presentation
این Patch قراردادهای API را تغییر نمی‌دهد.
Web و Mobile باید هر دو از endpointهای یکسان استفاده کنند.

## پیشنهاد برای Mobile App آینده

اگر React Native انتخاب شود:
- package مشترک `packages/domain` برای typeها و option catalogها ساخته شود.
- package مشترک `packages/design-tokens` ساخته شود.
- query keys و API DTOها نیز قابل اشتراک هستند.

اگر Flutter انتخاب شود:
- JSON contract و enumهای Backend منبع حقیقت باقی بمانند.
- Design Tokenها از فایل JSON تولید شوند.

## تست Responsive

Chrome DevTools:
- 360x800
- 390x844
- 430x932
- 768x1024
- 1440x900

صفحه‌های مهم:
- Login
- Cases
- Case Detail
- Property
- Assistant
- Owners
- Map
