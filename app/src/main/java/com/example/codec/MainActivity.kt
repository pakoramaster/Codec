package com.example.codec

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CodecGreen = Color(0xFF4AFF7A)
private val CodecBrightGreen = Color(0xFF9CFFB7)
private val CodecRed = Color(0xFFFF3B30)

private enum class CodecSection {
    COMMAND,
    MISSION,
    SUPPORT,
    RANKING
}

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        requestNotificationPermission()
        NotificationUtils.createChannel(this)

        val savedSettings = SettingsStore.load(this)
        if (savedSettings.username.isNotBlank()) scheduleWorker(savedSettings)

        setContent {
            MainScreen(
                initialSettings = savedSettings,
                onSettingsChanged = { settings ->
                    SettingsStore.save(this, settings)
                    if (settings.username.isNotBlank()) scheduleWorker(settings)
                }
            )
        }
    }

    private fun scheduleWorker(settings: UserSettings) {
        val workData = Data.Builder()
            .putString("username", settings.username)
            .putInt("daily_goal", settings.dailyGoal)
            .build()

        val request = PeriodicWorkRequestBuilder<LeetCodeWorker>(
            settings.reminderMinutes.coerceAtLeast(15L), TimeUnit.MINUTES,
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
    initialSettings: UserSettings,
    onSettingsChanged: (UserSettings) -> Unit
) {
    var activeUsername by remember { mutableStateOf(initialSettings.username) }
    var usernameText by remember { mutableStateOf(initialSettings.username) }
    var dailyGoal by remember { mutableIntStateOf(initialSettings.dailyGoal) }
    var reminderMinutes by remember { mutableLongStateOf(initialSettings.reminderMinutes) }
    var friends by remember { mutableStateOf(initialSettings.friends) }
    var friendText by remember { mutableStateOf("") }
    var leaderboardPeriod by remember { mutableStateOf(LeaderboardPeriod.DAY) }
    var leaderboardEntries by remember { mutableStateOf(emptyList<LeaderboardEntry>()) }
    var leaderboardCache by remember {
        mutableStateOf<Map<LeaderboardPeriod, List<LeaderboardEntry>>>(emptyMap())
    }
    var leaderboardCacheVersions by remember {
        mutableStateOf<Map<LeaderboardPeriod, Int>>(emptyMap())
    }
    var leaderboardLoading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var currentSection by remember { mutableStateOf(CodecSection.COMMAND) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .build()
    }

    BackHandler(enabled = currentSection != CodecSection.COMMAND) {
        currentSection = CodecSection.COMMAND
    }

    fun currentSettings(
        username: String = activeUsername,
        updatedFriends: List<String> = friends
    ) = UserSettings(
        username = username,
        dailyGoal = dailyGoal,
        reminderMinutes = reminderMinutes,
        friends = updatedFriends
    )

    LaunchedEffect(activeUsername, friends, leaderboardPeriod, refreshKey, currentSection) {
        if (currentSection != CodecSection.RANKING) return@LaunchedEffect
        val users = buildList {
            if (activeUsername.isNotBlank()) add(activeUsername)
            addAll(friends.filterNot { it.equals(activeUsername, ignoreCase = true) })
        }
        if (users.isEmpty()) {
            leaderboardEntries = emptyList()
            leaderboardLoading = false
            return@LaunchedEffect
        }

        val cachedEntries = leaderboardCache[leaderboardPeriod]
            .orEmpty()
            .associateBy { it.username.lowercase() }
        leaderboardEntries = users.map { user ->
            cachedEntries[user.lowercase()]?.copy(
                username = user,
                isCurrentUser = user.equals(activeUsername, ignoreCase = true)
            ) ?: LeaderboardEntry(
                username = user,
                submissions = 0,
                isCurrentUser = user.equals(activeUsername, ignoreCase = true),
                isPending = true
            )
        }.sortedWith(
            compareByDescending<LeaderboardEntry> { it.submissions }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.username }
        )

        val rosterKeys = users.map { it.lowercase() }.toSet()
        val cacheIsFresh = leaderboardCacheVersions[leaderboardPeriod] == refreshKey &&
            cachedEntries.keys == rosterKeys
        if (cacheIsFresh) {
            leaderboardLoading = false
            return@LaunchedEffect
        }

        leaderboardLoading = true
        val refreshedEntries = withContext(Dispatchers.IO) {
            coroutineScope {
                users.map { user ->
                    async {
                        val count = runCatching {
                            LeetCodeApi.getSubmissionCount(user, leaderboardPeriod)
                        }.getOrDefault(0)
                        LeaderboardEntry(
                            username = user,
                            submissions = count,
                            isCurrentUser = user.equals(activeUsername, ignoreCase = true)
                        )
                    }
                }.awaitAll().sortedWith(
                    compareByDescending<LeaderboardEntry> { it.submissions }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.username }
                )
            }
        }
        leaderboardEntries = refreshedEntries
        leaderboardCache = leaderboardCache + (leaderboardPeriod to refreshedEntries)
        leaderboardCacheVersions = leaderboardCacheVersions + (leaderboardPeriod to refreshKey)
        leaderboardLoading = false
    }

    val saveMission: () -> Unit = {
        scope.launch {
            val candidate = usernameText.trim()
            if (candidate.isBlank()) {
                snackbarHostState.showSnackbar("Username cannot be empty")
                return@launch
            }
            val isValid = withContext(Dispatchers.IO) {
                runCatching { LeetCodeApi.isValidUser(candidate) }.getOrDefault(false)
            }
            if (!isValid) {
                snackbarHostState.showSnackbar("Invalid or unreachable LeetCode username")
            } else {
                val updatedFriends = friends.filterNot {
                    it.equals(candidate, ignoreCase = true)
                }
                activeUsername = candidate
                friends = updatedFriends
                onSettingsChanged(
                    currentSettings(
                        username = candidate,
                        updatedFriends = updatedFriends
                    )
                )
                refreshKey++
                snackbarHostState.showSnackbar("Mission parameters saved")
            }
        }
    }

    val addFriend: () -> Unit = {
        scope.launch {
            val candidate = friendText.trim()
            when {
                candidate.isBlank() -> snackbarHostState.showSnackbar("Friend username cannot be empty")
                candidate.equals(activeUsername, ignoreCase = true) ||
                    friends.any { it.equals(candidate, ignoreCase = true) } ->
                    snackbarHostState.showSnackbar("Callsign is already on the team")
                else -> {
                    val isValid = withContext(Dispatchers.IO) {
                        runCatching { LeetCodeApi.isValidUser(candidate) }.getOrDefault(false)
                    }
                    if (!isValid) {
                        snackbarHostState.showSnackbar("Invalid or unreachable LeetCode username")
                    } else {
                        val updated = friends + candidate
                        friends = updated
                        friendText = ""
                        onSettingsChanged(currentSettings(updatedFriends = updated))
                        snackbarHostState.showSnackbar("Contact added")
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                CustomNeonSnackbar(
                    message = data.visuals.message,
                    isError = data.visuals.message.contains("Invalid") || data.visuals.message.contains("empty"),
                    fontFamily = MGS
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CodecHeader()
            Spacer(modifier = Modifier.height(14.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (currentSection) {
                    CodecSection.COMMAND -> CommandCenter(
                        imageLoader = imageLoader,
                        username = activeUsername,
                        dailyGoal = dailyGoal,
                        reminderMinutes = reminderMinutes,
                        friendCount = friends.size,
                        onNavigate = { currentSection = it }
                    )
                    CodecSection.MISSION -> MissionMenu(
                        username = usernameText,
                        onUsernameChanged = { usernameText = it },
                        dailyGoal = dailyGoal,
                        onDailyGoalChanged = { dailyGoal = it },
                        reminderMinutes = reminderMinutes,
                        onReminderChanged = { reminderMinutes = it },
                        onSave = saveMission,
                        onBack = { currentSection = CodecSection.COMMAND }
                    )
                    CodecSection.SUPPORT -> SupportMenu(
                        friendText = friendText,
                        onFriendTextChanged = { friendText = it },
                        friends = friends,
                        onAdd = addFriend,
                        onRemove = { friend ->
                            val updated = friends.filterNot { it == friend }
                            friends = updated
                            onSettingsChanged(currentSettings(updatedFriends = updated))
                        },
                        onBack = { currentSection = CodecSection.COMMAND }
                    )
                    CodecSection.RANKING -> RankingMenu(
                        selectedPeriod = leaderboardPeriod,
                        onPeriodSelected = { leaderboardPeriod = it },
                        entries = leaderboardEntries,
                        loading = leaderboardLoading,
                        onRefresh = { refreshKey++ },
                        onBack = { currentSection = CodecSection.COMMAND }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandCenter(
    imageLoader: ImageLoader,
    username: String,
    dailyGoal: Int,
    reminderMinutes: Long,
    friendCount: Int,
    onNavigate: (CodecSection) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CodecPanel(title = "FOXHOUND STATUS") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).height(166.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusValue(
                        label = "OPERATIVE",
                        value = username.ifBlank { "UNASSIGNED" }
                    )
                    StatusValue(
                        label = "NEXT CODEC",
                        value = formatReminder(reminderMinutes)
                    )
                }

                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LeetCodeSnakeGif(
                        imageLoader = imageLoader,
                        imageRes = R.drawable.snake_blink,
                        imageWidth = 82.dp,
                        extraGlowPadding = 12.dp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "141.80",
                        color = CodecBrightGreen,
                        fontFamily = MGS,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f).height(166.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.End
                ) {
                    StatusValue(
                        label = "DAILY TARGET",
                        value = dailyGoal.toString().padStart(2, '0'),
                        alignment = Alignment.End
                    )
                    StatusValue(
                        label = "SUPPORT UNIT",
                        value = friendCount.toString().padStart(2, '0'),
                        alignment = Alignment.End
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "SELECT SECURE CHANNEL",
            color = CodecGreen.copy(alpha = 0.75f),
            fontFamily = MGS,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(7.dp))
        CommandMenuButton(
            code = "01",
            title = "MISSION PARAMETERS",
            subtitle = "CALLSIGN / TARGET / REMINDER",
            onClick = { onNavigate(CodecSection.MISSION) }
        )
        Spacer(Modifier.height(8.dp))
        CommandMenuButton(
            code = "02",
            title = "SUPPORT TEAM",
            subtitle = "MANAGE FOXHOUND CONTACTS",
            onClick = { onNavigate(CodecSection.SUPPORT) }
        )
        Spacer(Modifier.height(8.dp))
        CommandMenuButton(
            code = "03",
            title = "TEAM RANKING",
            subtitle = "VIEW FIELD PERFORMANCE",
            onClick = { onNavigate(CodecSection.RANKING) }
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MissionMenu(
    username: String,
    onUsernameChanged: (String) -> Unit,
    dailyGoal: Int,
    onDailyGoalChanged: (Int) -> Unit,
    reminderMinutes: Long,
    onReminderChanged: (Long) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    SubmenuLayout(
        title = "MISSION PARAMETERS",
        subtitle = "CONFIGURE OPERATIVE OBJECTIVES",
        onBack = onBack
    ) {
        CodecPanel(title = "OPERATIVE PROFILE") {
            NeonTextField(
                value = username,
                onValueChange = onUsernameChanged,
                fontFamily = MGS,
                labelText = "LEETCODE CALLSIGN",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))
        CodecPanel(title = "DAILY OBJECTIVE") {
            SettingLabel("PROBLEMS REQUIRED BEFORE STAND-DOWN")
            GoalStepper(value = dailyGoal, onValueChange = onDailyGoalChanged)
        }

        Spacer(Modifier.height(14.dp))
        CodecPanel(title = "CODEC SCHEDULE") {
            SettingLabel("TRANSMISSION INTERVAL")
            ReminderSelector(
                selectedMinutes = reminderMinutes,
                onSelected = onReminderChanged
            )
        }

        Spacer(Modifier.height(18.dp))
        CompactNeonButton(
            text = "CONFIRM MISSION",
            fontFamily = MGS,
            onClick = onSave
        )
    }
}

@Composable
private fun SupportMenu(
    friendText: String,
    onFriendTextChanged: (String) -> Unit,
    friends: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit
) {
    SubmenuLayout(
        title = "SUPPORT TEAM",
        subtitle = "SECURE PERSONNEL CHANNEL",
        onBack = onBack
    ) {
        CodecPanel(title = "ADD OPERATIVE") {
            NeonTextField(
                value = friendText,
                onValueChange = onFriendTextChanged,
                fontFamily = MGS,
                labelText = "FRIEND CALLSIGN",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            CompactNeonButton(
                text = "ADD CONTACT",
                fontFamily = MGS,
                onClick = onAdd
            )
        }

        Spacer(Modifier.height(14.dp))
        CodecPanel(title = "ACTIVE ROSTER // ${friends.size.toString().padStart(2, '0')}") {
            if (friends.isEmpty()) {
                Text(
                    text = "NO SUPPORT OPERATIVES ASSIGNED.",
                    color = CodecGreen.copy(alpha = 0.7f),
                    fontFamily = MGS,
                    fontSize = 14.sp
                )
            } else {
                friends.forEach { friend ->
                    FriendRow(username = friend, onRemove = { onRemove(friend) })
                }
            }
        }
    }
}

@Composable
private fun RankingMenu(
    selectedPeriod: LeaderboardPeriod,
    onPeriodSelected: (LeaderboardPeriod) -> Unit,
    entries: List<LeaderboardEntry>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    SubmenuLayout(
        title = "TEAM RANKING",
        subtitle = "FOXHOUND PERFORMANCE INTEL",
        onBack = onBack
    ) {
        CodecPanel(title = "REPORTING WINDOW") {
            PeriodSelector(selected = selectedPeriod, onSelected = onPeriodSelected)
        }

        Spacer(Modifier.height(14.dp))
        CodecPanel(title = "FIELD REPORT // ${selectedPeriod.label}") {
            when {
                entries.isEmpty() -> Text(
                    if (loading) "RECEIVING TRANSMISSION..."
                    else "SAVE A CALLSIGN OR ADD CONTACTS TO BEGIN.",
                    color = CodecGreen.copy(alpha = 0.75f),
                    fontFamily = MGS,
                    fontSize = 14.sp
                )
                else -> {
                    if (loading) {
                        Text(
                            "UPDATING FIELD INTEL...",
                            color = CodecGreen.copy(alpha = 0.7f),
                            fontFamily = MGS,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    entries.forEachIndexed { index, entry ->
                        LeaderboardRow(index + 1, entry)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            CompactNeonButton(
                text = "REFRESH INTEL",
                fontFamily = MGS,
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun SubmenuLayout(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .border(1.dp, CodecGreen)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Text("< COMMAND", color = CodecBrightGreen, fontFamily = MGS, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = CodecBrightGreen, fontFamily = MGS, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = CodecGreen.copy(alpha = 0.65f), fontFamily = MGS, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StatusValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(label, color = CodecGreen.copy(alpha = 0.6f), fontFamily = MGS, fontSize = 10.sp)
        Text(
            value.uppercase(),
            color = CodecBrightGreen,
            fontFamily = MGS,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun CommandMenuButton(code: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(Color(0xFF031108))
            .border(1.dp, CodecGreen.copy(alpha = 0.75f), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(code, color = CodecRed, fontFamily = MGS, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = CodecBrightGreen, fontFamily = MGS, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = CodecGreen.copy(alpha = 0.6f), fontFamily = MGS, fontSize = 10.sp)
        }
        Text(">", color = CodecBrightGreen, fontFamily = MGS, fontSize = 22.sp)
    }
}

private fun formatReminder(minutes: Long): String = when {
    minutes < 60 -> "${minutes}M"
    minutes % 60L == 0L -> "${minutes / 60}H"
    else -> "${minutes / 60}H ${minutes % 60}M"
}

@Composable
private fun CodecHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(width = 152.dp, height = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val plateWidth = 134.dp.toPx()
                val plateHeight = 32.dp.toPx()
                val centerX = size.width / 2f
                val centerY = size.height / 2f

                for (layer in 4 downTo 1) {
                    val expansion = (layer * 2).dp.toPx()
                    drawRoundRect(
                        color = CodecGreen.copy(alpha = 0.035f * (5 - layer)),
                        topLeft = Offset(
                            centerX - plateWidth / 2f - expansion,
                            centerY - plateHeight / 2f - expansion / 2f
                        ),
                        size = Size(plateWidth + expansion * 2f, plateHeight + expansion),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                    )
                }

                val plateTopLeft = Offset(
                    centerX - plateWidth / 2f,
                    centerY - plateHeight / 2f
                )
                drawRoundRect(
                    color = Color(0xFF4FAD72).copy(alpha = 0.9f),
                    topLeft = plateTopLeft,
                    size = Size(plateWidth, plateHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = CodecBrightGreen.copy(alpha = 0.55f),
                    topLeft = plateTopLeft,
                    size = Size(plateWidth, plateHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            Text(
                text = "CODEC",
                color = Color(0xFFE0FFE8),
                fontFamily = MGS,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun CodecPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CodecGreen.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            .background(Color(0xFF031108))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(CodecRed))
            Spacer(Modifier.width(7.dp))
            Text(
                text = title,
                color = CodecBrightGreen,
                fontFamily = MGS,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
        Box(
            Modifier
                .padding(top = 5.dp, bottom = 14.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(CodecGreen.copy(alpha = 0.45f))
        )
        content()
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        color = CodecGreen,
        fontFamily = MGS,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun GoalStepper(value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniCodecButton("−", enabled = value > 1) { onValueChange((value - 1).coerceAtLeast(1)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.toString().padStart(2, '0'),
                color = CodecBrightGreen,
                fontFamily = MGS,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text("PROBLEMS / DAY", color = CodecGreen.copy(alpha = 0.7f), fontFamily = MGS, fontSize = 12.sp)
        }
        MiniCodecButton("+", enabled = value < 99) { onValueChange((value + 1).coerceAtMost(99)) }
    }
}

@Composable
private fun MiniCodecButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 38.dp)
            .border(1.dp, if (enabled) CodecGreen else CodecGreen.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) CodecBrightGreen else CodecGreen.copy(alpha = 0.3f), fontSize = 25.sp)
    }
}

@Composable
private fun ReminderSelector(selectedMinutes: Long, onSelected: (Long) -> Unit) {
    val options = listOf(
        240L to "4H", 480L to "8H", 720L to "12H", 1440L to "24H"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        options.forEach { (minutes, label) ->
            SelectionCell(
                text = label,
                selected = minutes == selectedMinutes,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(minutes) }
            )
        }
    }
}

@Composable
private fun PeriodSelector(selected: LeaderboardPeriod, onSelected: (LeaderboardPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        LeaderboardPeriod.entries.forEach { period ->
            SelectionCell(
                text = period.label,
                selected = selected == period,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(period) }
            )
        }
    }
}

@Composable
private fun SelectionCell(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(if (selected) CodecGreen.copy(alpha = 0.24f) else Color.Black)
            .border(1.dp, if (selected) CodecBrightGreen else CodecGreen.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) CodecBrightGreen else CodecGreen.copy(alpha = 0.75f),
            fontFamily = MGS,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun FriendRow(username: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, CodecGreen.copy(alpha = 0.35f))
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = username,
            color = CodecBrightGreen,
            fontFamily = MGS,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "REMOVE",
            color = CodecRed,
            fontFamily = MGS,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onRemove).padding(12.dp)
        )
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry) {
    val rankColor = if (rank == 1) CodecRed else CodecGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (entry.isCurrentUser) CodecGreen.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, CodecGreen.copy(alpha = 0.25f))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString().padStart(2, '0'),
            color = rankColor,
            fontFamily = MGS,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(34.dp)
        )
        Text(
            text = entry.username + if (entry.isCurrentUser) "  [YOU]" else "",
            color = CodecBrightGreen,
            fontFamily = MGS,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (entry.isPending) "--" else entry.submissions.toString(),
            color = CodecBrightGreen,
            fontFamily = MGS,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
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

        // Hide the GIF reconnect behind an intentional codec transmission burst.
        CodecSignalStatic(modifier = Modifier.fillMaxSize())

        // Animated CRT overlay on top
        AnimatedDitherOverlay(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun CodecSignalStatic(modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        repeat(12) {
            frame = it
            delay(44L)
        }
        active = false
    }

    if (!active) return

    Canvas(modifier = modifier) {
        val cell = 4.dp.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val fade = (1f - frame / 14f).coerceIn(0.25f, 1f)

        drawRect(Color.Black.copy(alpha = 0.82f * fade))

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val noise = ((column * 37 + row * 73 + frame * 101) xor
                    (column * row * 11 + frame * 17)) and 15
                if (noise < 6) {
                    val brightness = when (noise) {
                        0 -> 0.76f
                        1, 2 -> 0.48f
                        else -> 0.26f
                    }
                    drawRect(
                        color = CodecBrightGreen.copy(alpha = brightness * fade),
                        topLeft = Offset(column * cell, row * cell),
                        size = Size(cell, cell)
                    )
                }
            }
        }

        val tearHeight = 7.dp.toPx()
        val tearY = ((frame * 19.dp.toPx()) % (size.height + tearHeight)) - tearHeight
        drawRect(
            color = CodecGreen.copy(alpha = 0.55f * fade),
            topLeft = Offset(0f, tearY),
            size = Size(size.width, tearHeight)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.72f * fade),
            topLeft = Offset(0f, tearY + tearHeight),
            size = Size(size.width, 2.dp.toPx())
        )
    }
}

@Composable
fun AnimatedDitherOverlay(modifier: Modifier = Modifier) {
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
    val framePulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(modifier = modifier) {
        val stepPx = stepDp.toPx()
        val actualOffset = yOffset * stepPx

        val glowGreen = Color(0xFF4AFF7A)

        val rows = (size.height / stepPx).toInt()
        for (y in 0 until rows step 3) {
            drawRect(
                color = glowGreen.copy(alpha = 0.05f),
                topLeft = Offset(0f, (y * stepPx + actualOffset) % size.height),
                size = Size(size.width, stepPx)
            )
        }

        // Dark analog falloff near the portrait edges, like a CRT image losing signal.
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.34f),
                0.10f to Color.Transparent,
                0.90f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.34f)
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.24f),
                0.08f to Color.Transparent,
                0.92f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.24f)
            )
        )

        val outerInset = 0.75.dp.toPx()
        val outerSize = Size(size.width - outerInset * 2f, size.height - outerInset * 2f)
        val frameCorner = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx())

        // Keep only a very dim continuous base; the visible bloom is built
        // from larger square cells below.
        drawRoundRect(
            color = Color(0xFF073E1B).copy(alpha = 0.20f * framePulse),
            topLeft = Offset(outerInset, outerInset),
            size = outerSize,
            cornerRadius = frameCorner,
            style = Stroke(width = 6.dp.toPx())
        )

        // Chunky, uneven squares form the phosphor bloom around a smooth frame.
        val glowPixel = 2.8.dp.toPx()
        fun glowAlpha(index: Int, layer: Int): Float {
            val variation = when (index % 5) {
                0 -> 1f
                1 -> 0.62f
                2 -> 0.82f
                3 -> 0.52f
                else -> 0.72f
            }
            val layerStrength = if (layer == 0) 0.13f else 0.21f
            return variation * layerStrength * framePulse
        }

        repeat(2) { layer ->
            val inset = (0.45.dp + (layer * 1.7f).dp).toPx()
            val left = inset
            val right = size.width - inset
            val top = inset
            val bottom = size.height - inset
            val cellSize = glowPixel * if (layer == 0) 1.15f else 0.92f

            var cellIndex = 0
            var x = left
            while (x < right) {
                val alpha = glowAlpha(cellIndex, layer)
                drawRect(
                    color = glowGreen.copy(alpha = alpha),
                    topLeft = Offset(x, top),
                    size = Size(cellSize, cellSize)
                )
                drawRect(
                    color = glowGreen.copy(alpha = alpha * 0.78f),
                    topLeft = Offset(x, bottom - cellSize),
                    size = Size(cellSize, cellSize)
                )
                x += glowPixel
                cellIndex++
            }

            cellIndex = 0
            var cellY = top
            while (cellY < bottom) {
                val alpha = glowAlpha(cellIndex, layer)
                drawRect(
                    color = glowGreen.copy(alpha = alpha),
                    topLeft = Offset(left, cellY),
                    size = Size(cellSize, cellSize)
                )
                drawRect(
                    color = glowGreen.copy(alpha = alpha * 0.82f),
                    topLeft = Offset(right - cellSize, cellY),
                    size = Size(cellSize, cellSize)
                )
                cellY += glowPixel
                cellIndex++
            }
        }

        // The border itself remains a clean connected phosphor trace.
        drawRoundRect(
            color = Color(0xFFB8FFC8).copy(alpha = 0.52f * framePulse),
            topLeft = Offset(2.8.dp.toPx(), 2.8.dp.toPx()),
            size = Size(size.width - 5.6.dp.toPx(), size.height - 5.6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.8.dp.toPx()),
            style = Stroke(width = 1.05.dp.toPx())
        )

        drawRoundRect(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = Offset(4.3.dp.toPx(), 4.3.dp.toPx()),
            size = Size(size.width - 8.6.dp.toPx(), size.height - 8.6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2.dp.toPx()),
            style = Stroke(width = 0.8.dp.toPx())
        )

        // Sparse edge noise keeps the frame imperfect and video-like.
        val noiseStep = 7.dp.toPx()
        val noiseThickness = 0.8.dp.toPx()
        var y = 5.dp.toPx() + actualOffset
        var index = 0
        while (y < size.height - 5.dp.toPx()) {
            val distanceFromCenter = kotlin.math.abs(y / size.height - 0.5f) * 2f
            val edgeVariation = 0.45f + 0.55f * (1f - distanceFromCenter)
            val alpha = (if (index % 3 == 0) 0.17f else 0.065f) * edgeVariation
            val width = if (index % 2 == 0) 3.dp.toPx() else 1.5.dp.toPx()
            drawRect(
                color = glowGreen.copy(alpha = alpha * framePulse),
                topLeft = Offset(outerInset, y),
                size = Size(width, noiseThickness)
            )
            drawRect(
                color = glowGreen.copy(alpha = alpha * framePulse),
                topLeft = Offset(size.width - outerInset - width, y),
                size = Size(width, noiseThickness)
            )
            y += noiseStep
            index++
        }
    }
}
@Composable
fun NeonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    labelText: String,
    modifier: Modifier = Modifier
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
        modifier = modifier
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
            .padding(bottom = 3.dp, start = 13.5.dp, end = 12.dp)

    ) {
        Text(
            text = text,
            fontFamily = fontFamily,
            fontSize = 24.sp,
            color = if (isPressed) brightGreen else glowGreen
        )
    }
}

@Composable
fun CustomNeonSnackbar(
    message: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val glowColor = if (isError) Color(0xFFFF4A4A) else Color(0xFF4AFF7A)

    Box(
        modifier = modifier
            .padding(12.dp)
            .border(
                width = 2.dp,
                color = glowColor,
                shape = RoundedCornerShape(6.dp)
            )
            .background(Color.Black, RoundedCornerShape(6.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = message,
            color = glowColor,
            fontFamily = fontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    MainScreen(
        initialSettings = UserSettings(
            username = "solid_snake",
            dailyGoal = 3,
            reminderMinutes = 240,
            friends = listOf("gray_fox", "otacon")
        )
    ) {}
}
