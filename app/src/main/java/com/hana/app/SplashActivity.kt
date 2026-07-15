package com.hana.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hana.app.core.AppContainer
import kotlinx.coroutines.delay
import kotlin.random.Random

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            enterImmersiveMode()
        } catch (_: Exception) {}

        setContent {
            SplashScreen(
                onInitStart = {
                    preInitAppContainer()
                },
                onDone = {
                    startActivitySafe()
                }
            )
        }
    }

    private fun preInitAppContainer() {
        try {
            AppContainer(applicationContext)
        } catch (e: Exception) {
            Log.e("Splash", "Pre-init AppContainer failed, will retry in MainActivity", e)
        }
    }

    private fun startActivitySafe() {
        try {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (e: Exception) {
            Log.e("Splash", "startActivity failed", e)
        } finally {
            try { finish() } catch (_: Exception) {}
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try { enterImmersiveMode() } catch (_: Exception) {}
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
private fun SplashScreen(onInitStart: () -> Unit, onDone: () -> Unit) {
    val backgroundRes = remember {
        try {
            listOf(
                R.drawable.splash_1,
                R.drawable.splash_2,
                R.drawable.splash_3,
                R.drawable.splash_4,
                R.drawable.splash_5
            ).random(Random(System.currentTimeMillis()))
        } catch (_: Exception) {
            R.drawable.splash_1
        }
    }
    var visible by remember { mutableStateOf(true) }
    var initFailed by remember { mutableStateOf(false) }
    var imageLoadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            onInitStart()
        } catch (e: Exception) {
            initFailed = true
        }
        delay(if (initFailed) 500 else 2_200)
        visible = false
        delay(400)
        try { onDone() } catch (_: Exception) {}
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 400))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageLoadFailed) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                }
            } else {
                val bgRes = try { backgroundRes } catch (_: Exception) { R.drawable.splash_1 }
                Image(
                    painter = painterResource(bgRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "BY: Vanflower / 万花",
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.65f),
                            offset = Offset(1.5f, 1.5f),
                            blurRadius = 4f
                        )
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_version_display),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.65f),
                            offset = Offset(1.5f, 1.5f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }
    }
}
