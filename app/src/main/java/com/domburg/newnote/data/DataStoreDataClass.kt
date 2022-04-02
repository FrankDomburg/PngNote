package com.domburg.newnote.data

import android.net.Uri
import com.domburg.newnote.ThumbnailNewStyle
import javax.inject.Inject

class DataStoreDataClass {
}

/** What gets exposed to the viewmodel,
 *
 * suspend fun -> to provide a hook for actions on the data store
 * Flow<Type> -> to provide viewmodels with data
 *
 * */

data class BooksAsStored @Inject constructor(val uri: Uri, val thumbnail: ThumbnailNewStyle?, val name: String )

