package com.domburg.newnote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import com.domburg.newnote.R.mipmap.ic_launcher

import com.domburg.newnote.data.preferences.PrefManager
import com.domburg.newnote.theme.NewNoteTheme
import com.domburg.newnote.theme.booxTextButtonColors
import com.domburg.newnote.utils.FastFile
import com.domburg.newnote.utils.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class Thumbnail(val page: Bitmap, val bg: Bitmap?)

@AndroidEntryPoint
class BookListActivity (): ComponentActivity()
{

    @Inject
    lateinit var prefManager: PrefManager

    private val viewModel: BookListActivityViewModel by viewModels()

    private var _url : Uri? = null

    private val getRootDirUrl = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        // if cancel, null coming.
        uri?.let {
            contentResolver.takePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            writeLastUri(it)
            openRootDir(it)
        }
    }

    private val lastUri: Uri?
        get() = prefManager.getUri()

    private fun writeLastUri(uri: Uri) = prefManager.setUri(uri)



    private fun openRootDir(url: Uri) {
        _url = url
        reloadBookList(url)
    }

    private val files = MutableLiveData(emptyList<FastFile>())
    private val thumbnails =  Transformations.switchMap(files) { flist ->
        liveData {
            emit(flist.map { Thumbnail(blankBitmap, null) })
            withContext(lifecycleScope.coroutineContext + Dispatchers.IO) {
                val thumbs = flist.map {
                    val page = bookIO.loadThumbnail(it) ?: blankBitmap
                    val bg = bookIO.loadBgThumbnail(it)
                    Thumbnail(page, bg)
                }
                withContext(Dispatchers.Main) {
                    emit(thumbs)
                }
            }
        }
    }

    private fun reloadBookList(url: Uri) {
        listFiles(url).also { flist->
            files.value = flist
        }
    }

    private fun listFiles(url: Uri): List<FastFile> {
        val rootDir = FastFile.fromTreeUri(this, url)

        if (!rootDir.isDirectory)
            throw Exception("Not directory")

        return rootDir.listFiles()
            .filter { it.isDirectory }
            .toList()
            .sortedByDescending { it.name }
    }

    private val bookSizeDP by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // about half of 80%???90%.

        val height = (metrics.heightPixels*0.40/metrics.density).dp
        val width = (metrics.widthPixels*0.45/metrics.density).dp

        Pair(width, height)
    }

    private val bookSizeInt by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // about half of 80%???90%.

        val height = (metrics.heightPixels*0.40).toInt()
        val width = (metrics.widthPixels*0.45).toInt()

        Pair(width, height)
    }

    private val bookIO by lazy { BookIO(contentResolver) }

    override fun onRestart() {
        super.onRestart()

        // return from other activity, etc.
        if(true == files.value?.isNotEmpty()) {
            reloadBookList(_url!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
//        try {
//            Class.forName("dalvik.system.CloseGuard")
//                .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
//                .invoke(null, true)
//        } catch (e: ReflectiveOperationException) {
//            throw RuntimeException(e)
//        }
        super.onCreate(savedInstanceState)
        setContent {
            NewNoteTheme {
                Column {
                    val showDialog = rememberSaveable { mutableStateOf(false) }
                    TopAppBar(title={Text("Book List")}, actions = {
                        IconButton(onClick={ showDialog.value = true }) {
                            Icon(Icons.Filled.Add, "New Book")
                        }
                        IconButton(onClick={ getRootDirUrl.launch(null) }) {
                            Icon(Icons.Filled.Settings, "Settings")
                        }
                    }, navigationIcon = {
                        Image(painterResource(id = ic_launcher), null)
                    })
                    if (showDialog.value) {
                        NewBookPopup(onNewBook = { addNewBook(it) }, onDismiss= { showDialog.value = false })
                    }
                    BookList(files, thumbnails, bookSizeDP,
                        gotoBook = { bookDir ->
                        Intent(this@BookListActivity, BookActivity::class.java).also {
                            it.data = bookDir.uri
                            startActivity(it)
                        }
                    })
                }
            }
        }
        // Datastore action
        try {
            lastUri?.let {
                return openRootDir(it)
            }
        } catch(_: Exception) {
            toast("Can't open dir. Please re-open.")
        }
        // UI Action
        getRootDirUrl.launch(null)
    }

    private fun addNewBook(newBookName: String) {
        val rootDir = _url?.let { FastFile.fromTreeUri(this, it) } ?: throw Exception("Can't open dir")
        try {
            rootDir.createDirectory(newBookName)
            openRootDir(_url!!)
        } catch(_: Exception) {
            toast("Can't create book directory ($newBookName).")
        }
    }

}


@Composable
fun NewBookPopup(onNewBook : (bookName: String)->Unit, onDismiss: ()->Unit) {
    var textState by remember { mutableStateOf("") }
    val requester = FocusRequester()
    val buttonColors = booxTextButtonColors()
    AlertDialog(
        modifier=Modifier.border(width=1.dp, MaterialTheme.colors.onPrimary),
        onDismissRequest = onDismiss,
        text = {
            Column {
                TextField(value = textState, onValueChange={textState = it},
                    modifier= Modifier
                        .border(width = 1.dp, MaterialTheme.colors.onPrimary)
                        .fillMaxWidth()
                        .focusRequester(requester),
                    placeholder = { Text("New book name")})
                DisposableEffect(Unit) {
                    requester.requestFocus()
                    onDispose {}
                }
            }
        },
        confirmButton = {
            TextButton(modifier=Modifier.border(width=1.dp, MaterialTheme.colors.onPrimary),
                colors = buttonColors, onClick= {
                onDismiss()
                if(textState != "") {
                    onNewBook(textState)
                }
            }) {
                Text("CREATE")
            }
        },
        dismissButton = {
            TextButton(modifier=Modifier.border(width=1.dp, MaterialTheme.colors.onPrimary),
                colors = buttonColors, onClick= onDismiss) {
                Text("CANCEL")
                Spacer(modifier = Modifier.width(5.dp))
            }
        }

    )
}

val blankBitmap: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(
    android.graphics.Color.LTGRAY) }


@Composable
fun BookList(bookDirs: LiveData<List<FastFile>>, thumbnails: LiveData<List<Thumbnail>>, bookSize : Pair<Dp, Dp>, gotoBook : (dir: FastFile)->Unit) {
    val bookListState = bookDirs.observeAsState(emptyList())
    val thumbnailListState = thumbnails.observeAsState(emptyList())

    Column(modifier= Modifier
        .padding(10.dp)
        .verticalScroll(rememberScrollState())) {
        val rowNum = (bookListState.value.size+1)/2
        0.until(rowNum).forEach { rowIdx ->
            TwoBook(bookListState.value, thumbnailListState.value,rowIdx*2, bookSize, gotoBook)
        }
    }
}

@Composable
fun TwoBook(books: List<FastFile>, thumbnails: List<Thumbnail>, leftIdx: Int, bookSize : Pair<Dp, Dp>, gotoBook : (dir: FastFile)->Unit) {
    Row {
        Card(modifier= Modifier
            .weight(1f)
            .padding(5.dp), border= BorderStroke(2.dp, Color.Black)) {
            val book =books[leftIdx]
            Book(book.name, bookSize, thumbnails[leftIdx], onOpenBook = { gotoBook(book) })
        }
        if (leftIdx+1 < books.size) {
            Card(modifier= Modifier
                .weight(1f)
                .padding(5.dp), border= BorderStroke(2.dp, Color.Black)) {
                val book =books[leftIdx+1]
                Book(book.name, bookSize, thumbnails[leftIdx+1], onOpenBook = { gotoBook(book) })
            }
        } else {
            Card(modifier=Modifier.weight(1f)) {}
        }
    }
}

@Composable
fun Book(bookName: String, bookSize : Pair<Dp, Dp>, thumbnail: Thumbnail, onOpenBook : ()->Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier= Modifier
        .clickable(onClick = onOpenBook)) {
        ThumbnailImage(thumbnail = thumbnail, size = bookSize )
        Text(bookName , fontSize = 20.sp)
    }
}

@Composable
fun ThumbnailImage(thumbnail: Thumbnail, size: Pair<Dp, Dp>) {
    Canvas(modifier= Modifier
        .size(size.first, size.second)
        .padding(5.dp, 10.dp)
    ) {
        // Ugly hack - the viewmodel should only have access to nicely formed thumbnails
        val blendMode = thumbnail.bg?.let { bg ->
            drawImage(
                bg.asImageBitmap(),

                )
            BlendMode.Multiply
        } ?: BlendMode.SrcOver
        drawImage(
            thumbnail.page.asImageBitmap(),

            blendMode = blendMode
        )
    }
}
