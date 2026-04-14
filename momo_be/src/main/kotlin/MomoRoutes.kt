
package com.example

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MomoRequest(
    val amount: String,
    val orderInfo: String,
    val redirectUrl: String,
    val ipnUrl: String
)

@Serializable
data class MomoResponse(
    val payUrl: String? = null,
    val resultCode: Int? = null,
    val message: String? = null
)

@Serializable
data class MomoQrRequest(
    val amount: String,
    val orderInfo: String,
    val description: String? = null,  // Nội dung người dùng tự nhập
    val paymentMethod: String? = null  // "app" = payWithATM (QR), "card" = captureWallet (Thẻ test)
)

@Serializable
data class MomoQrResponse(
    val qrCodeUrl: String? = null,
    val orderId: String? = null,
    val qrData: String? = null,
    val resultCode: Int? = null,
    val message: String? = null
)

@Serializable
data class PaymentStatusResponse(
    val orderId: String,
    val status: String,  // "pending", "success", "failed"
    val amount: String? = null,
    val description: String? = null,
    val transId: String? = null,
    val completedAt: String? = null,
    val message: String? = null
)

@Serializable
data class IpnResponse(
    val message: String,
    val resultCode: Int
)

@Serializable
data class ConfirmResponse(
    val message: String,
    val orderId: String,
    val status: String
)

@Serializable
data class ErrorResponse(
    val message: String
)

// In-memory storage cho demo (production nên dùng database)
data class PaymentRecord(
    var orderId: String,
    var status: String = "pending",
    var amount: String,
    var description: String,
    var qrData: String,
    var transId: String? = null,
    var completedAt: String? = null,
    var createdAt: Long = System.currentTimeMillis()
)

val paymentStorage = ConcurrentHashMap<String, PaymentRecord>()

fun Application.momoRoutes() {
    routing {
        // Endpoint thanh toán bằng app MoMo
        post("/api/momo/pay") {
            val momoReq = call.receive<MomoRequest>()
            val partnerCode = "MOMO"
            val accessKey = "F8BBA842ECF85"
            val secretKey = "K951B6PE1waDMi640xX08PD3vg6EkVlz"
            val requestType = "captureWallet"
            val requestId = partnerCode + System.currentTimeMillis()
            val orderId = requestId
            val extraData = ""

            val rawSignature = "accessKey=$accessKey&amount=${momoReq.amount}&extraData=$extraData&ipnUrl=${momoReq.ipnUrl}&orderId=$orderId&orderInfo=${momoReq.orderInfo}&partnerCode=$partnerCode&redirectUrl=${momoReq.redirectUrl}&requestId=$requestId&requestType=$requestType"
            val signature = hmacSHA256(rawSignature, secretKey)

            val requestBody = buildJsonObject {
                put("partnerCode", partnerCode)
                put("accessKey", accessKey)
                put("requestId", requestId)
                put("amount", momoReq.amount)
                put("orderId", orderId)
                put("orderInfo", momoReq.orderInfo)
                put("redirectUrl", momoReq.redirectUrl)
                put("ipnUrl", momoReq.ipnUrl)
                put("extraData", extraData)
                put("requestType", requestType)
                put("signature", signature)
                put("lang", "vi")
            }

            val client = HttpClient(CIO)
            try {
                val momoRes: HttpResponse = client.post("https://test-payment.momo.vn/v2/gateway/api/create") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(requestBody))
                }
                val responseText = momoRes.bodyAsText()
                val jsonRes = Json.parseToJsonElement(responseText).jsonObject
                val payUrl = jsonRes["payUrl"]?.jsonPrimitive?.contentOrNull
                val resultCode = jsonRes["resultCode"]?.jsonPrimitive?.intOrNull
                val message = jsonRes["message"]?.jsonPrimitive?.contentOrNull
                call.respond(MomoResponse(payUrl, resultCode, message))
            } catch (e: Exception) {
                call.respond(MomoResponse(message = e.message))
            } finally {
                client.close()
            }
        }

        // Endpoint tạo QR code MoMo - GỌI API MOMO THẬT giống Java
        post("/api/momo/qr") {
            try {
                val qrReq = call.receive<MomoQrRequest>()
                println("=== Creating MOMO QR Payment ===")
                println("Amount: ${qrReq.amount}")
                println("Description: ${qrReq.description}")

                // Config MoMo
                val partnerCode = "MOMO"
                val accessKey = "F8BBA842ECF85"
                val secretKey = "K951B6PE1waDMi640xX08PD3vg6EkVlz"
                val momoEndpoint = "https://test-payment.momo.vn/v2/gateway/api/create"

                // Tạo orderId và requestId unique
                val orderId = "QR_${System.currentTimeMillis()}"
                val requestId = "REQ_${System.currentTimeMillis()}"
                val amount = qrReq.amount.toLongOrNull() ?: 0L

                // Sử dụng description từ user làm orderInfo (nội dung chuyển khoản)
                val orderInfo = qrReq.description?.trim()?.takeIf { it.isNotEmpty() }
                    ?: qrReq.orderInfo

                val redirectUrl = "momoapp://callback"
                //
                //
                //
                val ipnUrl = "https://bb95-1-55-241-30.ngrok-free.app/api/momo/ipn"
                //
                //
                //

                // XÁC ĐỊNH requestType
                // captureWallet = Thanh toán qua ví MoMo (QR nhảy thẳng vào app)
                val requestType = "captureWallet"
                val extraData = ""

                println("OrderInfo (nội dung CK): $orderInfo")
                println("PaymentMethod: ${qrReq.paymentMethod}")
                println("Using requestType: $requestType")

                // Tạo raw signature giống Java
                val rawSignature = "accessKey=$accessKey" +
                        "&amount=$amount" +
                        "&extraData=$extraData" +
                        "&ipnUrl=$ipnUrl" +
                        "&orderId=$orderId" +
                        "&orderInfo=$orderInfo" +
                        "&partnerCode=$partnerCode" +
                        "&redirectUrl=$redirectUrl" +
                        "&requestId=$requestId" +
                        "&requestType=$requestType"

                println("Raw signature: $rawSignature")

                // Tạo HMAC SHA256 signature
                val signature = hmacSHA256(rawSignature, secretKey)
                println("Signature: $signature")

                // Tạo request body
                val requestBody = buildJsonObject {
                    put("partnerCode", partnerCode)
                    put("accessKey", accessKey)
                    put("requestId", requestId)
                    put("amount", amount)
                    put("orderId", orderId)
                    put("orderInfo", orderInfo)
                    put("redirectUrl", redirectUrl)
                    put("ipnUrl", ipnUrl)
                    put("requestType", requestType)
                    put("extraData", extraData)
                    put("lang", "vi")
                    put("signature", signature)
                }

                println("Request body: ${requestBody.toString()}")

                // Lưu vào storage trước khi gọi MoMo
                paymentStorage[orderId] = PaymentRecord(
                    orderId = orderId,
                    amount = qrReq.amount,
                    description = orderInfo,
                    qrData = ""
                )

                // Gọi API MOMO
                val client = HttpClient(CIO)
                try {
                    val momoRes: HttpResponse = client.post(momoEndpoint) {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                    }

                    val responseText = momoRes.bodyAsText()
                    println("MOMO response: $responseText")

                    val jsonRes = Json.parseToJsonElement(responseText).jsonObject
                    val resultCode = jsonRes["resultCode"]?.jsonPrimitive?.intOrNull
                    val message = jsonRes["message"]?.jsonPrimitive?.contentOrNull
                    val payUrl = jsonRes["payUrl"]?.jsonPrimitive?.contentOrNull
                    val deeplink = jsonRes["deeplink"]?.jsonPrimitive?.contentOrNull

                    if (resultCode != 0 || payUrl == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            MomoQrResponse(
                                resultCode = resultCode,
                                message = message ?: "Lỗi tạo QR"
                            )
                        )
                        return@post
                    }

                    // Tạo QR code URL từ payUrl
                    // Cách 1: Dùng API QR generator với payUrl
                    val qrCodeImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=${java.net.URLEncoder.encode(payUrl, "UTF-8")}"

                    // Cập nhật storage với response từ MoMo
                    paymentStorage[orderId]?.let { record ->
                        record.qrData = payUrl
                        paymentStorage[orderId] = record
                    }

                    // Trả về response
                    call.respond(
                        MomoQrResponse(
                            qrCodeUrl = qrCodeImageUrl,  // QR code image của payUrl
                            orderId = orderId,
                            qrData = payUrl,  // payUrl gốc từ MoMo
                            resultCode = resultCode,
                            message = "QR được tạo thành công với số tiền $amount VND và nội dung: $orderInfo. Quét QR hoặc mở link để thanh toán."
                        )
                    )
                } catch (e: Exception) {
                    println("Error calling MOMO API: ${e.message}")
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        MomoQrResponse(
                            resultCode = 99,
                            message = "Lỗi kết nối MoMo: ${e.message}"
                        )
                    )
                } finally {
                    client.close()
                }

            } catch (e: Exception) {
                println("Error in QR endpoint: ${e.message}")
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.BadRequest,
                    MomoQrResponse(
                        resultCode = 1,
                        message = "Yêu cầu không hợp lệ: ${e.message}"
                    )
                )
            }
        }

        // Endpoint kiểm tra trạng thái thanh toán
        // Endpoint kiểm tra trạng thái thanh toán
        get("/api/momo/status/{orderId}") {
            try {
                val orderId = call.parameters["orderId"]
                if (orderId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PaymentStatusResponse(
                            orderId = "",
                            status = "error",
                            message = "Thiếu orderId"
                        )
                    )
                    return@get
                }

                val payment = paymentStorage[orderId]
                if (payment == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        PaymentStatusResponse(
                            orderId = orderId,
                            status = "not_found",
                            message = "Không tìm thấy giao dịch"
                        )
                    )
                    return@get
                }

                call.respond(
                    PaymentStatusResponse(
                        orderId = payment.orderId,
                        status = payment.status,
                        amount = payment.amount,
                        description = payment.description,
                        transId = payment.transId,
                        completedAt = payment.completedAt,
                        message = when(payment.status) {
                            "success" -> "Thanh toán thành công"
                            "pending" -> "Đang chờ thanh toán"
                            "failed" -> "Thanh toán thất bại"
                            else -> null
                        }
                    )
                )
            } catch (e: Exception) {
                println("Error checking status: ${e.message}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    PaymentStatusResponse(
                        orderId = "",
                        status = "error",
                        message = "Lỗi: ${e.message}"
                    )
                )
            }
        }

        // IPN callback endpoint - MoMo sẽ gọi khi thanh toán thành công

        post("/api/momo/ipn") {
            try {
                val ipnData = call.receiveText()
                println("=== MoMo IPN received ===")
                println(ipnData)

                val jsonData = Json.parseToJsonElement(ipnData).jsonObject
                val orderId = jsonData["orderId"]?.jsonPrimitive?.contentOrNull
                val resultCode = jsonData["resultCode"]?.jsonPrimitive?.intOrNull
                val transId = jsonData["transId"]?.jsonPrimitive?.contentOrNull

                println("OrderId: $orderId, ResultCode: $resultCode, TransId: $transId")

                if (orderId != null) {
                    val payment = paymentStorage[orderId]
                    if (payment != null) {
                        if (resultCode == 0) {
                            payment.status = "success"
                            payment.transId = transId
                            payment.completedAt = System.currentTimeMillis().toString()
                            println("✓ Payment $orderId marked as SUCCESS")
                        } else {
                            payment.status = "failed"
                            println("✗ Payment $orderId marked as FAILED (resultCode: $resultCode)")
                        }
                        paymentStorage[orderId] = payment
                    } else {
                        println("✗ Payment not found in storage: $orderId")
                    }
                }

                call.respondText(
                    "{\"message\":\"OK\",\"resultCode\":0}",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                println("✗ Error in IPN: ${e.message}")
                e.printStackTrace()
                call.respondText(
                    "{\"message\":\"Error\",\"resultCode\":99}",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            }
        }


        // Endpoint xác nhận thanh toán thủ công (cho demo/testing)
        post("/api/momo/confirm/{orderId}") {
            try {
                val orderId = call.parameters["orderId"]
                if (orderId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Thiếu orderId"))
                    return@post
                }

                val payment = paymentStorage[orderId]
                if (payment == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(message = "Không tìm thấy giao dịch"))
                    return@post
                }

                payment.status = "success"
                payment.transId = "MANUAL_${System.currentTimeMillis()}"
                payment.completedAt = System.currentTimeMillis().toString()
                paymentStorage[orderId] = payment

                call.respond(HttpStatusCode.OK, ConfirmResponse(
                    message = "Đã xác nhận thanh toán thành công",
                    orderId = orderId,
                    status = "success"
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(message = e.message ?: "Lỗi không xác định"))
            }
        }
    }
}

fun hmacSHA256(data: String, key: String): String {
    val algorithm = "HmacSHA256"
    val mac = Mac.getInstance(algorithm)
    val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm)
    mac.init(secretKeySpec)
    return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}