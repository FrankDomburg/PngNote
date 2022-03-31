package com.domburg.newnote

import android.net.Uri

/** What gets exposed to the viewmodel,
 *
 * suspend fun -> to provide a hook for actions on the data store
 * Flow<Type> -> to provide viewmodels with data
 *
 * */

data class BooksAsStored(val uri: Uri, val thumbnail: ThumbnailNewStyle?, val name: String)


class BooksRepository(
    private val booksLocalDataSource: BookLocalDataSource,

    ) : BookRepositoryStorage {
    override fun authorizeAccess(): Outcome<Any> {
        return booksLocalDataSource.authorizeAccess()
    }


    override fun getListOfBooks(): List<BooksAsStored> {
        TODO("Not yet implemented")
    }

}

interface BookRepositoryStorage {
    /**
     * Check if it is possible to access the data store
     * Implementation assumes data is stored in PrefManager
     * (for now)
     */
    fun authorizeAccess(): Outcome<Any>
    fun getListOfBooks(): List<BooksAsStored>

}

sealed class Outcome<out T : Any> {
    data class Success<out T : Any>(val message: T) : Outcome<T>()
    data class NoAuthDataReceived<out T : Any>(val message: T) : Outcome<T>()
    data class NotAccessible<out T : Any>(val message: Any?, val cause: Exception? = null) :
        Outcome<T>()

    /** may be possible to merge into [NotAccessible] */
    data class NotFound<out T : Any>(val message: Any?) : Outcome<T>()
}