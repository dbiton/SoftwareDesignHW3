package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import com.google.inject.Provider

import il.ac.technion.cs.softwaredesign.storage.SecureStorage

import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

import java.util.concurrent.CompletableFuture

interface Storage{


    fun write( key: String, value: String): CompletableFuture<Unit>
    fun read(key: String): CompletableFuture<String?>
    fun delete(key: String): CompletableFuture<Unit>
    fun keyExists(key: String): CompletableFuture<Boolean>
}

/**
 * A wrapping library for the primitive storage layer [SecureStorageFactory], with the [CompletableFuture] monad.
 *
 * @constructor Creates an empty storage.
 * @property storageProvider: The primitive storage layer provider.
 */
class StorageSystem @Inject constructor(private val storageProvider: Provider<SecureStorageFactory>): Storage{
    private val storage = storageProvider.get()
    private val MAX_SIZE = 100
    private val separator = "_"
    private val dbName = "dbName".toByteArray()

    /**
     * Writes a string [value] into the primitive storage to [read] with [key]
     * @param key The key to which to [write]
     * @param value The value to write
     * @return a future to be completed when the [write] action is finished.
     */
    override fun write(key: String, value: String): CompletableFuture<Unit>{
        return extendedWrite(key, value)
    }
    /**
     * Reads the stored value of [key] from the primitive storage.
     * @param key The key from which to [read]
     * @return a future to be completed when the [read] action is finished. The future contains:
     *   - A string of value, If a value with [key] was stored using the [write] method
     *   - Else, null
     */
    override fun read( key: String): CompletableFuture<String?>{
        return keyExists(key).thenCompose {
            when(it!!){
                true -> extendedRead( key)
                false-> CompletableFuture.completedFuture<String?>(null)
            }
        }
    }
    /**
     * Deletes a [key] and it's value from the primitive storage.
     * If the key was not written to, this method does nothing
     *
     * @param key The key to [delete]
     * @return A future to be completed when the [delete] action is finished.
     */
    override fun delete(key: String): CompletableFuture<Unit>{
        return extendedWrite(key, "")
            .thenCompose{ extendedDataSize(key) }
            .thenApply { assert(it == 0) }
    }
    /**
     * Indicates the existence of a [key] stored in primitive storage with [write]
     *
     * @param key The key to check its existence
     * @return A future to be completed when check is finished,
     * containing true if the was written to, and false if it wasn't.
     */
    override fun keyExists(key: String): CompletableFuture<Boolean> {
        return CompletableFuture.completedFuture(Unit).
        thenCompose<ByteArray?>{rawRead(extendedSizeKey(key).toByteArray())}.
        thenApply{
            when(it != null){
                false -> false
                true -> it.getString().toInt() > 0
            }
        }
    }

    private fun ByteArray.getString(): String{
        val cts = contentToString().trim('[').trim(']').split(", ")
        val charList = cts.map{ it.toInt().toChar()}
        val str = charList.fold("",{ acc, s -> acc + s})
        return str
    }

    private fun open():CompletableFuture<SecureStorage>{
        return storage.open(dbName)
    }

    private fun rawRead(key: ByteArray): CompletableFuture<ByteArray?>{
        return open().thenCompose { it.read(key) }
    }

    private fun rawWrite(key: ByteArray, value: ByteArray): CompletableFuture<Unit>{
        return open().thenCompose { it.write(key, value) }
    }

    private fun extendedSizeKey(key: String): String{
        return extendedKey(key, -1)
    }

    private fun extendedKey(key: String, i: Int): String{
        return key + separator + i.toString()
    }

    private fun updateExtendedDataSize(key: String, size: Int): CompletableFuture<Unit>{
        return rawWrite(extendedSizeKey(key).toByteArray(), size.toString().toByteArray())
    }
    private fun extendedDataSize(key: String): CompletableFuture<Int> {
        return rawRead(extendedSizeKey(key).toByteArray())
            .thenApply { it!!.getString().toInt() }

    }
    private fun extendedWrite(key: String, data: String): CompletableFuture<Unit>{
        fun writeAux(auxKey: String, auxValue: ByteArray): CompletableFuture<Unit>{ // Efficiency
            return rawWrite(auxKey.toByteArray(), auxValue)
        }
        val dataByteArray = data.toByteArray()
        val dataSize = dataByteArray.size
        val numOfFullBlocks = (dataSize / MAX_SIZE).toInt()
        val blockList = MutableList(numOfFullBlocks) {
            Pair(
                it,
                dataByteArray.copyOfRange(it * MAX_SIZE, it * MAX_SIZE + MAX_SIZE)
            )
        }
        assert((dataSize <= MAX_SIZE && blockList.size == 0)
                ||(dataSize > MAX_SIZE && blockList.size >= 1))

        val residualSize = dataSize - numOfFullBlocks * MAX_SIZE
        if (residualSize > 0){
            val numOfBlocks = blockList.size
            blockList.add(
                Pair(numOfBlocks,
                    dataByteArray.copyOfRange(dataSize - residualSize, dataSize)))
        }
        val writeString = blockList.map{it.second.getString()}.fold("",{ acc, s -> acc + s})
        return CompletableFuture.completedFuture(Unit)
            .thenApply { blockList.forEach{
            writeAux(extendedKey(key, it.first), it.second) }
            }
            .thenApply { assert( writeString == data)}
            .thenCompose {updateExtendedDataSize( key, blockList.size)}
            .thenCompose{extendedDataSize(key)}
            .thenApply { assert( it == blockList.size)}
            .thenCompose { keyExists(key) }.thenApply { assert(blockList.size == 0 || (blockList.size > 0 && it)) }
            .thenCompose { read(key)}.thenApply { assert(it == null || (it == writeString) )}
    }

    private fun extendedRead(key: String): CompletableFuture<String?>{
        fun longExtendedRead(storage: SecureStorage, numOfBlocks: Int): CompletableFuture<String>{
                val byteList = CompletableFuture.completedFuture(mutableListOf<Byte>())
                MutableList<CompletableFuture<ByteArray?>>(numOfBlocks)
                { storage.read(extendedKey(key, it).toByteArray())}
                    .map{futureByteArray ->
                        futureByteArray
                            .thenApply {realByteArray->
                    realByteArray!!
                        .forEach{byte -> byteList.thenApply {it.add(byte)}
                        }
                  }
            }
            return byteList.thenApply { it.toByteArray().getString()}
        }
        val storage = storage.open(dbName)
        return extendedDataSize(key)
            .thenApply { Pair(it, storage) }.thenCompose{ pair  ->
            when (pair.first == 0) {
                true -> CompletableFuture.completedFuture(null)
                false -> pair.second.thenCompose{longExtendedRead(it,  pair.first)}
            }
        }
    }


}