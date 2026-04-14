package com.example.testmomo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.testmomo.ui.theme.TestMomoTheme

class MainActivity : ComponentActivity() {
    // Biến lưu trạng thái thanh toán
    var momoSuccess: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() nếu muốn, nhưng chỉ cần dùng trong Compose thôi!

        // Kiểm tra deeplink khi app được mở bằng intent
        handleMomoCallback(intent)

        setContent {
            TestMomoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PayScreen(momoSuccess)
                }
            }
        }
    }

    // SỬA LẠI: Intent (không phải Intent?)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMomoCallback(intent)
        setContent {
            TestMomoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PayScreen(momoSuccess)
                }
            }
        }
    }

    private fun handleMomoCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            android.util.Log.d("MOMO_DEEPLINK", "uri: $uri")
            if (uri.scheme == "momoapp" && uri.host == "callback") {
                momoSuccess = true
            }
        }
    }
}
