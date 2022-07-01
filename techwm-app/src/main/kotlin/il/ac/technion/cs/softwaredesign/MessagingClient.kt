package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import java.lang.IllegalArgumentException
import java.util.concurrent.CompletableFuture

/**
 * A message sent by [fromUser].
 * [id] should be unique over all the messages of users of a MessagingClientFactory.
 */
data class Message(val id: String, val fromUser: String, val message: String)

typealias Inbox = Map<String, List<Message>>


/**
 * This is a class implementing messaging between users
 */
class MessagingClient(
    private val username: String,
    private val password: String,
    private val storage: Storage,
    private val usersOnline: MutableSet<MessagingClient>,
    private val usersOffline: MutableSet<MessagingClient>,
    private var isLoggedIn: Boolean = false
) {
    /**
     * equals and hashCode overridden to enforce comparison by username
     */

    override fun equals(other: Any?): Boolean {
        return when(other){
            is MessagingClient -> username == other.username
            else -> false
        }
    }

    override fun hashCode(): Int {
        return username.hashCode()
    }

    /**
     * some helper private functions
     */

    private fun inboxRec(index: Int, inbox: Inbox, messageCount: Int) : CompletableFuture<Inbox> {
        return if (index >= messageCount) CompletableFuture.completedFuture(inbox)
        else CompletableFuture.completedFuture(Unit)
            .thenCompose { readMessage(index) }
            .thenCompose { message ->
                if (message == null) inboxRec(index+1, inbox, messageCount)
                else {
                    val oldList = inbox[message.fromUser] ?: listOf()
                    val newInbox = inbox.toMutableMap()
                    newInbox[message.fromUser] = oldList + listOf(message)
                    inboxRec(index+1, newInbox.toMap(), messageCount)
                }
            }
    }

    private fun readMessage(index: Int) : CompletableFuture<Message?> {
        return storage.read(username + "_" + index + "_id")
            .thenCompose { id ->
                if (id == null) CompletableFuture.completedFuture(null)
                else storage.read(username + "_" + index + "_message")
                    .thenCompose { message ->
                        if (message == null) CompletableFuture.completedFuture(null)
                        else storage.read(username + "_" + index + "_fromUser")
                            .thenApply { fromUser ->
                                if (fromUser == null) null
                                else Message(id, fromUser, message)
                            }
                    }
            }
    }

    private fun readCountMessages(username: String): CompletableFuture<String> {
        return storage.read("$username count")
            .thenApply { count ->
                count ?: "0"
            }
    }

    fun getUsername(): String {
        return this.username
    }

    /**
     * Login with a given password. A successfully logged-in user is considered "online". If the user is already
     * logged in, this is a no-op.
     *
     * @throws IllegalArgumentException If the password was wrong (according to the factory that created the instance)
     */
    fun login(password: String): CompletableFuture<Unit> =
        CompletableFuture.completedFuture(Unit)
            .thenApply {
                if (!isLoggedIn && password != this.password) throw IllegalArgumentException("login: wrong password")
                isLoggedIn = true
                usersOffline.removeIf { user -> user.username == username }
                usersOnline.add(this)
            }


    /**
     * Log out of the system. After logging out, a user is no longer considered online.
     *
     * @throws IllegalArgumentException If the user was not previously logged in.
     */
    fun logout(): CompletableFuture<Unit> =
        CompletableFuture.completedFuture(Unit)
            .thenApply {
                if (!isLoggedIn) throw (IllegalArgumentException("logout: isn't logged in"))
                isLoggedIn = false
                usersOnline.removeIf { user -> user.username == username }
                usersOffline.add(this)
            }

    /**
     * Get online (logged in) users.
     *
     * @throws PermissionException If the user is not logged in.
     * @return A list of usernames which are currently online.
     */
    fun onlineUsers(): CompletableFuture<List<String>> = CompletableFuture.completedFuture(Unit)
        .thenApply {
            if (!isLoggedIn) throw PermissionException()
            usersOnline.map { client -> client.username }
        }

    /**
     * Get messages currently in your inbox from other users.
     *
     * @return A mapping from usernames to lists of messages (conversations), sorted by time of sending.
     * @throws PermissionException If the user is not logged in.
     */
    fun inbox(): CompletableFuture<Inbox> {
        return CompletableFuture.completedFuture(Unit)
            .thenApply { if (!isLoggedIn) throw (PermissionException()) }
            .thenCompose { readCountMessages(username) }
            .thenCompose { messageCount ->
                inboxRec(0, mapOf(), messageCount.toInt())
            }
    }


    /**
     * Send a message to a username [toUsername].
     *
     * @throws PermissionException If the user is not logged in.
     * @throws IllegalArgumentException If the target user does not exist, or message contains more than 120 characters.
     */
    fun sendMessage(toUsername: String, messageString: String): CompletableFuture<Unit> =
        CompletableFuture.completedFuture(Unit)
        .thenApply {
            if (!isLoggedIn) throw(PermissionException())
            val users = (usersOffline + usersOnline).filter { user -> user.username == toUsername }
            if (users.isEmpty() || messageString.length > 120) {
                throw(IllegalArgumentException("sendMessage: users is empty or message is too long"))
            }
            else {
                readCountMessages(toUsername).thenApply {
                    count -> val message = Message(count, username, messageString)
                    storage.write(toUsername + "_" + count + "_id", message.id)
                        .thenCompose { storage.write("$toUsername $count message", message.message) }
                        .thenCompose { storage.write("$toUsername $count fromUser", message.fromUser) }
                        .thenCompose { storage.write("$toUsername count", (count.toInt() + 1).toString()) }
                }
            }
        }


    /**
     * Delete a message from your inbox.
     *
     * @throws PermissionException If the user is not logged in.
     * @throws IllegalArgumentException If a message with the given [id] does not exist
     */
    fun deleteMessage(id: String): CompletableFuture<Unit> {
        val fromUserKey = "$username $id message"
        val messageKey = "$username $id fromUser"
        val idKey = "$username $id id"
        return CompletableFuture.completedFuture(Unit)
            .thenApply {
                if (!isLoggedIn) throw (PermissionException())
                storage.read(idKey)
            }
            .thenCompose { idRead ->
                if (idRead == null) throw (IllegalArgumentException("deleteMessage: no such id"))
                else storage.delete(idKey)
                storage.read(fromUserKey)
            }
            .thenCompose { fromUserRead ->
                if (fromUserRead == null) throw (IllegalArgumentException("deleteMessage: no such user"))
                else storage.delete(fromUserRead)
                storage.read(messageKey)
            }
            .thenCompose { messageRead ->
                if (messageRead == null) throw (IllegalArgumentException())
                else storage.delete(messageRead)
            }
        }
}

/**
 * A factory for creating messaging clients that can send messages to each other.
 */
interface MessagingClientFactory {
    /**
     * Get an instance of a [MessagingClient] for a given username and password.
     * You can assume that:
     * 1. different clients will have different usernames.
     * 2. calling get for the first time creates a user with [username] and [password].
     * 3. calling get for an existing client (not the first time) is called with the right password.
     *
     * About persistence:
     * All inboxes of clients should be persistent.
     * Note: restart == a new instance is created and the previous one is not used.
     * When MessagingClientFactory restarts all users should be logged off.
     * When a MessagingClient restarts (another instance is created with [MessagingClientFactory]'s [get]), only the
     *  specific user is logged off.
     */
    fun get(username: String, password: String): CompletableFuture<MessagingClient>
}

class MessagingClientFactoryI @Inject constructor (private val storage : Storage) : MessagingClientFactory {
    private val usersOnline: MutableSet<MessagingClient> = mutableSetOf()
    private val usersOffline: MutableSet<MessagingClient> = mutableSetOf()

    override fun get(username: String, password: String): CompletableFuture<MessagingClient> {
        val filtered = (usersOnline + usersOffline).filter { user -> user.getUsername() == username }
        val oldUser: MessagingClient? = if (filtered.isEmpty()) null
        else filtered[0]

        oldUser?.logout()
        return if (oldUser == null) {
            val newUser = MessagingClient(username, password, storage, usersOnline, usersOffline, false)
            usersOffline.add(newUser)
            CompletableFuture.completedFuture(newUser)
        } else CompletableFuture.completedFuture(oldUser)
    }
}