package com.kumalatova.lmsadmin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Оранжевый значок с числом. При нуле не показывается. */
@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .height(22.dp)
            .widthIn(min = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(LmsColors.Badge)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = LmsColors.BadgeText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = LmsColors.Muted,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 12.dp),
            )
        }
        Text(
            text = title,
            color = LmsColors.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                color = LmsColors.Badge,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { onAction() }
                    .padding(start = 12.dp),
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LmsColors.Line),
    )
}

/** Главная: сайдбар, строка = сайт. Долгое нажатие на строку предлагает удалить сайт. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    sites: List<Site>,
    states: Map<String, SiteState>,
    onOpen: (Site) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Site) -> Unit,
    notificationsOn: Boolean,
    onEnableNotifications: () -> Unit,
    update: UpdateState,
    onUpdate: () -> Unit,
    versionName: String,
    onSettings: () -> Unit,
) {
    var confirmId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Мои сайты", onBack = null, actionText = "Опрос", onAction = onSettings)
        if (!notificationsOn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LmsColors.RowActive)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Уведомления выключены, звука не будет",
                    color = LmsColors.Text,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButtonLike("Включить") { onEnableNotifications() }
            }
        }
        if (update !is UpdateState.None) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LmsColors.RowActive)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val text = when (update) {
                    is UpdateState.Available -> "Доступна новая версия ${update.info.name}"
                    is UpdateState.Downloading -> "Скачиваю обновление…"
                    is UpdateState.Failed -> "Не удалось скачать: ${update.message}"
                    UpdateState.None -> ""
                }
                Text(text = text, color = LmsColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (update !is UpdateState.Downloading) {
                    TextButtonLike(if (update is UpdateState.Failed) "Ещё раз" else "Обновить") { onUpdate() }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(sites, key = { it.id }) { site ->
                val state = states[site.id]
                if (confirmId == site.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LmsColors.Row)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Удалить «${site.name}»?",
                            color = LmsColors.Text,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButtonLike("Да") {
                            confirmId = null
                            onRemove(site)
                        }
                        TextButtonLike("Нет") { confirmId = null }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpen(site) },
                                onLongClick = { confirmId = site.id },
                            )
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = site.name,
                                color = LmsColors.Text,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state?.error != null) {
                                Text(
                                    text = state.error,
                                    color = LmsColors.Muted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        Badge(state?.counts?.total ?: 0, Modifier.padding(horizontal = 8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = LmsColors.Dim,
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LmsColors.Line),
                )
            }
            item {
                Text(
                    text = if (sites.isEmpty()) "Добавить первый сайт" else "Добавить сайт",
                    color = LmsColors.Muted,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAdd() }
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                )
                if (sites.isNotEmpty()) {
                    Text(
                        text = "Чтобы удалить сайт, нажмите на строку и подержите.",
                        color = LmsColors.Dim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
                Text(
                    text = "Версия приложения $versionName",
                    color = LmsColors.Dim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun TextButtonLike(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = LmsColors.Badge,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Содержимое сайта: три вкладки с числами и список обращений. */
@Composable
fun SiteScreen(
    site: Site,
    counts: Counts?,
    list: ListState,
    onBack: () -> Unit,
    onGroup: (Group) -> Unit,
    onOpenItem: (ListItem) -> Unit,
    onSound: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = site.name, onBack = onBack, actionText = "Звук", onAction = onSound)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Group.entries.forEach { group ->
                val active = group == list.group
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) LmsColors.RowActive else LmsColors.Row)
                        .clickable { onGroup(group) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = group.title,
                        color = if (active) LmsColors.Text else LmsColors.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    val n = counts?.of(group) ?: 0
                    if (n > 0) {
                        Spacer(Modifier.padding(start = 4.dp))
                        Badge(n)
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LmsColors.Line),
        )

        when {
            list.error != null && list.items.isEmpty() -> InfoText(list.error)
            list.loading && list.items.isEmpty() -> InfoText("Загрузка…")
            list.items.isEmpty() -> InfoText("Пока пусто")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list.items, key = { it.id }) { item ->
                    ItemRow(item, onClick = { onOpenItem(item) })
                }
            }
        }
    }
}

@Composable
fun InfoText(text: String) {
    Text(
        text = text,
        color = LmsColors.Muted,
        fontSize = 14.sp,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
fun ItemRow(item: ListItem, onClick: () -> Unit) {
    val unread = item.unread > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.person,
                color = if (unread) LmsColors.Text else LmsColors.Muted,
                fontSize = 15.sp,
                fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = formatTime(item.time), color = LmsColors.Dim, fontSize = 12.sp)
            Badge(item.unread, Modifier.padding(start = 8.dp))
        }
        Text(
            text = item.topic,
            color = LmsColors.Muted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (item.to.isNotEmpty()) {
            Text(
                text = "кому: ${item.to}",
                color = LmsColors.Dim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LmsColors.Line),
    )
}

/** Переписка и поле ответа. */
@Composable
fun ThreadScreen(
    thread: ThreadData?,
    error: String?,
    sending: Boolean,
    replyError: String?,
    onBack: () -> Unit,
    onSend: (String, () -> Unit) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(
            title = if (thread != null) "${thread.person} · ${thread.topic}" else "Переписка",
            onBack = onBack,
        )
        when {
            thread == null && error != null -> InfoText(error)
            thread == null -> InfoText("Загрузка…")
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(thread.messages.size) {
                    if (thread.messages.isNotEmpty()) listState.scrollToItem(thread.messages.size - 1)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
                ) {
                    items(thread.messages, key = { it.id }) { m ->
                        Bubble(m)
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LmsColors.Line),
                )
                Text(
                    text = "Ответ уйдёт от имени: " +
                        (if (thread.to.isNotEmpty()) thread.to else "администратора"),
                    color = LmsColors.Dim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
                )
                if (replyError != null) {
                    Text(
                        text = replyError,
                        color = LmsColors.Badge,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Ваш ответ") },
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LmsColors.Text,
                            unfocusedTextColor = LmsColors.Text,
                            focusedBorderColor = LmsColors.Badge,
                            unfocusedBorderColor = LmsColors.Dim,
                            cursorColor = LmsColors.Badge,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (text.isBlank() || sending) LmsColors.RowActive else LmsColors.Badge)
                            .clickable(enabled = text.isNotBlank() && !sending) {
                                onSend(text) { text = "" }
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (sending) "…" else "Отправить",
                            color = if (text.isBlank() || sending) LmsColors.Muted else LmsColors.BadgeText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Bubble(m: com.kumalatova.lmsadmin.Message) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (m.mine) LmsColors.RowActive else LmsColors.Row)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = m.sender + if (m.time.isNotEmpty()) " · " + formatTime(m.time) else "",
                color = LmsColors.Muted,
                fontSize = 11.sp,
            )
            if (m.text.isNotEmpty()) {
                Text(
                    text = m.text,
                    color = LmsColors.Text,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (m.attachment.isNotEmpty()) {
                Text(
                    text = "Файл: ${m.attachment}",
                    color = LmsColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}


/** Добавление сайта: адрес и ключ вручную или QR-код. */
@Composable
fun AddSiteScreen(
    state: AddState,
    onBack: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onScan: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = LmsColors.Text,
        unfocusedTextColor = LmsColors.Text,
        focusedBorderColor = LmsColors.Badge,
        unfocusedBorderColor = LmsColors.Dim,
        focusedLabelColor = LmsColors.Badge,
        unfocusedLabelColor = LmsColors.Muted,
        cursorColor = LmsColors.Badge,
    )

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Добавить сайт", onBack = onBack)
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = "Адрес сайта и ключ берутся на странице «Курсы → Мобильное приложение» на сайте.",
                color = LmsColors.Muted,
                fontSize = 13.sp,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Адрес сайта") },
                placeholder = { Text("https://example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Ключ") },
                placeholder = { Text("lmsk_…") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = LmsColors.Badge,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LmsColors.Badge)
                    .clickable(enabled = !state.loading) { onSubmit(url, key) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.loading) "Подключаюсь…" else "Подключить",
                    color = LmsColors.BadgeText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = "Сканировать QR-код",
                color = LmsColors.Muted,
                fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.loading) { onScan() }
                    .padding(vertical = 18.dp),
            )
        }
    }
}

/** Настройки опроса: рабочие дни и часы, как часто опрашивать. Время московское. */
@Composable
fun SettingsScreen(
    settings: PollSettings,
    onChange: (PollSettings) -> Unit,
    onBack: () -> Unit,
) {
    val dayNames = listOf(1 to "Пн", 2 to "Вт", 3 to "Ср", 4 to "Чт", 5 to "Пт", 6 to "Сб", 7 to "Вс")
    val workOptions = listOf(1, 2, 3, 5, 10, 15, 30)
    val offOptions = listOf(15, 30, 60, 120, 240, 480)

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Опрос сайтов", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsTitle("Рабочие дни")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dayNames.forEach { (num, name) ->
                    OptionChip(name, num in settings.days) {
                        val days = if (num in settings.days) settings.days - num else settings.days + num
                        onChange(settings.copy(days = days))
                    }
                }
            }

            SettingsTitle("Рабочее время")
            HourStepper(
                label = "С",
                hour = settings.startHour,
                onMinus = { if (settings.startHour > 0) onChange(settings.copy(startHour = settings.startHour - 1)) },
                onPlus = {
                    if (settings.startHour < settings.endHour - 1) {
                        onChange(settings.copy(startHour = settings.startHour + 1))
                    }
                },
            )
            HourStepper(
                label = "До",
                hour = settings.endHour,
                onMinus = {
                    if (settings.endHour > settings.startHour + 1) {
                        onChange(settings.copy(endHour = settings.endHour - 1))
                    }
                },
                onPlus = { if (settings.endHour < 24) onChange(settings.copy(endHour = settings.endHour + 1)) },
            )

            SettingsTitle("Как часто в рабочее время")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                workOptions.forEach { m ->
                    OptionChip(minutesLabel(m), settings.workMinutes == m) {
                        onChange(settings.copy(workMinutes = m))
                    }
                }
            }

            SettingsTitle("Как часто в остальное время")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                offOptions.forEach { m ->
                    OptionChip(minutesLabel(m), settings.offMinutes == m) {
                        onChange(settings.copy(offMinutes = m))
                    }
                }
            }

            Text(
                text = "Время московское. Изменения сохраняются сразу. Когда телефон долго лежит без движения с выключенным экраном, Android сам может растягивать интервалы.",
                color = LmsColors.Dim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
        }
    }
}

private fun minutesLabel(m: Int): String = if (m < 60) "$m мин" else "${m / 60} ч"

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text = text,
        color = LmsColors.Muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun OptionChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (active) LmsColors.Text else LmsColors.Muted,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) LmsColors.RowActive else LmsColors.Row)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun HourStepper(label: String, hour: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = LmsColors.Muted, fontSize = 14.sp, modifier = Modifier.widthIn(min = 36.dp))
        StepperSign("−", onMinus)
        Text(
            text = "$hour:00",
            color = LmsColors.Text,
            fontSize = 16.sp,
            modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 8.dp),
        )
        StepperSign("+", onPlus)
    }
}

@Composable
private fun StepperSign(sign: String, onClick: () -> Unit) {
    Text(
        text = sign,
        color = LmsColors.Text,
        fontSize = 18.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LmsColors.Row)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
