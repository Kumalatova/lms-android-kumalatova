package com.kumalatova.lmsadmin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx: Application = app
    private val store = SiteStore(app)
    private val seenStore = SeenStore(app)

    private val _sites = MutableStateFlow(store.load())
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()

    private val _states = MutableStateFlow<Map<String, SiteState>>(emptyMap())
    val states: StateFlow<Map<String, SiteState>> = _states.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _list = MutableStateFlow(ListState())
    val list: StateFlow<ListState> = _list.asStateFlow()

    private val _thread = MutableStateFlow<ThreadData?>(null)
    val thread: StateFlow<ThreadData?> = _thread.asStateFlow()

    private val _threadError = MutableStateFlow<String?>(null)
    val threadError: StateFlow<String?> = _threadError.asStateFlow()

    private val _add = MutableStateFlow(AddState())
    val add: StateFlow<AddState> = _add.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _replyError = MutableStateFlow<String?>(null)
    val replyError: StateFlow<String?> = _replyError.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.None)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    private var noticeJob: Job? = null
    private var lastReadMark = ""

    init {
        Notifier.removeOldServiceChannels(app)
        _sites.value.forEach { Notifier.ensureSiteChannel(app, it) }
    }

    private fun siteById(id: String): Site? = _sites.value.firstOrNull { it.id == id }

    fun showNotice(text: String) {
        _notice.value = text
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(10_000)
            _notice.value = null
        }
    }

    /** Проверяет, не вышла ли новая версия приложения. */
    fun checkUpdate() {
        if (_update.value is UpdateState.Downloading) return
        viewModelScope.launch {
            val info = Updater.check(appCtx)
            if (info != null) _update.value = UpdateState.Available(info)
            else if (_update.value is UpdateState.Available) _update.value = UpdateState.None
        }
    }

    /** Скачивает новую версию и открывает окно установки. */
    fun startUpdate() {
        val info = when (val s = _update.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info
            else -> return
        }
        if (!Updater.canInstall(appCtx)) {
            Updater.openInstallPermission(appCtx)
            showNotice("Разрешите установку из этого приложения и нажмите «Обновить» ещё раз")
            return
        }
        _update.value = UpdateState.Downloading(info)
        viewModelScope.launch {
            try {
                val file = Updater.download(appCtx)
                Updater.install(appCtx, file)
                _update.value = UpdateState.Available(info)
            } catch (e: ApiException) {
                _update.value = UpdateState.Failed(info, e.userMessage)
            } catch (e: Exception) {
                _update.value = UpdateState.Failed(info, "нет связи")
            }
        }
    }

    fun dismissNotice() {
        noticeJob?.cancel()
        _notice.value = null
    }

    /** Опрос: вызывается раз в 30 секунд, пока приложение открыто. */
    suspend fun poll() {
        refreshAll()
        when (val s = _screen.value) {
            is Screen.SiteView -> loadList(s.siteId, _list.value.group, silent = true)
            is Screen.ThreadView -> loadThread(s.siteId, s.group, s.id, silent = true)
            Screen.Home, Screen.AddSite -> Unit
        }
    }

    suspend fun refreshAll() {
        val current = _sites.value
        if (current.isEmpty()) return
        val results = coroutineScope {
            current.map { site ->
                async {
                    try {
                        val r = Api.summary(site)
                        site.id to SiteState(counts = r.counts)
                    } catch (e: ApiException) {
                        site.id to SiteState(error = e.userMessage)
                    } catch (e: Exception) {
                        site.id to SiteState(error = "Сайт ответил с ошибкой")
                    }
                }
            }.awaitAll()
        }
        val old = _states.value
        _states.value = results.toMap().mapValues { (id, state) ->
            // При ошибке оставляем прошлые числа, чтобы значок не исчезал из-за сбоя связи.
            if (state.error != null) SiteState(counts = old[id]?.counts, error = state.error) else state
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refreshAll() }
    }

    fun openAdd() {
        _add.value = AddState()
        _screen.value = Screen.AddSite
    }

    /** Приводит адрес к виду https://сайт/ . Возвращает null, если адрес не подходит. */
    private fun normalizeUrl(raw: String): String? {
        var u = raw.trim()
        if (u.isEmpty()) return null
        if (u.startsWith("http://")) return null
        if (!u.startsWith("https://")) u = "https://$u"
        if (!u.endsWith("/")) u += "/"
        return u
    }

    /**
     * Подключает сайт: пробует адреса обращений по очереди (свой из QR,
     * затем обычный wp-json, затем запасной ?rest_route=).
     * Возвращает текст ошибки или null, если сайт добавлен.
     */
    private suspend fun connect(siteUrl: String, apiFromQr: String?, key: String): String? {
        if (_sites.value.any { it.key == key }) return "Этот сайт уже добавлен"

        val candidates = listOfNotNull(
            apiFromQr?.takeIf { it.isNotEmpty() },
            siteUrl + "wp-json/my-lms-mobile/v1/",
            siteUrl + "?rest_route=/my-lms-mobile/v1/",
        ).distinct()

        var lastError = "Не удалось подключиться"
        for (api in candidates) {
            val probe = Site(UUID.randomUUID().toString(), "", siteUrl, api, key)
            try {
                val r = Api.summary(probe)
                val name = r.siteName.ifEmpty { siteUrl.removePrefix("https://").trimEnd('/') }
                val site = probe.copy(name = name)
                _sites.value = _sites.value + site
                store.save(_sites.value)
                _states.value = _states.value + (site.id to SiteState(counts = r.counts))
                Notifier.ensureSiteChannel(appCtx, site)
                Poller.schedule(appCtx)
                KeepAliveService.start(appCtx)
                return null
            } catch (e: ApiException) {
                lastError = e.userMessage
                // Ключ не подошёл: другие адреса не помогут.
                if (e.userMessage.startsWith("Ключ не подходит")) break
            } catch (e: Exception) {
                lastError = "Не удалось подключиться"
            }
        }
        return lastError
    }

    /** Добавление по адресу и ключу, введённым вручную. */
    fun addSiteManual(rawUrl: String, rawKey: String) {
        val url = normalizeUrl(rawUrl)
        val key = rawKey.trim()
        if (url == null) {
            _add.value = AddState(error = "Введите адрес сайта, начиная с https://")
            return
        }
        if (key.isEmpty()) {
            _add.value = AddState(error = "Введите ключ")
            return
        }
        _add.value = AddState(loading = true)
        viewModelScope.launch {
            val error = connect(url, null, key)
            if (error == null) {
                _add.value = AddState()
                _screen.value = Screen.Home
                showNotice("Сайт добавлен")
            } else {
                _add.value = AddState(error = error)
            }
        }
    }

    /** Добавление из QR-кода плагина: {"v":1,"site":"...","api":"...","key":"..."}. */
    fun addSiteFromQr(text: String) {
        val data = try {
            JSONObject(text)
        } catch (e: Exception) {
            _add.value = AddState(error = "Это не QR-код плагина")
            return
        }
        val url = normalizeUrl(data.optString("site"))
        val key = data.optString("key").trim()
        if (url == null || key.isEmpty()) {
            _add.value = AddState(error = "Это не QR-код плагина или сайт без https")
            return
        }
        _screen.value = Screen.AddSite
        _add.value = AddState(loading = true)
        viewModelScope.launch {
            val error = connect(url, data.optString("api"), key)
            if (error == null) {
                _add.value = AddState()
                _screen.value = Screen.Home
                showNotice("Сайт добавлен")
            } else {
                _add.value = AddState(error = error)
            }
        }
    }

    fun removeSite(id: String) {
        _sites.value = _sites.value.filter { it.id != id }
        store.save(_sites.value)
        _states.value = _states.value - id
        Notifier.deleteSiteChannel(appCtx, id)
        if (_sites.value.isEmpty()) {
            Poller.schedule(appCtx)
            KeepAliveService.stop(appCtx)
        }
        seenStore.remove(id)
    }

    fun openSite(siteId: String) {
        val counts = _states.value[siteId]?.counts
        // Открываем первую группу, где есть непрочитанное; иначе тикеты.
        val group = Group.entries.firstOrNull { counts != null && counts.of(it) > 0 } ?: Group.Tickets
        _screen.value = Screen.SiteView(siteId)
        _list.value = ListState(group = group, loading = true)
        viewModelScope.launch { loadList(siteId, group, silent = false) }
    }

    fun selectGroup(siteId: String, group: Group) {
        _list.value = ListState(group = group, loading = true)
        viewModelScope.launch { loadList(siteId, group, silent = false) }
    }

    private suspend fun loadList(siteId: String, group: Group, silent: Boolean) {
        val site = siteById(siteId) ?: return
        if (!silent) _list.value = _list.value.copy(loading = true, error = null)
        try {
            val items = Api.list(site, group)
            // Пока шёл запрос, пользователь мог переключить группу.
            if (_list.value.group == group) {
                _list.value = ListState(group = group, items = items)
            }
            // Список продаж открыт: новые считаются просмотренными, значок гаснет.
            if (group == Group.Sales && items.any { it.unread > 0 }) {
                try {
                    Api.markRead(site, Group.Sales, 0)
                    refreshAll()
                } catch (e: ApiException) {
                    // Не критично: отметим при следующем открытии.
                }
            }
        } catch (e: ApiException) {
            if (_list.value.group == group) _list.value = _list.value.copy(loading = false, error = e.userMessage)
        } catch (e: Exception) {
            if (_list.value.group == group) _list.value = _list.value.copy(loading = false, error = "Сайт ответил с ошибкой")
        }
    }

    fun openThread(siteId: String, group: Group, id: Long) {
        // Продажа — только уведомление: переписки у неё нет.
        if (group == Group.Sales) return
        _thread.value = null
        _threadError.value = null
        _replyError.value = null
        lastReadMark = ""
        Notifier.cancelItem(appCtx, siteId, group, id)
        _screen.value = Screen.ThreadView(siteId, group, id)
        viewModelScope.launch { loadThread(siteId, group, id, silent = false) }
    }

    private suspend fun loadThread(siteId: String, group: Group, id: Long, silent: Boolean) {
        val site = siteById(siteId) ?: return
        try {
            val data = Api.thread(site, group, id)
            _thread.value = data
            _threadError.value = null

            // Открыли переписку: отмечаем прочитанной и обновляем значки.
            val mark = "$id:${data.messages.lastOrNull()?.id ?: 0}"
            if (mark != lastReadMark) {
                lastReadMark = mark
                try {
                    Api.markRead(site, group, id)
                    refreshAll()
                } catch (e: ApiException) {
                    // Не критично: значок обновится при следующем опросе.
                }
            }
        } catch (e: ApiException) {
            if (!silent || _thread.value == null) _threadError.value = e.userMessage
        } catch (e: Exception) {
            if (!silent || _thread.value == null) _threadError.value = "Сайт ответил с ошибкой"
        }
    }

    /** Открыть переписку из уведомления. */
    fun openFromNotification(siteId: String, groupCode: String, id: Long) {
        if (siteById(siteId) == null) return
        val group = Group.from(groupCode)
        if (group == Group.Sales) {
            // У продажи нет переписки: открываем вкладку «Продажи» сайта.
            _screen.value = Screen.SiteView(siteId)
            _list.value = ListState(group = group, loading = true)
            viewModelScope.launch { loadList(siteId, group, silent = false) }
            return
        }
        // Список группы под перепиской, чтобы "назад" вёл в нужное место.
        _list.value = ListState(group = group, loading = true)
        viewModelScope.launch { loadList(siteId, group, silent = false) }
        openThread(siteId, group, id)
    }

    /** Отправить ответ в открытую переписку. onDone вызывается при успехе. */
    fun sendReply(text: String, onDone: () -> Unit) {
        val s = _screen.value as? Screen.ThreadView ?: return
        val site = siteById(s.siteId) ?: return
        val clean = text.trim()
        if (clean.isEmpty() || _sending.value) return
        _sending.value = true
        _replyError.value = null
        viewModelScope.launch {
            try {
                val data = Api.reply(site, s.group, s.id, clean)
                _thread.value = data
                lastReadMark = "${s.id}:${data.messages.lastOrNull()?.id ?: 0}"
                Notifier.cancelItem(appCtx, s.siteId, s.group, s.id)
                onDone()
                refreshAll()
            } catch (e: ApiException) {
                _replyError.value = e.userMessage
            } catch (e: Exception) {
                _replyError.value = "Не удалось отправить ответ"
            } finally {
                _sending.value = false
            }
        }
    }

    /** Кнопка "назад". Возвращает true, если экран сменился. */
    fun back(): Boolean {
        return when (val s = _screen.value) {
            is Screen.ThreadView -> {
                _screen.value = Screen.SiteView(s.siteId)
                viewModelScope.launch {
                    refreshAll()
                    loadList(s.siteId, _list.value.group, silent = true)
                }
                true
            }
            is Screen.SiteView -> {
                _screen.value = Screen.Home
                refreshNow()
                true
            }
            Screen.AddSite -> {
                _screen.value = Screen.Home
                true
            }
            Screen.Home -> false
        }
    }
}
