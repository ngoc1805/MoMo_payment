# MoMo Payment

* Backend: momo_be  
* Frontend: testMomo

---

# Cài đặt

- Đầu tiên tải MoMo UAT về điện thoại và đăng ký tài khoản: https://developers.momo.vn/v3/download
- Phải xác thực căn cước công dân trên MoMo UAT trước khi thử nghiệm
- Video cách thêm ngân hàng để nạp tiền ảo trên MoMo UAT: https://drive.google.com/file/d/1a_emS2Vb7ltP6ajrRxw_1Iqafr09UqPl/view?usp=sharing
- Nếu máy chạy FE chưa cài MoMo UAT thì thử nghiệm bằng hình thức quét QR (không cần cài app trên máy FE)

---

#  Cấu hình URL 

Project dùng ngrok để expose server local ra internet (nếu dự án đã deploy thì dùng luôn không cần cái này)

# tìm hiểu và chạy ngrok

Sau khi chạy, sao chép URL ngrok được cấp, ví dụ: `https://bb95-1-55-241-30.ngrok-free.app`

# Cập nhật URL trong Backend

Mở file MomoRoutes.kt, dòng 171, sửa `ipnUrl` thành URL ngrok của bạn:

## Cập nhật URL trong Frontend

Mở file requestPayUrl.kt, dòng 15, sửa biến `url`:

# Lưu ý URL ngrok thay đổi mỗi lần khởi động (bản free). Bạn phải cập nhật cả 2 file mỗi lần đổi URL.

---

# Luồng hoạt động tổng quan


Android App (FE)
    │
    │  POST /api/momo/pay     (thanh toán App MoMo)
    │  POST /api/momo/qr      (tạo QR)
    │  GET  /api/momo/status  (kiểm tra trạng thái)
    ▼
Ktor Server (BE)
    │
    │  POST https://test-payment.momo.vn/v2/gateway/api/create
    ▼
MoMo Sandbox API
    │
    │  POST /api/momo/ipn  (MoMo tự callback khi hoàn thành)
    ▼
Ktor Server (BE) – cập nhật trạng thái vào memory


# Hình thức thanh toán:
- App MoMo: FE lấy `payUrl` → mở deeplink vào app MoMo UAT trên điện thoại
- Quét QR: BE tạo QR từ `payUrl` → FE hiển thị ảnh QR → người dùng quét bằng MoMo UAT

---

# Backend – momo_be

### Cấu trúc file


momo_be/src/main/kotlin/
├── Application.kt     # Entry point, khởi động server
├── MomoRoutes.kt      # Toàn bộ logic thanh toán MoMo
├── Routing.kt         # Cấu hình routing chung, health check
├── HTTP.kt            # Cấu hình CORS và default headers
├── Security.kt        # Cấu hình bảo mật
├── Serialization.kt   # Cấu hình JSON serialization
└── Monitoring.kt      # Cấu hình logging


### `Application.kt` – Entry point


fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureSecurity()
    configureRouting()   // ← Routing.kt gọi momoRoutes() bên trong
}

Server khởi động bằng Netty engine, tự động load config từ `application.yaml`.

### `MomoRoutes.kt` – File chính

#### Data Classes (Models)

| Class | Mục đích |
|---|---|
| `MomoRequest` | Request body cho endpoint `/api/momo/pay` |
| `MomoResponse` | Response trả về `payUrl` cho thanh toán App |
| `MomoQrRequest` | Request body cho endpoint `/api/momo/qr` |
| `MomoQrResponse` | Response trả về URL ảnh QR và `orderId` |
| `PaymentStatusResponse` | Response trạng thái giao dịch |
| `PaymentRecord` | Dữ liệu giao dịch lưu trong bộ nhớ |
| `IpnResponse` | Response trả về cho MoMo khi nhận IPN |
| `ConfirmResponse` | Response xác nhận thủ công |

#### Storage

```kotlin
val paymentStorage = ConcurrentHashMap<String, PaymentRecord>()
```

Lưu trữ trạng thái giao dịch **trong bộ nhớ** (demo). Mỗi `PaymentRecord` lưu:
- `orderId`, `status` (`pending` / `success` / `failed`)
- `amount`, `description`, `qrData`, `transId`, `completedAt`

* Data mất khi server restart. Production nên dùng database.

---

#### Các API Endpoint

##### `POST /api/momo/pay` – Thanh toán qua App MoMo

FE gửi: { amount, orderInfo, redirectUrl, ipnUrl }
    ↓
BE tạo orderId, ký HMAC-SHA256
    ↓
BE gọi MoMo API → nhận payUrl
    ↓
BE trả về payUrl cho FE
    ↓
FE mở deeplink → App MoMo UAT

**Quy trình tạo chữ ký (quan trọng):**

// Chuỗi phải đúng thứ tự alphabet theo tên field
val rawSignature = "accessKey=$accessKey&amount=${momoReq.amount}" +
    "&extraData=$extraData&ipnUrl=${momoReq.ipnUrl}" +
    "&orderId=$orderId&orderInfo=${momoReq.orderInfo}" +
    "&partnerCode=$partnerCode&redirectUrl=${momoReq.redirectUrl}" +
    "&requestId=$requestId&requestType=$requestType"

val signature = hmacSHA256(rawSignature, secretKey)



##### `POST /api/momo/qr` – Tạo mã QR thanh toán

FE gửi: { amount, orderInfo, description, paymentMethod }
    ↓
BE tạo orderId dạng "QR_<timestamp>", ký HMAC-SHA256
    ↓
BE lưu PaymentRecord vào storage (status: "pending")
    ↓
BE gọi MoMo API (requestType = "captureWallet") → nhận payUrl
    ↓
BE tạo URL ảnh QR từ payUrl:
    https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=<payUrl>
    ↓
BE cập nhật storage với qrData = payUrl
    ↓
BE trả về { qrCodeUrl, orderId, qrData }

- `description` từ FE được dùng làm `orderInfo` (nội dung hiển thị trong lịch sử MoMo)
- `requestType = "captureWallet"` → yêu cầu thanh toán qua ví MoMo


##### `GET /api/momo/status/{orderId}` – Kiểm tra trạng thái

// FE gọi mỗi 3 giây để polling trạng thái
GET /api/momo/status/QR_1234567890

// Response:
{
  "orderId": "QR_1234567890",
  "status": "pending",   // or "success" / "failed"
  "amount": "10000",
  "description": "NGUYENVANA",
  "transId": "3081234567",
  "completedAt": "1713012345678"
}


##### `POST /api/momo/ipn` – Nhận callback từ MoMo

MoMo tự động gọi endpoint này sau khi người dùng thanh toán:

// MoMo gửi JSON với resultCode = 0 nếu thành công
val resultCode = jsonData["resultCode"]?.jsonPrimitive?.intOrNull

if (resultCode == 0) {
    payment.status = "success"
    payment.transId = transId
    payment.completedAt = System.currentTimeMillis().toString()
}

// BE phải trả về đúng format này để MoMo xác nhận đã nhận
call.respondText("""{"message":"OK","resultCode":0}""", ...)

*  Endpoint này phải có URL public (ngrok) vì MoMo gọi từ server của họ.


##### `POST /api/momo/confirm/{orderId}` – Xác nhận thủ công (testing)

Endpoint phụ trợ để test: giả lập cập nhật trạng thái `success` mà không cần MoMo callback thực. Dùng khi debug.

---

#### `hmacSHA256()` – Hàm ký

fun hmacSHA256(data: String, key: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(UTF_8), "HmacSHA256"))
    return mac.doFinal(data.toByteArray(UTF_8))
        .joinToString("") { "%02x".format(it) }
}


Trả về chuỗi hex thường (lowercase). MoMo yêu cầu chữ ký này để xác thực request đến từ đúng partner.

---

#### Credentials MoMo Sandbox (đã có sẵn)


partnerCode = "MOMO"
accessKey   = "F8BBA842ECF85"
secretKey   = "K951B6PE1waDMi640xX08PD3vg6EkVlz"
endpoint    = "https://test-payment.momo.vn/v2/gateway/api/create"


* Đây là credentials test do MoMo cung cấp công khai. Production cần thay bằng credentials thật.

---

## Frontend – `testMomo`

### Cấu trúc file

testMomo/app/src/main/java/com/example/testmomo/
├── MainActivity.kt      # Activity chính, xử lý deeplink callback
├── PayScreen.kt         # UI thanh toán (Jetpack Compose)
└── requestPayUrl.kt     # Các hàm gọi API backend

---

### `requestPayUrl.kt` – Gọi API Backend

File chứa tất cả hàm network, dùng **OkHttp** để gọi API.


#### `requestPayUrl()` – Thanh toán App MoMo

fun requestPayUrl(context, amount, callback: (payUrl, error) -> Unit)

- Gọi `POST $url/api/momo/pay`
- Nhận `payUrl` từ response
- Trả về `payUrl` qua callback → FE dùng để mở app MoMo

#### `requestQrUrl()` – Tạo QR thanh toán

fun requestQrUrl(context, amount, description, paymentMethod, callback: (qrCodeUrl, orderId, error) -> Unit)

- Gọi `POST $url/api/momo/qr`
- Gửi kèm `description` (nội dung chuyển khoản người dùng nhập)
- Nhận `qrCodeUrl` (URL ảnh QR hiển thị trực tiếp bằng Coil) và `orderId`
- `orderId` dùng để polling trạng thái sau đó

#### `checkPaymentStatus()` – Kiểm tra trạng thái

fun checkPaymentStatus(context, orderId, callback: (status, transId, error) -> Unit)

- Gọi `GET $url/api/momo/status/$orderId`
- Trả về `status`: `"pending"`, `"success"`, `"failed"`

#### `openMomoApp()` – Mở app MoMo

fun openMomoApp(context: Context, payUrl: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payUrl))
    context.startActivity(intent)
}


Dùng deeplink để mở trực tiếp app MoMo UAT đã cài trên điện thoại.

---

### `PayScreen.kt` – UI thanh toán (Compose)

Màn hình chính với 2 chế độ thanh toán:

#### State quản lý

| State | Mô tả |
|---|---|
| `amount` | Số tiền người dùng nhập |
| `description` | Nội dung chuyển khoản (chỉ dùng cho QR) |
| `selectedMethod` | `0` = App MoMo, `1` = Quét QR |
| `qrImageUrl` | URL ảnh QR để hiển thị |
| `currentOrderId` | orderId của giao dịch QR đang chờ |
| `paymentStatus` | `pending` / `success` / `failed` |
| `loading` | Hiển thị loading indicator |

#### Auto-polling trạng thái QR

LaunchedEffect(currentOrderId) {
    // Cứ 3 giây gọi checkPaymentStatus một lần
    while (currentOrderId != null && paymentStatus != "success") {
        delay(3000)
        checkPaymentStatus(context, currentOrderId!!) { status, transId, err ->
            paymentStatus = status
        }
    }
}

Khi có `orderId`, FE tự động kiểm tra trạng thái mỗi 3 giây cho đến khi `status = "success"`.

#### Luồng UI

Nhập số tiền
    ↓
Chọn phương thức (App / QR)
    ↓
[App MoMo]                      [Quét QR]
Nhấn "Thanh toán"               Nhập nội dung → Nhấn "Tạo mã QR"
    ↓                               ↓
requestPayUrl()                 requestQrUrl()
    ↓                               ↓
openMomoApp() → mở app          Hiển thị ảnh QR
    ↓                               ↓ (polling mỗi 3s)
Callback deeplink               checkPaymentStatus()
    ↓                               ↓
momoSuccess = true              paymentStatus = "success"
    ↓                               ↓
Hiển thị thành công             Hiển thị thành công

---

### `MainActivity.kt` – Xử lý deeplink

Dùng cho hình thức thanh toán **App MoMo**: sau khi người dùng thanh toán xong trên app MoMo, app redirect về `momoapp://callback`.

private fun handleMomoCallback(intent: Intent?) {
    intent?.data?.let { uri ->
        // Kiểm tra scheme và host của deeplink
        if (uri.scheme == "momoapp" && uri.host == "callback") {
            momoSuccess = true  // Cập nhật state để hiển thị thông báo thành công
        }
    }
}

Deeplink `momoapp://callback` được khai báo trong `AndroidManifest.xml` để Android biết mở app này khi MoMo redirect về.

---

# Tóm tắt các file cần quan tâm

| File | Vị trí | Sửa gì |
|---|---|---|
| `MomoRoutes.kt` | `momo_be/src/main/kotlin/` | Dòng 171 – sửa `ipnUrl` ngrok |
| `requestPayUrl.kt` | `testMomo/.../testmomo/` | Dòng 15 – sửa biến `url` ngrok |