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
    private val store = SiteStore(app)

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

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var noticeJob: Job? = null
    private var lastReadMark = ""

    private fun siteById(id: String): Site? = _sites.value.firstOrNull { it.id == id }

    fun showNotice(text: String) {
        _notice.value = text
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(6000)
            _notice.value = null
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
            Screen.Home -> Unit
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

    /** Добавление сайта из QR-кода плагина: {"v":1,"site":"...","api":"...","key":"..."}. */
    fun addSiteFromQr(text: String) {
        viewModelScope.launch {
            val data = try {
                JSONObject(text)
            } catch (e: Exception) {
                showNotice("Это не QR-код плагина")
                return@launch
            }
            val siteUrl = data.optString("site")
            val key = data.optString("key")
            var api = data.optString("api")
            if (siteUrl.isEmpty() || key.isEmpty()) {
                showNotice("Это не QR-код плагина")
                return@launch
            }
            if (!siteUrl.startsWith("https://")) {
                showNotice("Сайт должен работать по https")
                return@launch
            }
            if (api.isEmpty()) {
                api = siteUrl.trimEnd('/') + "/wp-json/my-lms-mobile/v1/"
            }
            if (_sites.value.any { it.key == key }) {
                showNotice("Этот сайт уже добавлен")
                return@launch
            }

            val probe = Site(UUID.randomUUID().toString(), "", siteUrl, api, key)
            try {
                val r = Api.summary(probe)
                val name = r.siteName.ifEmpty { siteUrl.removePrefix("https://").trimEnd('/') }
                val site = probe.copy(name = name)
                _sites.value = _sites.value + site
                store.save(_sites.value)
                _states.value = _states.value + (site.id to SiteState(counts = r.counts))
                showNotice("Сайт «$name» добавлен")
            } catch (e: ApiException) {
                showNotice(e.userMessage)
            } catch (e: Exception) {
                showNotice("Не удалось добавить сайт")
            }
        }
    }

    fun removeSite(id: String) {
        _sites.value = _sites.value.filter { it.id != id }
        store.save(_sites.value)
        _states.value = _states.value - id
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
        } catch (e: ApiException) {
            if (_list.value.group == group) _list.value = _list.value.copy(loading = false, error = e.userMessage)
        } catch (e: Exception) {
            if (_list.value.group == group) _list.value = _list.value.copy(loading = false, error = "Сайт ответил с ошибкой")
        }
    }

    fun openThread(siteId: String, group: Group, id: Long) {
        _thread.value = null
        _threadError.value = null
        lastReadMark = ""
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
            Screen.Home -> false
        }
    }
}
