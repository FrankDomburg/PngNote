package com.domburg.newnote

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.domburg.newnote.preferences.PrefManager


class ModelViewFactory(
    private val application: Application
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>, prefManager: PrefManager): T {
        return BookListActivityViewModel(application, prefManager) as T
    }
}