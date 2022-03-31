package com.domburg.newnote

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.domburg.newnote.preferences.PrefManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.Serializable

data class ThumbnailNewStyle(
    val foreground: Outcome<Bitmap>,
    val background: Outcome<Bitmap>
) {}

data class BookListData(val uri: Uri, val thumbnail: ThumbnailNewStyle, val name: String) {
}

data class BooklistState(
    val isLoading: Boolean,
    val successMessage: String,
    val failureMessage: String
) : State, Serializable

sealed class BookListAction : Action, Serializable {
    // Specify Actions from the UI here
    data class Greet(val nickname: String) : BookListAction()
}

interface Action
interface State

class BookListActivityViewModel(
    application: Application,
    prefManager: PrefManager,
    repository: BooksRepository,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    /** To receive Actions from the UI */
    val actionFlow = MutableSharedFlow<BookListAction>()
    private val mutableState = MutableStateFlow(BooklistState(false, "", ""))

    init {
        // Need to determine initial state
        prefManager.getUri() ?: null
        viewModelScope.launch {
            handleActions()
        }
        savedStateHandle.get<BooklistState>("State")?.let { savedState ->
            mutableState.value = savedState
        }
    }


    /** To emit states to the UI */
    private val booklistState = MutableStateFlow(BooklistState(false, "", ""))
    val state: StateFlow<BooklistState>
        get() = mutableState

    private suspend fun handleActions() {
        actionFlow.collect { action ->
            when (action) {
                is BookListAction.Greet -> {
                    // update de state
                }
            }
        }
    }

    private fun applyState(newState: BooklistState) {
        savedStateHandle["state"] = newState
        mutableState.value = newState
    }

}
