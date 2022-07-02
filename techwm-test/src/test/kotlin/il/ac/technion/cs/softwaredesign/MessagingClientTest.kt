package il.ac.technion.cs.softwaredesign

import com.google.inject.Guice
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import dev.misfitlabs.kotlinguice4.getInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletionException

class MessagingClientTest {
    private val injector = Guice.createInjector(MessagingClientTestModule())
    private val clientFactory = injector.getInstance<MessagingClientFactory>()

    private fun getUser(username: String, password: String) : MessagingClient{
        return clientFactory.get(username, password).join()
    }

    @Test
    fun `can't log in with wrong password`(){
        val user = getUser("Dvir", "CoolBeans43")
        val thrown = assertThrows<CompletionException> {
            user.login("NotMyPass").join()
        }
        assertThat(thrown.cause!!, isA<IllegalArgumentException>())
    }

    @Test
    fun `can log in with the correct password`(){
        val user = getUser("Dvir", "DvirB")
        assertDoesNotThrow { user.login("DvirB").join() }
    }

    @Test
    fun `can log out if logged in`(){
        val user = getUser("Bigboi", "whoop")
        user.login("whoop").join()
        assertDoesNotThrow { user.logout().join() }
    }

    @Test
    fun `can log in with wrong password if already logged in`(){
        val user = getUser("Dvir", "PASS")
        user.login("PASS").join()
        assertDoesNotThrow { user.login("NOTMYPASS").join() }
    }


    @Test
    fun `non logged in user can't log out`(){
        val user = getUser("spaghetti", "woop")
        val throwable = assertThrows<CompletionException> {
            user.logout().join()
        }
        assertThat(throwable.cause!!, isA<IllegalArgumentException>())
    }

    @Test
    fun `logged in user can get online users`(){
        val user0 = getUser("first", "mcfirst")
        val user1 = getUser("second", "mcsecond")

        user0.login("mcfirst").join()
        user1.login("mcsecond").join()

        val onlineUsers0 = user0.onlineUsers().join()
        val onlineUsers1 = user1.onlineUsers().join()

        assertEquals(onlineUsers0, onlineUsers1)
        assert(onlineUsers0.size == 2 && onlineUsers0.contains("first") && onlineUsers0.contains("second"))
    }

    @Test
    fun `user needs to be logged in to get online users`(){
        val user = getUser("ayy", "lmao")
        val throwable = assertThrows<CompletionException> {
            user.onlineUsers().join()
        }
        assertThat(throwable.cause!!, isA<PermissionException>())
    }

    @Test
    fun `offline users do not show up in onlineUsers()`(){
        val user0 = getUser("ayo", "whass")
        val user1 = getUser("bro", "gutten")

        user0.login("whass")
            .thenCompose { user1.login("gutten") }
            .thenCompose { user1.logout() }.join()

        val onlineUsers = user0.onlineUsers().join()
        assert(onlineUsers.equals(listOf("ayo")))
    }

    @Test
    fun `user can't send messages longer than 120 chars`(){
        var message = ""
        for (i in (1..121)){
            message += "DATA"
        }
        val user1 = getUser("DvirBiton", "HeSoYam")
        val user2 = getUser("DanielLieberman", "SmellMyFinger")
        user1.login("HeSoYam")
            .thenCompose { user2.login("SmellMyFinger") }.join()

        val throwable = assertThrows<CompletionException> {
            user1.sendMessage("DanielLieberman", message).join()
        }
        assertThat(throwable.cause!!, isA<IllegalArgumentException>())
    }

    @Test
    fun `user can't send message to a user that does not exist`(){
        val user = getUser("CoolBOI", "CoolBOI")
        user.login("CoolBOI").join()
        val throwable = assertThrows<CompletionException> {
            user.sendMessage("NesZiona", "You there?").join()
        }
        assertThat(throwable.cause!!, isA<IllegalArgumentException>())
    }

    @Test
    fun `user can't get inbox if offline`(){
        val message = "message"
        val user1 = getUser("DvirBiton", "HeSoYam")
        val user2 = getUser("DanielLieberman", "SmellMyFinger")
        user1.login("HeSoYam")
            .thenCompose { user2.login("SmellMyFinger") }.join()

        user1.sendMessage("DanielLieberman", message).join()

        user2.logout().join()

        val throwable = assertThrows<CompletionException> {
            user2.inbox().join()
        }
        assertThat(throwable.cause!!, isA<PermissionException>())
    }

    @Test
    fun `offline user can't send messages`(){
        val user = getUser("1", "2")
        val throwable = assertThrows<CompletionException> {
            user.sendMessage("who", "what's up broseph").join()
        }
        assertThat(throwable.cause!!, isA<PermissionException>())
    }

    @Test
    fun `single message is recoverable`(){
        val message = "message"
        val user1 = getUser("DvirBiton", "HeSoYam")
        val user2 = getUser("DanielLieberman", "SmellMyFinger")
        user1.login("HeSoYam")
            .thenCompose { user2.login("SmellMyFinger") }.join()

        user1.sendMessage("DanielLieberman", message).join()
        val inbox = user2.inbox().join()

        assertEquals(1, inbox["DvirBiton"]?.size)
        assert(inbox["DvirBiton"]!![0].message == message)
        assert(inbox["DvirBiton"]!![0].fromUser == "DvirBiton")
    }

    @Test
    fun `multiple messages are recoverable`(){
        val message1 = "message1"
        val message2 = "message2"
        val message3 = "message3"
        val message4 = "message4"
        val user1 = getUser("DvirBiton", "HeSoYam")
        val user2 = getUser("DanielLieberman", "SmellMyFinger")
        user1.login("HeSoYam")
            .thenCompose { user2.login("SmellMyFinger") }.join()

        user1.sendMessage("DanielLieberman", message1).join()
        user1.sendMessage("DanielLieberman", message2).join()
        user2.sendMessage("DvirBiton", message3).join()
        user2.sendMessage("DvirBiton", message4).join()

        val user1Inbox = user1.inbox().join()
        val user2Inbox = user2.inbox().join()

        assertEquals(2, user1Inbox["DanielLieberman"]?.size)
        assert(user1Inbox["DanielLieberman"]!![0].message == message3)
        assert(user1Inbox["DanielLieberman"]!![0].fromUser == "DanielLieberman")
        assert(user1Inbox["DanielLieberman"]!![1].message == message4)
        assert(user1Inbox["DanielLieberman"]!![1].fromUser == "DanielLieberman")

        assertEquals(2, user2Inbox["DvirBiton"]?.size)
        assert(user2Inbox["DvirBiton"]!![0].message == message1)
        assert(user2Inbox["DvirBiton"]!![0].fromUser == "DvirBiton")
        assert(user2Inbox["DvirBiton"]!![1].message == message2)
        assert(user2Inbox["DvirBiton"]!![1].fromUser == "DvirBiton")
    }

    @Test
    fun `user can send messages to himself`(){
        val user = getUser("thats", "me")
        user.login("me").join()
        user.sendMessage("thats", "high five!")
        val inbox = user.inbox().join()
        assertEquals(1, inbox["username"]?.size)
        assert(inbox["username"]!![0].message == "high five!")
    }

    @Test
    fun `offline user can't delete a message`(){
        val user0 = getUser("1", "2")
        val user1 = getUser("2", "3")
        val message1 = "wew"
        val message0 = "mom"

        user1.login("HeSoYam")
            .thenCompose { user0.login("SmellMyFinger") }.join()
        user0.sendMessage("2", message1).join()
        user0.sendMessage("2", message0).join()
        val oldInbox = user1.inbox().join()
        user1.logout()
        val throwable = assertThrows<CompletionException> {
            user1.deleteMessage(oldInbox["2"]!![0].id).join()
        }
        assertThat(throwable.cause!!, isA<PermissionException>())
    }

    @Test
    fun `user can't delete message that does not exist`(){
        val user1 = getUser("DvirBiton", "HeSoYam")
        val user2 = getUser("DanielLieberman", "SmellMyFinger")
        val message1 = "message1"
        val message2 = "message2"

        user1.login("HeSoYam")
            .thenCompose { user2.login("SmellMyFinger") }.join()

        user2.sendMessage("DvirBiton", message1).join()
        user2.sendMessage("DvirBiton", message2).join()

        val throwable = assertThrows<CompletionException> {
            user1.deleteMessage("non-existent").join()
        }
        assertThat(throwable.cause!!, isA<IllegalArgumentException>())
    }

    @Test
    fun `user can delete a message`(){
        val user1 = getUser("DvirBiton", "HeSoYam")
        val user2 = getUser("DanielLieberman", "SmellMyFinger")
        val message1 = "message1"
        val message2 = "message2"

        user1.login("HeSoYam")
            .thenCompose { user2.login("SmellMyFinger") }.join()

        user2.sendMessage("DvirBiton", message1).join()
        user2.sendMessage("DvirBiton", message2).join()

        val oldInbox = user1.inbox().join()
        user1.deleteMessage(oldInbox["DanielLieberman"]!![0].id).join()

        val inbox = user1.inbox().join()

        assertEquals(1, inbox["DanielLieberman"]?.size)
        assert(inbox["DanielLieberman"]!![0].message == message2)
    }

    @Test
    fun `user can delete a message which had another message sent afterwards`(){
        val user1 = getUser("DvirBiton", "HeSoYam")
        val user2 = getUser("DanielLieberman", "SmellMyFinger")
        val message1 = "message1"
        val message2 = "message2"
        val message3 = "message3"

        user1.login("HeSoYam")
            .thenCompose { user2.login("SmellMyFinger") }.join()

        user2.sendMessage("DvirBiton", message1).join()
        user2.sendMessage("DvirBiton", message2).join()
        user2.sendMessage("DvirBiton", message3).join()

        val oldInbox = user1.inbox().join()
        user1.deleteMessage(oldInbox["DanielLieberman"]!![1].id).join()

        val inbox = user1.inbox().join()

        assertEquals(2, inbox["DanielLieberman"]?.size)
        assert(inbox["DanielLieberman"]!![0].message == message1)
        assert(inbox["DanielLieberman"]!![1].message == message3)
    }

    @Test
    fun `restarting client factory is logging user out`(){
        val user1 = getUser("DvirBiton", "HeSoYam")
        val user2 = getUser("DanielLieberman", "SmellMyFinger")
        user1.login("HeSoYam")
            .thenCompose { user2.login("SmellMyFinger") }.join()

        val newClientFactory = injector.getInstance<MessagingClientFactory>()
        val newUser = newClientFactory.get("DvirBiton", "HeSoYam").join()
        newUser.login("HeSoYam")

        val onlineList = newUser.onlineUsers().join()
        assertEquals(1, onlineList.size)
        assertEquals("DvirBiton", onlineList[0])
    }
}