import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap



class ByteArrayKey(private val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || other is ByteArrayKey && this.bytes contentEquals other.bytes

    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = bytes.contentToString()
}
class DummyStorage: SecureStorage{

    //val storageName: ByteArray
    val storage = ConcurrentHashMap<ByteArrayKey, ByteArray>()
    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
        //println("writeKey: " + key.getString() +" writeData: "+ value.getString())
        return CompletableFuture.completedFuture(Unit).thenApply{storage[ByteArrayKey(key)] = value.clone()}
    }

    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        //println("readKey: " + key.getString() +" readData: "+ storage[ByteArrayKey(key)]?.getString())
        return CompletableFuture.completedFuture(storage[ByteArrayKey(key)])
    }
    private fun ByteArray.getString(): String{
        val cts = contentToString().trim('[').trim(']').split(", ")
        // println("a:" +cts)
        val charList = cts.map{ it.toInt().toChar()}
        //println(charList)
        val str = charList.fold("",{ acc, s -> acc + s})
        // println(str)
        return str
    }

}
class DummyStorageFactory : SecureStorageFactory {
    private var storageMap = ConcurrentHashMap<ByteArrayKey, CompletableFuture<SecureStorage>>()
    override fun open(name: ByteArray): CompletableFuture<SecureStorage> {
        //println("Open: " + name.toString())
        return CompletableFuture.completedFuture(Unit)
            .thenApply {  storageMap.containsKey(ByteArrayKey(name))}
            .thenCompose {
            when(it) {
                false -> CompletableFuture.completedFuture(Unit)
                    .thenApply{storageMap[ByteArrayKey(name)] = CompletableFuture.completedFuture(DummyStorage())}
                    .thenCompose { storageMap[ByteArrayKey(name)] }
                else -> CompletableFuture.completedFuture(Unit).thenCompose { storageMap[ByteArrayKey(name)]}
            }
            }
    }
}