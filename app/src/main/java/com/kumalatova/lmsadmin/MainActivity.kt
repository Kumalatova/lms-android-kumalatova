package com.kumalatova.lmsadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        // Пока приложение открыто, опрашиваем сайты раз в 30 секунд.
        // Опрос в фоне (когда приложение закрыто) появится в блоке 4.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    vm.poll()
                    delay(30_000)
                }
            }
        }

        setContent {
            LmsTheme {
                AppRoot(vm)
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val sites by vm.sites.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val thread by vm.thread.collectAsStateWithLifecycle()
    val threadError by vm.threadError.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()

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
                onAdd = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Наведите камеру на QR-код на странице плагина")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                },
                onRemove = { vm.removeSite(it.id) },
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
                    )
                }
            }

            is Screen.ThreadView -> ThreadScreen(
                thread = thread,
                error = threadError,
                onBack = { vm.back() },
            )
        }

        if (notice != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(LmsColors.RowActive, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
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
