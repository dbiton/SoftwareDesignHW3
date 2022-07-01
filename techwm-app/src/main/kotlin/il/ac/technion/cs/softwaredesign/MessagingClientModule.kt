package il.ac.technion.cs.softwaredesign

import dev.misfitlabs.kotlinguice4.KotlinModule

class MessagingClientModule: KotlinModule() {
    override fun configure() {
        bind<Storage>().to<StorageSystem>()
        bind<MessagingClientFactory>().to<MessagingClientFactoryI>()
        install(SecureStorageModule())
    }
}