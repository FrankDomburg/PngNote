package io.github.karino2.pngnote

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.karino2.pngnote.data.preferences.PrefManager


class ModelViewFactory(
    private val application: Application
): ViewModelProvider.NewInstanceFactory() {
    override fun <T: ViewModel> create(modelClass:Class<T>, prefManager:PrefManager): T {
        return BookListActivityViewModel(application, prefManager) as T
    }
}