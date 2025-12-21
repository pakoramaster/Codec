package com.example.codec

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import com.example.codec.ui.theme.MGS
import java.util.concurrent.TimeUnit
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        requestNotificationPermission()
        NotificationUtils.createChannel(this)

        val prefs = getSharedPreferences("leetcode_prefs", Context.MODE_PRIVATE)
        val savedUsername = prefs.getString("username", "") ?: ""

        if (savedUsername.isNotBlank()) {
            scheduleWorker(savedUsername)
        }

        setContent {
            MainScreen(
                username = savedUsername,
                onSave = { newUsername ->
                    prefs.edit().putString("username", newUsername).apply()
                    scheduleWorker(newUsername)
                }
            )
        }
    }

    private fun scheduleWorker(username: String) {
        val workData = Data.Builder()
            .putString("username", username)
            .build()

        val request = PeriodicWorkRequestBuilder<LeetCodeWorker>(
            2, TimeUnit.HOURS,
        ).setInputData(workData)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "leetcode_checker",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun MainScreen(
    username: String,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(username) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(ImageDecoderDecoder.Factory())
        }
        .build()

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val imageLoader = ImageLoader.Builder(LocalContext.current)
                .components { add(ImageDecoderDecoder.Factory()) }
                .build()

            LeetCodeSnakeGif(
                imageLoader = imageLoader,
                imageRes = R.drawable.snake_blink,
                imageWidth = 160.dp,
                extraGlowPadding = 40.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            NeonTextField(
                value = text,
                onValueChange = { text = it },
                fontFamily = MGS,
                labelText = "LeetCode Username"
            )

            Spacer(modifier = Modifier.height(16.dp))

            CompactNeonButton(
                text = "Save",
                fontFamily = MGS,
                onClick = {
                    if (text.isNotBlank()) {
                        onSave(text)
                        // show snackbar or do other logic here
                    }
                }
            )
        }
    }
}

@Composable
fun GreenPhosphorBloom(extraPadding: Dp = 0.dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val paddingPx = extraPadding.toPx()
        val widthWithPadding = size.width + paddingPx * 2
        val heightWithPadding = size.height + paddingPx * 2

        val aspectRatio = 256f / 438f
        val rectWidth: Float
        val rectHeight: Float
        if (widthWithPadding / heightWithPadding > aspectRatio) {
            rectHeight = heightWithPadding * 0.8f
            rectWidth = rectHeight * aspectRatio
        } else {
            rectWidth = widthWithPadding * 0.8f
            rectHeight = rectWidth / aspectRatio
        }

        val topLeft = Offset(
            (size.width - rectWidth) / 2f,
            (size.height - rectHeight) / 2f
        )

        val layers = 6
        val growthFactor = 0.12f

        for (i in 0..layers) {
            val progress = i / layers.toFloat()
            val layerWidth = rectWidth * (1f + progress * growthFactor)
            val layerHeight = rectHeight * (1f + progress * growthFactor)

            drawRoundRect(
                color = Color(0xFF4AFF7A).copy(alpha = 0.15f * (1f - progress)),
                topLeft = topLeft - Offset((layerWidth - rectWidth) / 2f, (layerHeight - rectHeight) / 2f),
                size = Size(layerWidth, layerHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.5.dp.toPx()) // adjust radius
            )
        }
    }
}

@Composable
fun LeetCodeSnakeGif(
    imageLoader: ImageLoader,
    imageRes: Int,
    imageWidth: Dp = 160.dp,
    extraGlowPadding: Dp = 40.dp
) {
    val imageAspectRatio = 256f / 438f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(imageWidth)
            .aspectRatio(imageAspectRatio)
    ) {
        // Bloom behind the GIF
        GreenPhosphorBloom(extraPadding = extraGlowPadding)

        // GIF itself
        AsyncImage(
            model = imageRes,
            contentDescription = "Snake",
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize()
        )

        // Animated CRT overlay on top
        AnimatedDitherOverlay(modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun AnimatedDitherOverlay(modifier: Modifier = Modifier) {
    // Animate yOffset in composable
    val stepDp = 1.5.dp
    val infiniteTransition = rememberInfiniteTransition()
    val yOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f, // this will be multiplied by step in DrawScope
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier) {
        val stepPx = stepDp.toPx()
        val actualOffset = yOffset * stepPx

        val brightGreen = Color(0xFF6BFF9A)
        val glowGreen = Color(0xFF4AFF7A)

        // Define the "inner rectangle" as the GIF area
        val edgePadding = 1.0.dp.toPx()          // distance from GIF to phosphor edge

        val topLeft = Offset(edgePadding, edgePadding)
        val sizeRect = Size(
            size.width - 2 * edgePadding,
            size.height - 2 * edgePadding
        )

        val phosphorWidth = 2.dp.toPx()   // width of the phosphor edge

        // --- Phosphor edge (draw on top) ---
        drawRect(
            color = brightGreen.copy(alpha = 0.7f),
            topLeft = topLeft,
            size = sizeRect,
            style = Stroke(width = phosphorWidth)
        )

        // --- Animated vertical scanlines ---
        val rows = (size.height / stepPx).toInt()
        for (y in 0 until rows step 3) {
            drawRect(
                color = glowGreen.copy(alpha = 0.05f),
                topLeft = Offset(0f, (y * stepPx + actualOffset) % size.height),
                size = Size(size.width, stepPx)
            )
        }
    }
}
@Composable
fun NeonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    labelText: String
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = fontFamily,
            fontSize = 24.sp,
            color = Color(0xFF6BFF9A)
        ),
        label = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelText,
                    fontFamily = fontFamily,
                    fontSize = 20.sp, // larger label
                    color = Color(0xFF4AFF7A),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color(0xFF6BFF9A),
            unfocusedTextColor = Color(0xFF6BFF9A),
            focusedContainerColor = Color.Black,
            unfocusedContainerColor = Color.Black,
            cursorColor = Color(0xFF6BFF9A),
            focusedIndicatorColor = Color(0xFF6BFF9A),
            unfocusedIndicatorColor = Color(0xFF4AFF7A),
            focusedLabelColor = Color(0xFF6BFF9A),
            unfocusedLabelColor = Color(0xFF4AFF7A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 50.dp) // optional horizontal padding
    )
}

@Composable
fun CompactNeonButton(
    text: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    onClick: () -> Unit
) {
    val brightGreen = Color(0xFF6BFF9A)
    val glowGreen = Color(0xFF4AFF7A)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .border(
                width = 2.dp,
                brush = SolidColor(if (isPressed) brightGreen else glowGreen),
                shape = RoundedCornerShape(2.dp)
            )
            .background(if (isPressed) glowGreen.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null // optional: remove ripple
            ) { onClick() }
            .padding(bottom = 3.dp, start = 13.dp, end = 12.dp)

    ) {
        Text(
            text = text,
            fontFamily = fontFamily,
            fontSize = 24.sp,
            color = if (isPressed) brightGreen else glowGreen
        )
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    MainScreen(username = "sampleUser") {}
}
