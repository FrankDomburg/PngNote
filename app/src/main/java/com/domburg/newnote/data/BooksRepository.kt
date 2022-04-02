package com.domburg.newnote.data

import android.app.Application
import android.net.Uri
import com.domburg.newnote.ThumbnailNewStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.internal.managers.ApplicationComponentManager
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton


class BooksRepository @Inject constructor (
    private val booksLocalDataSource: BookLocalDataSource,

    ) : BookRepositoryStorage
{
    override fun authorizeAccess(): Outcome<Any> {
        return booksLocalDataSource.authorizeAccess()
    }


    override fun getListOfBooks(): List<BooksAsStored> {
        TODO("Not yet implemented")
    }

}

interface BookRepositoryStorage{
    /**
     * Check if it is possible to access the data store
     * Implementation assumes data is stored in PrefManager
     * (for now)
     */
    fun authorizeAccess() : Outcome<Any>
    fun getListOfBooks() : List<BooksAsStored>

}

sealed class Outcome<out T : Any> {

    data class Success<out T: Any>(val message: T) : Outcome<T>()

    data class NoAuthDataReceived<out T: Any>(val message : T) : Outcome<T>()

    data class NotAccessible<out T: Any>(val message: Any?, val cause: Exception? = null) : Outcome<T>()
    /** may be possible to merge into [NotAccessible] */

    data class NotFound<out T: Any>(val message: Any?) : Outcome<T>()
}