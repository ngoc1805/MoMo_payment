
package com.example.testmomo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

@Composable
fun PayScreen(momoSuccess: Boolean) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var qrImageUrl by remember { mutableStateOf<String?>(null) }
    var payUrl by remember { mutableStateOf<String?>(null) }
    var selectedMethod by remember { mutableStateOf(0) } // 0=App, 1=QR
    var currentOrderId by remember { mutableStateOf<String?>(null) }
    var paymentStatus by remember { mutableStateOf<String?>(null) }
    var checkingStatus by remember { mutableStateOf(false) }

    // Auto-check payment status khi có orderId
    LaunchedEffect(currentOrderId) {
        if (currentOrderId != null && selectedMethod == 1) {
            checkingStatus = true
            // Kiểm tra mỗi 3 giây
            while (currentOrderId != null && paymentStatus != "success") {
                delay(3000)
                checkPaymentStatus(context, currentOrderId!!) { status, transId, err ->
                    if (err == null && status != null) {
                        paymentStatus = status
                        if (status == "success") {
                            checkingStatus = false
                            // Có thể thêm sound effect hoặc animation ở đây
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = "Thanh toán MoMo",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        // Nhập số tiền
        TextField(
            value = amount,
            onValueChange = { amount = it.filter { char -> char.isDigit() } },
            label = { Text("Số tiền (VNĐ)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && paymentStatus != "success"
        )

        Spacer(Modifier.height(16.dp))

        // Nhập nội dung (chỉ hiện khi chọn QR)
        if (selectedMethod == 1) {
            TextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Nội dung chuyển khoản") },
                placeholder = { Text("VD: USER123, NGUYENVANA") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && paymentStatus != "success"
            )
            Text(
                text = "Nội dung này sẽ hiển thị trong lịch sử giao dịch MoMo của bạn",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        // Chọn phương thức
        Text("Phương thức thanh toán:", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedMethod == 0,
                onClick = {
                    if (!loading && paymentStatus != "success") {
                        selectedMethod = 0
                        qrImageUrl = null
                        currentOrderId = null
                        paymentStatus = null
                    }
                },
                enabled = !loading && paymentStatus != "success"
            )
            Text("App MoMo", Modifier.padding(start = 4.dp))

            Spacer(Modifier.width(16.dp))

            RadioButton(
                selected = selectedMethod == 1,
                onClick = {
                    if (!loading && paymentStatus != "success") {
                        selectedMethod = 1
                        payUrl = null
                        currentOrderId = null
                        paymentStatus = null
                    }
                },
                enabled = !loading && paymentStatus != "success"
            )
            Text("Quét QR", Modifier.padding(start = 4.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Button thanh toán
        Button(
            onClick = {
                error = null
                loading = true
                qrImageUrl = null
                payUrl = null
                currentOrderId = null
                paymentStatus = null

                if (selectedMethod == 0) {
                    // Giao dịch bằng app MoMo
                    requestPayUrl(context, amount) { url, err ->
                        loading = false
                        if (url != null) {
                            payUrl = url
                            openMomoApp(context, url)
                        } else {
                            error = err ?: "Lỗi không xác định"
                        }
                    }
                } else {
                    // Tạo QR code
                    if (description.isEmpty()) {
                        loading = false
                        error = "Vui lòng nhập nội dung chuyển khoản"
                        return@Button
                    }

                    requestQrUrl(context, amount, description) { qrUrl, orderId, err ->
                        loading = false
                        if (qrUrl != null && orderId != null) {
                            qrImageUrl = qrUrl
                            currentOrderId = orderId
                            paymentStatus = "pending"
                        } else {
                            error = err ?: "Không tạo được QR"
                        }
                    }
                }
            },
            enabled = !loading && amount.isNotBlank() && paymentStatus != "success" &&
                    (selectedMethod == 0 || description.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedMethod == 0) "Thanh toán MoMo App" else "Tạo mã QR MoMo")
        }

        Spacer(Modifier.height(16.dp))

        // Loading indicator
        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text("Đang xử lý...")
            }
        }

        // Error message
        error?.let {
            Card(
                backgroundColor = Color(0xFFFFEBEE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Hiển thị khi đang chuyển sang app MoMo
        if (payUrl != null) {
            Card(
                backgroundColor = Color(0xFFE3F2FD),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Đang chuyển hướng sang app MoMo...",
                    color = Color(0xFF1976D2),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Hiển thị QR code
        if (qrImageUrl != null) {
            Card(
                elevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Quét mã QR bằng app MoMo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Số tiền: $amount VNĐ",
                        color = Color.Gray
                    )
                    Text(
                        text = "Nội dung: $description",
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(16.dp))

                    Image(
                        painter = rememberAsyncImagePainter(qrImageUrl),
                        contentDescription = "QR MoMo",
                        modifier = Modifier.size(250.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    // Hiển thị trạng thái
                    when (paymentStatus) {
                        "pending" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Đang chờ thanh toán...",
                                    color = Color(0xFFFF9800)
                                )
                            }
                        }
                        "success" -> {
                            Card(
                                backgroundColor = Color(0xFFC8E6C9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "✓ Thanh toán thành công!",
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            // Reset form
                                            amount = ""
                                            description = ""
                                            qrImageUrl = null
                                            currentOrderId = null
                                            paymentStatus = null
                                            error = null
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(0xFF4CAF50)
                                        )
                                    ) {
                                        Text("Tạo giao dịch mới", color = Color.White)
                                    }
                                }
                            }
                        }
                        "failed" -> {
                            Text(
                                text = "✗ Thanh toán thất bại",
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
        }

        // Success from app callback
        if (momoSuccess) {
            Spacer(Modifier.height(16.dp))
            Card(
                backgroundColor = Color(0xFFC8E6C9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "✓ Thanh toán App MoMo thành công!",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}