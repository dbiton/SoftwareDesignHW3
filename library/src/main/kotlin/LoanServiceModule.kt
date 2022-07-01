package il.ac.technion.cs.softwaredesign

import DummyLoanService

import dev.misfitlabs.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.loan.LoanService
import il.ac.technion.cs.softwaredesign.loan.impl.LoanServiceImpl


class LoanServiceModule: KotlinModule() {
        override fun configure(){
        bind<LoanService>().to<LoanServiceImpl>()

        }
}