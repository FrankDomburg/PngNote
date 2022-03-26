package io.github.karino2.pngnote

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider


class ModelViewFactory(
    private val application: Application
): ViewModelProvider.NewInstanceFactory() {
    override fun <T: ViewModel> create(modelClass:Class<T>): T {
        return BookListActivityViewModel(application) as T
    }
}