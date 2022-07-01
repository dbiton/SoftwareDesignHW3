package il.ac.technion.cs.softwaredesign

import DummyStorageFactory
import dev.misfitlabs.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import il.ac.technion.cs.softwaredesign.storage.impl.SecureStorageFactoryImpl
import il.ac.technion.cs.softwaredesign.storage.impl.SecureStorageImpl


class SecureStorageModule: KotlinModule() {
    override fun configure(){
        bind<SecureStorageFactory>().to<SecureStorageFactoryImpl>()

    }

}