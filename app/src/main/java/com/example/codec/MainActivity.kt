package com.example.codec

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yourpackage.codec.LeetCodeWorker
import com.yourpackage.codec.NotificationUtils
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        requestNotificationPermission()

        NotificationUtils.createChannel(this)

        val prefs = getSharedPreferences("leetcode_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        if (username.isNotBlank()) {
            scheduleWorker(username)
        }

        setContent {
            MainScreen(username) { newUsername ->
                prefs.edit().putString("username", newUsername).apply()
                scheduleWorker(newUsername)
            }
        }
    }

    private fun scheduleWorker(username: String) {
        val workData = Data.Builder()
            .putString("username", username)
            .build()

        val request = PeriodicWorkRequestBuilder<LeetCodeWorker>(
            15, TimeUnit.MINUTES
        ).setInputData(workData).build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "leetcode_checker",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
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

@Composable
fun MainScreen(username: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(username) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("LeetCode Username") }
            )
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSave(text)
                        scope.launch {
                            snackbarHostState.showSnackbar("Username Saved! Worker scheduled.")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF32CD32))
            ) {
                Text("Save")
            }
        }
    }
}
