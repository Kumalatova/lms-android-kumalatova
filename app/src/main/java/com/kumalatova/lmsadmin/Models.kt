package com.kumalatova.lmsadmin

/** Подключённый сайт. api — адрес обращений плагина, оканчивается на "/". */
data class Site(
    val id: String,
    val name: String,
    val url: String,
    val api: String,
    val key: String,
)

/** Три группы обращений. */
enum class Group(val code: String, val title: String) {
    Tickets("tickets", "Тикеты"),
    Teachers("teachers", "Преподавателям"),
    Shop("shop", "Магазин");

    companion object {
        fun from(code: String): Group = entries.firstOrNull { it.code == code } ?: Tickets
    }
}

data class Counts(
    val tickets: Int,
    val teachers: Int,
    val shop: Int,
) {
    val total: Int get() = tickets + teachers + shop

    fun of(group: Group): Int = when (group) {
        Group.Tickets -> tickets
        Group.Teachers -> teachers
        Group.Shop -> shop
    }
}

/** Что известно о сайте после последнего опроса. */
data class SiteState(
    val counts: Counts? = null,
    val error: String? = null,
)

data class ListItem(
    val id: Long,
    val group: Group,
    val person: String,
    val topic: String,
    val to: String,
    val unread: Int,
    val time: String,
)

data class ListState(
    val group: Group = Group.Tickets,
    val items: List<ListItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class Message(
    val id: Long,
    val mine: Boolean,
    val sender: String,
    val text: String,
    val attachment: String,
    val time: String,
)

data class ThreadData(
    val id: Long,
    val group: Group,
    val topic: String,
    val person: String,
    val to: String,
    val messages: List<Message>,
)

sealed interface Screen {
    data object Home : Screen
    data object AddSite : Screen
    data class SiteView(val siteId: String) : Screen
    data class ThreadView(val siteId: String, val group: Group, val id: Long) : Screen
}

data class AddState(
    val loading: Boolean = false,
    val error: String? = null,
)
