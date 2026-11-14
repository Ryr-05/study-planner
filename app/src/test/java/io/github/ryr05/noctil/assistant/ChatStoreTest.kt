package io.github.ryr05.noctil.ui.assistant

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChatStoreTest {
    private val chat = ChatStore.Chat(
        id = "abc-1",
        title = "What's due this week?",
        updatedAt = 1_000_000L,
        lines = listOf(
            ChatStore.Line(ChatStore.Role.User, "What's due this week?"),
            ChatStore.Line(ChatStore.Role.Bot, "Due in the next 7 days: Essay (Thu)."),
            ChatStore.Line(ChatStore.Role.Note, "Essay now starts Tonight 7:00 PM."),
        ),
    )

    @Test fun roundTrip() = assertEquals(chat, ChatStore.decode(ChatStore.encode(chat).toString()))

    @Test fun savesListsAndDropsOldChats() {
        val context = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        ChatStore.clear(context)
        ChatStore.save(context, chat.copy(id = "new", updatedAt = now))
        ChatStore.save(context, chat.copy(id = "old", updatedAt = now - 8L * 24 * 60 * 60 * 1000))
        // A chat with nothing from the student isn't worth keeping.
        ChatStore.save(context, chat.copy(id = "empty", lines = chat.lines.filter { it.role != ChatStore.Role.User }))
        assertEquals(listOf("new"), ChatStore.list(context, now).map { it.id })
        ChatStore.clear(context)
        assertEquals(emptyList<ChatStore.Chat>(), ChatStore.list(context, now))
    }

    @Test fun titlesAreShort() {
        assertEquals("Plan my week", ChatStore.titleFor("  Plan   my week "))
        assertEquals("move the history essay start to tonight...", ChatStore.titleFor("move the history essay start to tonight please, I'm busy"))
    }
}
