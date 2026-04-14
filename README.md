# MoMo Payment

Dự án tích hợp thanh toán MoMo gồm 2 phần:

- **Backend** — `momo_be` (Ktor)
- **Frontend** — `testMomo` (Android)

---

## Yêu cầu trước khi chạy

- Tải app **MoMo UAT** về điện thoại và đăng ký tài khoản: https://developers.momo.vn/v3/download
- otp mặc định là 000000 hoặc 0000 hoặc 111111 hoặc 1111, thử từng cái
- Xác thực căn cước công dân trên MoMo UAT trước khi thử nghiệm
- Hướng dẫn nạp tiền ảo: https://drive.google.com/file/d/1a_emS2Vb7ltP6ajrRxw_1Iqafr09UqPl/view?usp=sharing
- Liên kết ngân hàng gì cũng được, nhập đủ số là được, sau đó nạp tiền
- Nếu máy chạy FE chưa cài MoMo UAT, có thể thử nghiệm bằng hình thức quét QR

---

## Cấu hình URL với ngrok

Dự án dùng **ngrok** để expose server local ra internet. Sau khi chạy ngrok, sao chép URL được cấp (ví dụ: `https://bb95-1-55-241-30.ngrok-free.app`) và cập nhật vào 2 vị trí sau:

| File | Vị trí | Thay đổi |
|---|---|---|
| `MomoRoutes.kt` | Dòng 171 | Sửa `ipnUrl` thành URL ngrok |
| `requestPayUrl.kt` | Dòng 15 | Sửa biến `url` thành URL ngrok |

> URL ngrok thay đổi mỗi lần khởi động (bản free). Cần cập nhật cả 2 file mỗi lần đổi URL.

---

## Backend — `momo_be`

### Cấu trúc file

```
momo_be/src/main/kotlin/
├── Application.kt       # Entry point, khởi động server
├── MomoRoutes.kt        # Toàn bộ logic thanh toán MoMo
├── Routing.kt           # Cấu hình routing chung, health check
├── HTTP.kt              # Cấu hình CORS và default headers
├── Security.kt          # Cấu hình bảo mật
├── Serialization.kt     # Cấu hình JSON serialization
└── Monitoring.kt        # Cấu hình logging
```

### Credentials MoMo Sandbox

```
partnerCode = "MOMO"
accessKey   = "F8BBA842ECF85"
secretKey   = "K951B6PE1waDMi640xX08PD3vg6EkVlz"
endpoint    = "https://test-payment.momo.vn/v2/gateway/api/create"
```

> Đây là credentials test do MoMo cung cấp công khai. Production cần thay bằng credentials thật.

### Data Classes

| Class | Mục đích |
|---|---|
| `MomoRequest` | Request body cho `/api/momo/pay` |
| `MomoResponse` | Response trả về `payUrl` cho thanh toán App |
| `MomoQrRequest` | Request body cho `/api/momo/qr` |
| `MomoQrResponse` | Response trả về URL ảnh QR và `orderId` |
| `PaymentStatusResponse` | Response trạng thái giao dịch |
| `PaymentRecord` | Dữ liệu giao dịch lưu trong bộ nhớ |
| `IpnResponse` | Response trả về cho MoMo khi nhận IPN |
| `ConfirmResponse` | Response xác nhận thủ công |

### Storage

```kotlin
val paymentStorage = ConcurrentHashMap<String, PaymentRecord>()
```

Lưu trạng thái giao dịch trong bộ nhớ (demo). Mỗi `PaymentRecord` gồm: `orderId`, `status` (`pending` / `success` / `failed`), `amount`, `description`, `qrData`, `transId`, `completedAt`.

> Data mất khi server restart. Production nên dùng database.

### API Endpoints

#### `POST /api/momo/pay` — Thanh toán qua App MoMo

FE gửi `{ amount, orderInfo, redirectUrl, ipnUrl }`. BE tạo chữ ký HMAC-SHA256, gọi MoMo API và trả về `payUrl`. FE dùng `payUrl` để mở deeplink vào app MoMo UAT.

Chuỗi ký phải đúng thứ tự alphabet:

```kotlin
val rawSignature = "accessKey=$accessKey&amount=${momoReq.amount}" +
    "&extraData=$extraData&ipnUrl=${momoReq.ipnUrl}" +
    "&orderId=$orderId&orderInfo=${momoReq.orderInfo}" +
    "&partnerCode=$partnerCode&redirectUrl=${momoReq.redirectUrl}" +
    "&requestId=$requestId&requestType=$requestType"
```

#### `POST /api/momo/qr` — Tạo mã QR thanh toán

FE gửi `{ amount, orderInfo, description, paymentMethod }`. BE tạo `orderId` dạng `QR_<timestamp>`, lưu vào storage với `status: "pending"`, gọi MoMo API với `requestType = "captureWallet"`, tạo URL ảnh QR và trả về `{ qrCodeUrl, orderId, qrData }`.

```
https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=<payUrl>
```

#### `GET /api/momo/status/{orderId}` — Kiểm tra trạng thái

```json
{
  "orderId": "QR_1234567890",
  "status": "pending",
  "amount": "10000",
  "description": "NGUYENVANA",
  "transId": "3081234567",
  "completedAt": "1713012345678"
}
```

#### `POST /api/momo/ipn` — Nhận callback từ MoMo

MoMo tự gọi endpoint này sau khi người dùng thanh toán. BE kiểm tra `resultCode == 0` để cập nhật trạng thái `success`. Endpoint phải có URL public (ngrok) vì MoMo gọi từ server của họ.

```kotlin
// BE phải trả về đúng format này
call.respondText("""{"message":"OK","resultCode":0}""", ...)
```

#### `POST /api/momo/confirm/{orderId}` — Xác nhận thủ công

Endpoint phụ trợ để test, giả lập cập nhật trạng thái `success` mà không cần MoMo callback thực.

### Hàm ký HMAC-SHA256

```kotlin
fun hmacSHA256(data: String, key: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(UTF_8), "HmacSHA256"))
    return mac.doFinal(data.toByteArray(UTF_8))
        .joinToString("") { "%02x".format(it) }
}
```

---

## Frontend — `testMomo`

### Cấu trúc file

```
testMomo/app/src/main/java/com/example/testmomo/
├── MainActivity.kt        # Activity chính, xử lý deeplink callback
├── PayScreen.kt           # UI thanh toán (Jetpack Compose)
└── requestPayUrl.kt       # Các hàm gọi API backend
```

### requestPayUrl.kt — Gọi API Backend

Dùng **OkHttp** để gọi API.

| Hàm | Mục đích |
|---|---|
| `requestPayUrl()` | Gọi `POST /api/momo/pay`, nhận `payUrl` để mở app MoMo |
| `requestQrUrl()` | Gọi `POST /api/momo/qr`, nhận `qrCodeUrl` và `orderId` |
| `checkPaymentStatus()` | Gọi `GET /api/momo/status/$orderId`, trả về `status` |
| `openMomoApp()` | Mở deeplink `payUrl` vào app MoMo UAT |

### PayScreen.kt — UI thanh toán

Màn hình chính với 2 chế độ thanh toán.

**State quản lý:**

| State | Mô tả |
|---|---|
| `amount` | Số tiền người dùng nhập |
| `description` | Nội dung chuyển khoản (chỉ dùng cho QR) |
| `selectedMethod` | `0` = App MoMo, `1` = Quét QR |
| `qrImageUrl` | URL ảnh QR để hiển thị |
| `currentOrderId` | orderId của giao dịch QR đang chờ |
| `paymentStatus` | `pending` / `success` / `failed` |
| `loading` | Hiển thị loading indicator |

**Auto-polling trạng thái QR** — FE tự động kiểm tra trạng thái mỗi 3 giây cho đến khi `status = "success"`:

```kotlin
LaunchedEffect(currentOrderId) {
    while (currentOrderId != null && paymentStatus != "success") {
        delay(3000)
        checkPaymentStatus(context, currentOrderId!!) { status, transId, err ->
            paymentStatus = status
        }
    }
}
```

### MainActivity.kt — Xử lý deeplink

Sau khi thanh toán xong trên app MoMo, app redirect về `momoapp://callback`. Deeplink này được khai báo trong `AndroidManifest.xml`.

```kotlin
private fun handleMomoCallback(intent: Intent?) {
    intent?.data?.let { uri ->
        if (uri.scheme == "momoapp" && uri.host == "callback") {
            momoSuccess = true
        }
    }
}
```
