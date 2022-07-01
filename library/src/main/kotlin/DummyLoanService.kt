import il.ac.technion.cs.softwaredesign.loan.LoanService
import java.util.concurrent.CompletableFuture

class DummyLoanService: LoanService{
    val bookMap = CompletableFuture<MutableMap<String, BookStatus>>()

    override fun loanBook(id: String): CompletableFuture<Unit> {

       return bookMap.thenApply { map-> map[id] = BookStatus.LOANED }
    }

    override fun returnBook(id: String): CompletableFuture<Unit> {
        return bookMap.thenApply { map-> map[id] = BookStatus.AVAILABLE }
    }
    companion object {
        enum class BookStatus {

            AVAILABLE,
            LOANED
        }
    }

}