import com.google.inject.Guice
import com.google.inject.Provider
import dev.misfitlabs.kotlinguice4.KotlinModule
import dev.misfitlabs.kotlinguice4.getInstance
import il.ac.technion.cs.softwaredesign.Storage
import il.ac.technion.cs.softwaredesign.StorageSystem
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture



class SecureStorageModule: KotlinModule() {
    override fun configure() {
        bind<SecureStorageFactory>().to<DummyStorageFactory>()
    }
}
internal class StorageSystemTest{
    
    private val MAX_SIZE = 100
    
    private fun assertWriteReadAndKeyExists(key: String, value: String){
        storage.write(key, value).join()
      //  println(stor0ageSystem.read(dbName, key))
        val foundValue = storage.read(key).get()
        //println("found: " + foundValue.toString() )
        assert( foundValue != null)
        assert(foundValue!!.contentEquals(value))
        assert(storage.keyExists(key).get())
    }
    fun assertDeleteNotExistAndNullRead(key: String){
        storage.delete(key).join()
        assert(storage.keyExists(key).get() == false )
        assert(storage.read(key).get() == null)
    }
    private fun newStorageSystem(): StorageSystem{
        val secureStorageFactory = injector.getInstance<Provider<SecureStorageFactory>>()
        return StorageSystem(secureStorageFactory)
    }
    private fun stringOfRange(start: Int, end: Int): String{
       return List(end-start) { it + start }.fold("") { a, b -> a + b }.toString()
    }
    private fun stringOfRange(end: Int): String{
        return stringOfRange(0, end)
    }
    companion object{
        
        private val injector = Guice.createInjector(SecureStorageModule())
        private var storage = StorageSystem(injector.getInstance<Provider<SecureStorageFactory>>())
        @BeforeAll
        @JvmStatic
        fun setup(){
            storage = StorageSystem(injector.getInstance<Provider<SecureStorageFactory>>())
        }
                
    }
    @Test
    fun `Complete the future value`(){
        val newfuture:CompletableFuture<Int> =  CompletableFuture<Int>()
        newfuture.complete(10)
        assert(newfuture.get() == 10)

    }
    @Test
    fun `Complete the Unit future`(){
        var a = 0
        val future = CompletableFuture.completedFuture(Unit).thenApply {a = 1}
        //println("Before get: " + a.toString())
        assert(a == 1)
    }
    @Test
    fun `key does not exist`(){
        val dbName = "somedb"
        val key = "somekey"
        val value = "somevalue"
        assert(!(storage.keyExists(key).get()))
    }

    @Test
    fun    `key exists`(){
        val dbName = "somedb"
        val key = "somekey"
        val value = "somevalue"
        storage.write( key, value).join()
        //println(storage.read(dbName,key))
      //  println (storage.keyExists(key).get())
        assert(storage.keyExists(key).get())
    }
    @Test
    fun `writing of small data`() {
        val dbName = "somedb"
        val key = "somekey"
        val value = "somevalue"
        assertWriteReadAndKeyExists( key, value)
        assertDeleteNotExistAndNullRead(key)
    }
    @Test
    fun `writing of max data`(){
        val dbName = "somedb"
        val key = "somekey"
        val value = stringOfRange(5*MAX_SIZE)
        assert(value.toByteArray().size >= MAX_SIZE)
        assertWriteReadAndKeyExists(key, value)
        assertDeleteNotExistAndNullRead(key)
    }
    @Test
    fun `rewrite of small data`(){
        val storageSystem = newStorageSystem()
        val dbName = "somedb"
        val key = "somekey"
        val value = "somevalue"
        val value2 = "somevalue2"
        storageSystem.write(key, value)
        assertWriteReadAndKeyExists(key, value2)
        assertDeleteNotExistAndNullRead(key)
    }
    @Test
    fun `rewrite of max data`(){
        val storageSystem = newStorageSystem()
        val dbName = "somedb"
        val key = "somekey"
        val value = stringOfRange(5*MAX_SIZE)
        assert(value.toByteArray().size >= MAX_SIZE)
        val value2 = stringOfRange(5*MAX_SIZE,80*MAX_SIZE )
        assert(value2.toByteArray().size >= MAX_SIZE)
        assertWriteReadAndKeyExists(key, value)
        assertWriteReadAndKeyExists(key, value2)
        assertDeleteNotExistAndNullRead(key)
        assertDeleteNotExistAndNullRead(key)
    }
    @Test
    fun `rewrite of large data with small data`(){
        val storageSystem = newStorageSystem()
        val dbName = "somedb"
        val key = "somekey"
        val value = stringOfRange(5*MAX_SIZE)
        assert(value.toByteArray().size >= MAX_SIZE)
        val value2 = "somevalue"
        assertWriteReadAndKeyExists(key, value)
        assertWriteReadAndKeyExists(key, value2)
        assertDeleteNotExistAndNullRead(key)
        assertDeleteNotExistAndNullRead(key)
    }
    @Test
    fun `rewrite of small data with large data`(){
        val storageSystem = newStorageSystem()
        val dbName = "somedb"
        val key = "somekey"
        val value = "somevalue"
        val value2 = stringOfRange(5*MAX_SIZE)
        assert(value2.toByteArray().size >= MAX_SIZE)
        assertWriteReadAndKeyExists(key, value)
        assertWriteReadAndKeyExists(key, value2)
        assertDeleteNotExistAndNullRead(key)
        assertDeleteNotExistAndNullRead(key)
    }
    @Test
    fun `extended key extended collision`(){
        val storageSystem = newStorageSystem()
        val dbName = "somedb"
        val key = "somekey"
        val key2 = "somekey_2"
        val value = stringOfRange(5*MAX_SIZE)
        //print(value)
        assert(value.toByteArray().size >= MAX_SIZE)
        val value2 = stringOfRange(5*MAX_SIZE,80*MAX_SIZE )
        assert(value2.toByteArray().size >= MAX_SIZE)
        assertWriteReadAndKeyExists(key, value)
        assertWriteReadAndKeyExists(key2, value2)
        assertDeleteNotExistAndNullRead(key)
        assertDeleteNotExistAndNullRead(key2)
    }
    @Test
    fun `collision of extended key with short key`(){
        val storageSystem = newStorageSystem()
        val dbName = "somedb"
        val key = "somekey"
        val key2 = "somekey_2"
        val value = stringOfRange(5 * MAX_SIZE )
        assert(value.toByteArray().size >= MAX_SIZE)
        val value2 = "somevalue"
        assertWriteReadAndKeyExists(key, value)
        assertWriteReadAndKeyExists(key2, value2)
        assertDeleteNotExistAndNullRead(key)
        assertDeleteNotExistAndNullRead(key2)
    }
}