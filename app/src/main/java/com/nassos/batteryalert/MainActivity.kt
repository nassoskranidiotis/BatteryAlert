package com.nassos.batteryalert

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nassos.batteryalert.ui.AlertCard
import com.nassos.batteryalert.ui.BatteryAlertTheme
import com.nassos.batteryalert.ui.BatteryGauge
import com.nassos.batteryalert.ui.CardSpacing
import com.nassos.batteryalert.ui.IconBadge
import com.nassos.batteryalert.ui.Pill
import com.nassos.batteryalert.ui.SectionTitle
import com.nassos.batteryalert.ui.SheetOption

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BatteryAlertTheme { MainScreen() } }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app (e.g. by tapping an alert) silences a ringing alert.
        AlertSound.stop()
    }
}

@Composable
private fun rememberBatteryInfo(): State<BatteryInfo> {
    val context = LocalContext.current
    val state = remember {
        mutableStateOf(BatteryInfo.from(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))))
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = BatteryInfo.from(intent)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private fun hasNotificationPermission(context: Context) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun isUnrestricted(context: Context) =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private fun Context.safeStart(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        if (fallback != null) runCatching { startActivity(fallback) }
    }
}

private fun appDetailsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("BatteryLife")
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { AlertPrefs(context) }
    val battery by rememberBatteryInfo()

    var monitoring by remember { mutableStateOf(prefs.monitoring) }
    var alarmStream by remember { mutableStateOf(prefs.useAlarmStream) }
    val configs = remember {
        mutableStateMapOf<AlertKind, AlertConfig>().apply { AlertKind.entries.forEach { put(it, prefs.config(it)) } }
    }
    var notificationsOk by remember { mutableStateOf(hasNotificationPermission(context)) }
    var unrestricted by remember { mutableStateOf(isUnrestricted(context)) }

    LifecycleResumeEffect(Unit) {
        notificationsOk = hasNotificationPermission(context)
        unrestricted = isUnrestricted(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        // Make sure the service is running if it was killed by the system.
        if (monitoring) BatteryMonitorService.start(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOk = it
    }

    fun commit(kind: AlertKind) {
        prefs.save(kind, configs.getValue(kind))
        if (monitoring) BatteryMonitorService.start(context)
    }

    fun setSound(kind: AlertKind, sound: String?) {
        configs[kind] = configs.getValue(kind).copy(sound = sound)
        commit(kind)
    }

    fun setMonitoring(on: Boolean) {
        monitoring = on
        prefs.monitoring = on
        if (on) {
            if (!notificationsOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            BatteryMonitorService.start(context)
        } else {
            BatteryMonitorService.stop(context)
        }
    }

    // Sound picking: which alert the open sheet / pending picker result belongs to.
    var sheetFor by rememberSaveable { mutableStateOf<AlertKind?>(null) }
    var pickingFor by rememberSaveable { mutableStateOf<AlertKind?>(null) }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val kind = pickingFor ?: return@rememberLauncherForActivityResult
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        setSound(
            kind,
            when (uri) {
                null -> ""
                Settings.System.DEFAULT_NOTIFICATION_URI -> null
                else -> uri.toString()
            },
        )
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val kind = pickingFor ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setSound(kind, uri.toString())
    }

    fun openRingtonePicker(kind: AlertKind, type: Int, title: String) {
        pickingFor = kind
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, configs.getValue(kind).soundUri)
        runCatching { ringtoneLauncher.launch(intent) }
    }

    val scheme = MaterialTheme.colorScheme

    // Surface provides the content colour; without it text defaults to black.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.surface,
        contentColor = scheme.onSurface,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = CardSpacing,
            modifier = Modifier.navigationBarsPadding(),
        ) {
            // Hero: title + live gauge on a soft gradient.
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(
                            Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.surface)),
                            MaterialTheme.shapes.extraLarge,
                        )
                        .statusBarsPadding()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(R.drawable.ic_bolt, scheme.primary, scheme.onPrimary)
                        Text(
                            "Battery Alert",
                            style = MaterialTheme.typography.headlineMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ειδοποιήσεις επιπέδου μπαταρίας",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    BatteryGauge(
                        level = battery.level,
                        plugged = battery.plugged,
                        markers = AlertKind.entries.mapNotNull { k -> configs.getValue(k).takeIf { it.enabled }?.target },
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        Pill(battery.plugLabel)
                        Pill("%.1f°C".format(battery.temperatureC))
                        if (battery.full) Pill("Πλήρης")
                    }
                }
            }

            item {
                MonitoringCard(monitoring, ::setMonitoring)
            }

            if (monitoring && !notificationsOk) {
                item {
                    WarningCard(
                        "Οι ειδοποιήσεις είναι απενεργοποιημένες",
                        "Χωρίς αυτές η εφαρμογή δεν μπορεί να σε ειδοποιήσει.",
                        "Ενεργοποίηση",
                    ) {
                        context.safeStart(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            appDetailsIntent(context),
                        )
                    }
                }
            }

            item { SectionTitle("Ειδοποιήσεις") }
            AlertKind.entries.forEach { kind ->
                item(key = kind.key) {
                    val config = configs.getValue(kind)
                    val title = remember(config.sound) { AlertSound.title(context, config.sound) }
                    AlertCard(
                        kind = kind,
                        config = config,
                        soundTitle = title,
                        onChange = { configs[kind] = it },
                        onCommit = { commit(kind) },
                        onPickSound = { sheetFor = kind },
                        onTestSound = { AlertSound.play(context, config.soundUri, alarmStream, respectDnd = false) },
                    )
                }
            }

            item { SectionTitle("Ήχος") }
            item {
                SettingCard(
                    "Ένταση ξυπνητηριού",
                    "Οι ειδοποιήσεις ακούγονται με την ένταση του ξυπνητηριού, ακόμα και στο αθόρυβο.",
                    alarmStream,
                ) {
                    alarmStream = it
                    prefs.useAlarmStream = it
                }
            }

            item { SectionTitle("Αξιόπιστη λειτουργία (HyperOS)") }
            item {
                XiaomiCard(
                    unrestricted = unrestricted,
                    onAutostart = {
                        val autostart = Intent().setComponent(
                            ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity",
                            )
                        )
                        context.safeStart(autostart, appDetailsIntent(context))
                    },
                    onBattery = {
                        context.safeStart(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                            appDetailsIntent(context),
                        )
                    },
                )
            }
        }
    }

    sheetFor?.let { kind ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, sheetState = rememberModalBottomSheetState()) {
            Text(
                if (kind == AlertKind.CHARGE) "Ήχος φόρτισης" else "Ήχος χαμηλής μπαταρίας",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            fun pick(action: () -> Unit) {
                sheetFor = null
                action()
            }
            SheetOption(R.drawable.ic_bell, "Ήχοι ειδοποιήσεων") {
                pick { openRingtonePicker(kind, RingtoneManager.TYPE_NOTIFICATION, "Ήχοι ειδοποιήσεων") }
            }
            SheetOption(R.drawable.ic_alarm, "Ήχοι ξυπνητηριού") {
                pick { openRingtonePicker(kind, RingtoneManager.TYPE_ALARM, "Ήχοι ξυπνητηριού") }
            }
            SheetOption(R.drawable.ic_music, "Ήχοι κλήσης") {
                pick { openRingtonePicker(kind, RingtoneManager.TYPE_RINGTONE, "Ήχοι κλήσης") }
            }
            SheetOption(R.drawable.ic_folder, "Από αρχείο…", "Οποιοδήποτε αρχείο ήχου (MP3, OGG, …)") {
                pick {
                    pickingFor = kind
                    fileLauncher.launch(arrayOf("audio/*"))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            SheetOption(null, "Προεπιλογή συστήματος") { pick { setSound(kind, null) } }
            SheetOption(R.drawable.ic_bell_off, "Χωρίς ήχο") { pick { setSound(kind, "") } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonitoringCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = { onToggle(!on) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (on) scheme.primary else scheme.surfaceContainerHigh,
            contentColor = if (on) scheme.onPrimary else scheme.onSurface,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (on) "Η παρακολούθηση είναι ενεργή" else "Η παρακολούθηση είναι ανενεργή",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (on) "Θα ειδοποιηθείς όταν η μπαταρία φτάσει στα όρια" else "Πάτησε για να ξεκινήσει",
                    style = MaterialTheme.typography.bodyMedium,
                    color = (if (on) scheme.onPrimary else scheme.onSurfaceVariant).copy(alpha = 0.85f),
                )
            }
            Switch(
                on, onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = scheme.primary,
                    checkedTrackColor = scheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun SettingCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.ic_alarm, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onChange)
        }
    }
}

@Composable
private fun WarningCard(title: String, text: String, action: String, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun XiaomiCard(unrestricted: Boolean, onAutostart: () -> Unit, onBattery: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(R.drawable.ic_shield, scheme.secondaryContainer, scheme.onSecondaryContainer)
                Text(
                    "Το HyperOS κλείνει τις εφαρμογές στο παρασκήνιο. Κάνε αυτές τις δύο ρυθμίσεις μία φορά.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            ActionRow("Αυτόματη εκκίνηση", "Ενεργοποίησε το Battery Alert", done = false, onClick = onAutostart)
            ActionRow(
                "Χωρίς περιορισμούς μπαταρίας",
                if (unrestricted) "Ενεργό" else "Επίλεξε «Να επιτρέπεται»",
                done = unrestricted,
                onClick = onBattery,
            )
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (done) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
