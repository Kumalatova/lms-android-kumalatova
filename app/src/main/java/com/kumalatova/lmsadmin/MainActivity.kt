package com.kumalatova.lmsadmin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notificationsOn = MutableStateFlow(true)
    private var askedByUser = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsOn.value = Notifier.canNotify(this)
            if (!granted && !Notifier.canNotify(this)) {
                // Система больше не показывает окно запроса: открываем настройки уведомлений.
                if (askedByUser) Notifier.openAppNotificationSettings(this)
            }
            askedByUser = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        // Пока приложение открыто, список на экране обновляется раз в 30 секунд.
        // Уведомления в фоне приходят от Poller по расписанию (Schedule).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    vm.poll()
                    delay(30_000)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)

        setContent {
            LmsTheme {
                AppRoot(
                    vm = vm,
                    notificationsOn = notificationsOn,
                    onEnableNotifications = { enableNotifications() },
                    onSound = { siteId -> Notifier.openChannelSettings(this, siteId) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Poller.schedule(this)
        KeepAliveService.start(this)
        vm.checkUpdate()
    }

    override fun onResume() {
        super.onResume()
        notificationsOn.value = Notifier.canNotify(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val siteId = intent?.getStringExtra(Notifier.EXTRA_SITE) ?: return
        val group = intent.getStringExtra(Notifier.EXTRA_GROUP) ?: return
        val id = intent.getLongExtra(Notifier.EXTRA_ID, 0L)
        intent.removeExtra(Notifier.EXTRA_SITE)
        if (id > 0L) vm.openFromNotification(siteId, group, id)
    }

    private fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            askedByUser = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Notifier.openAppNotificationSettings(this)
        }
    }
}

@Composable
fun AppRoot(
    vm: AppViewModel,
    notificationsOn: MutableStateFlow<Boolean>,
    onEnableNotifications: () -> Unit,
    onSound: (String) -> Unit,
) {
    val sites by vm.sites.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val thread by vm.thread.collectAsStateWithLifecycle()
    val threadError by vm.threadError.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val addState by vm.add.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val replyError by vm.replyError.collectAsStateWithLifecycle()
    val notifOn by notificationsOn.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val versionName = Updater.currentName(androidx.compose.ui.platform.LocalContext.current)

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text != null) vm.addSiteFromQr(text)
    }

    BackHandler(enabled = screen != Screen.Home) { vm.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(LmsColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                sites = sites,
                states = states,
                onOpen = { vm.openSite(it.id) },
                onAdd = { vm.openAdd() },
                onRemove = { vm.removeSite(it.id) },
                notificationsOn = notifOn,
                onEnableNotifications = onEnableNotifications,
                update = update,
                onUpdate = { vm.startUpdate() },
                versionName = versionName,
            )

            Screen.AddSite -> AddSiteScreen(
                state = addState,
                onBack = { vm.back() },
                onSubmit = { url, key -> vm.addSiteManual(url, key) },
                onScan = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Наведите камеру на QR-код на странице плагина")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                },
            )

            is Screen.SiteView -> {
                val site = sites.firstOrNull { it.id == s.siteId }
                if (site != null) {
                    SiteScreen(
                        site = site,
                        counts = states[site.id]?.counts,
                        list = list,
                        onBack = { vm.back() },
                        onGroup = { vm.selectGroup(site.id, it) },
                        onOpenItem = { vm.openThread(site.id, it.group, it.id) },
                        onSound = { onSound(site.id) },
                    )
                }
            }

            is Screen.ThreadView -> ThreadScreen(
                thread = thread,
                error = threadError,
                sending = sending,
                replyError = replyError,
                onBack = { vm.back() },
                onSend = { text, done -> vm.sendReply(text, done) },
            )
        }

        if (notice != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(LmsColors.RowActive, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = notice.orEmpty(),
                    color = LmsColors.Text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButtonLike("Закрыть") { vm.dismissNotice() }
            }
        }
    }
}
