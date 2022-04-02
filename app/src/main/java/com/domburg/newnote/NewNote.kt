package com.domburg.newnote

import android.app.Application
import android.content.Context
import com.domburg.newnote.data.BookLocalDataSource
import com.domburg.newnote.data.BookRepositoryStorage
import com.domburg.newnote.data.BooksRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.internal.managers.ApplicationComponentManager
import dagger.hilt.android.internal.managers.ViewComponentManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp
class NewNote : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}

// @Module annotation which will make this class a module
// to inject dependency to other class within it's scope.
// @InstallIn(SingletonComponent::class) this will make
// this class to inject dependencies across the entire application.
//@Module
//@InstallIn(ViewModelComponent::class)
//class AppModule {
//    @Module
//    @InstallIn(SingletonComponent::class)
//    interface RepositoryModules {
//        @Binds
//        fun provideBooksRepository(repository: BooksRepository) : BooksRepository
//        {
//            return BooksRepository()
//        }
//    }
//}

