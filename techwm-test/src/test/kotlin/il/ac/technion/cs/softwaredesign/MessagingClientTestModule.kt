package il.ac.technion.cs.softwaredesign

import DummyStorage
import DummyStorageFactory
import dev.misfitlabs.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

class MessagingClientTestModule: KotlinModule() {
    override fun configure() {
        bind<MessagingClientFactory>().to<MessagingClientFactoryI>()
        bind<Storage>().to<StorageSystem>()
        bind<SecureStorage>().to<DummyStorage>()
        bind<SecureStorageFactory>().to<DummyStorageFactory>()
    }
}