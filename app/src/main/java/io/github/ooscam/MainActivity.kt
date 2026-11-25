package io.github.ooscam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppTitle()
            }
        }
    }
}

@Composable
fun AppTitle() {
    Text(text = APP_TITLE)
}

const val APP_TITLE = "OOS Cam"
