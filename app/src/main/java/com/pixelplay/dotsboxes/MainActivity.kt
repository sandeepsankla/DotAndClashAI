package com.pixelplay.dotsboxes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pixelplay.dotsboxes.presentation.navigation.AppNavigation
import com.pixelplay.dotsboxes.presentation.theme.DotsBoxesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DotsBoxesTheme {
                AppNavigation()
            }
        }
    }
}
