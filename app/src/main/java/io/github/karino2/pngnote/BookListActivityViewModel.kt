package io.github.karino2.pngnote

import android.app.Application
import androidx.lifecycle.AndroidViewModel

data class BookListActivityData(
    val isGood : Boolean = false
)

class BookListActivityViewModel(application: Application) : AndroidViewModel(application) {
}