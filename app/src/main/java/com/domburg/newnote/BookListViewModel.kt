package com.domburg.newnote

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.*
import com.domburg.newnote.data.BooksRepository
import com.domburg.newnote.data.Outcome
import com.domburg.newnote.data.preferences.PrefManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.Serializable
import javax.inject.Inject

data class ThumbnailNewStyle(
    val foreground : Outcome<Bitmap>,
    val background : Outcome<Bitmap>
){}

data class BookListData(val uri: Uri, val thumbnail: ThumbnailNewStyle, val name: String ) {
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

@HiltViewModel
class BookListActivityViewModel @Inject constructor(
    application: Application,
    prefManager: PrefManager,
    repository: BooksRepository,
    private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    /** To receive Actions from the UI */
    val actionFlow = MutableSharedFlow<BookListAction>()
    private val mutableState = MutableStateFlow(BooklistState(false, "",""))

    init{
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
        actionFlow.collect {
                action ->
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
