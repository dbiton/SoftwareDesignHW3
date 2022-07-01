package il.ac.technion.cs.softwaredesign

import com.google.inject.Guice
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import dev.misfitlabs.kotlinguice4.getInstance

import org.junit.jupiter.api.Test

class MessagingClientStaffTest {
    private val injector = Guice.createInjector(MessagingClientModule())
    private val clientFactory = injector.getInstance<MessagingClientFactory>()

    @Test
    fun `A user can send a message to another user`() {
        val msg = "Hello, Patrick!"

        val spongebob = clientFactory.get("spongebob", "pass").join()
        val patrick = clientFactory.get("patrick", "pass").join()
        spongebob.login("pass")
            .thenCompose { patrick.login("pass") }
            .thenCompose { spongebob.sendMessage("patrick", msg) }
            .thenCompose { patrick.inbox() }
            .thenAccept { inbox ->
                assertThat(inbox.size, equalTo(1))
                assertThat(inbox["spongebob"]!![0].message, equalTo(msg))
            }
            .join()
    }
}