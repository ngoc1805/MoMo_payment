

package com.example.testmomo

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

// Giao dịch bằng app MoMo

val url = "https://bb95-1-55-241-30.ngrok-free.app"
fun requestPayUrl(context: Context, amount: String, callback: (payUrl: String?, error: String?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val client = OkHttpClient()
            val json = JSONObject()
            json.put("amount", amount)
            json.put("orderInfo", "Thanh toán MoMo app")
            json.put("redirectUrl", "momoapp://callback")
            json.put("ipnUrl", "$url/api/momo/ipn")
            val body = RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                json.toString()
            )
            val request = Request.Builder()
                .url("$url/api/momo/pay")
                .post(body)
                .build()
            val res = client.newCall(request).execute()
            val resBody = res.body?.string()
            val obj = JSONObject(resBody ?: "")
            val payUrl = obj.optString("payUrl", null)
            val resultCode = obj.optInt("resultCode", -1)
            if (payUrl != null && resultCode == 0) {
                withContext(Dispatchers.Main) { callback(payUrl, null) }
            } else {
                val msg = obj.optString("message", "Không lấy được payUrl")
                withContext(Dispatchers.Main) { callback(null, msg) }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { callback(null, e.message) }
        }
    }
}

// Tạo link thanh toán MoMo - Trả về qrCodeUrl (ảnh QR) để hiển thị
fun requestQrUrl(
    context: Context,
    amount: String,
    description: String,
    paymentMethod: String = "card",  // "card" = form nhập thẻ, "app" = QR code
    callback: (qrCodeUrl: String?, orderId: String?, error: String?) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val client = OkHttpClient()
            val json = JSONObject()
            json.put("amount", amount)
            json.put("orderInfo", "Thanh toán MoMo")
            json.put("description", description)
            json.put("paymentMethod", paymentMethod)  // Thêm paymentMethod

            val body = RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                json.toString()
            )
            val request = Request.Builder()
                .url("$url/api/momo/qr")
                .post(body)
                .build()
            val res = client.newCall(request).execute()
            val resBody = res.body?.string()
            val obj = JSONObject(resBody ?: "")

            // SỬA LẠI: Lấy qrCodeUrl (URL ảnh QR) thay vì qrData
            val qrCodeUrl = obj.optString("qrCodeUrl", null)  // ĐÂY LÀ ĐIỂM QUAN TRỌNG!
            val orderId = obj.optString("orderId", null)
            val resultCode = obj.optInt("resultCode", -1)

            if (qrCodeUrl != null && resultCode == 0) {
                withContext(Dispatchers.Main) { callback(qrCodeUrl, orderId, null) }
            } else {
                val msg = obj.optString("message", "Không lấy được link thanh toán")
                withContext(Dispatchers.Main) { callback(null, null, msg) }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { callback(null, null, e.message) }
        }
    }
}

// Kiểm tra trạng thái thanh toán
fun checkPaymentStatus(
    context: Context,
    orderId: String,
    callback: (status: String?, transId: String?, error: String?) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$url/api/momo/status/$orderId")
                .get()
                .build()

            val res = client.newCall(request).execute()
            val resBody = res.body?.string()
            val obj = JSONObject(resBody ?: "")

            val status = obj.optString("status", null)
            val transId = obj.optString("transId", null)
            val message = obj.optString("message", null)

            withContext(Dispatchers.Main) {
                callback(status, transId, if (status == "error") message else null)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { callback(null, null, e.message) }
        }
    }
}

// Hàm mở app MoMo bằng url
fun openMomoApp(context: Context, payUrl: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payUrl))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}