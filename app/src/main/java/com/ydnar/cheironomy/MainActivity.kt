package com.ydnar.cheironomy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ydnar.cheironomy.ui.HomeScreen
import com.ydnar.cheironomy.ui.theme.CheironomyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheironomyTheme {
                HomeScreen()
            }
        }
    }
}
