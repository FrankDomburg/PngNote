package io.github.karino2.pngnote

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.ButtonDefaults.textButtonColors
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import io.github.karino2.pngnote.data.preferences.PrefManager
import io.github.karino2.pngnote.ui.theme.PngNoteTheme
import io.github.karino2.pngnote.ui.theme.booxTextButtonColors
import io.github.karino2.pngnote.utils.FastFile
import io.github.karino2.pngnote.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Thumbnail(val page: Bitmap = blankBitmapFg, val bg: Bitmap = blankBitmapBg)
{
    companion object {
        /** Blank Page Foreground */
        private val blankBitmapFg: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(
            android.graphics.Color.WHITE) }
        /** Blank Page Background */
        private val blankBitmapBg: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(
            android.graphics.Color.LTGRAY) }

        private fun loadBitmapThumbnail(file: FastFile, sampleSize: Int, resolver: ContentResolver) :Bitmap {
            return resolver.openFileDescriptor(file.uri, "r").use {
                val option = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor, null, option)
            }
        }

        private fun loadThumbnail(
            bookDir: FastFile,
            displayName: String,
            resolver: ContentResolver
        ): Bitmap? {
            return bookDir.findFile(displayName)?.let { loadBitmapThumbnail(it, 3, resolver) }
        }

        /**
         *
         * Creates a thumbnail based on the FastFile reference and IO Access
         * If different layouts per page are desired, we are doing it based on
         * file naming convention (add -bg to filename)
         *
         * TODO Or make thumbnail based on JSON linking pages and to their backgrounds - so that backgrounds are referenced (needs refinement)
         *
         * This is to replace the stuff in BookListActivity - for now
         *
         */

        fun fromFastFileDirect(source: FastFile, resolver: ContentResolver) : Thumbnail {
            val page = loadThumbnail(source, "0000.png", resolver) ?: blankBitmapFg
            val bg = loadThumbnail(source, "0000-bg.png", resolver) ?: blankBitmapBg
            return Thumbnail(page, bg)
        }
    }
}


class BookListActivity : ComponentActivity() {

    private lateinit var viewModel : BookListActivityViewModel

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
        get() = PrefManager.getUri()

    private fun writeLastUri(uri: Uri) = PrefManager.setUri(uri)

    private fun openRootDir(url: Uri) {
        _url = url
        reloadBookList(url)
    }

    private val files = MutableLiveData(emptyList<FastFile>())
    private val thumbnails =  Transformations.switchMap(files) { flist ->
        liveData {
            emit(flist.map { Thumbnail() })
            withContext(lifecycleScope.coroutineContext + Dispatchers.IO) {
                val thumbs = flist.map {
                    Thumbnail.fromFastFileDirect(it, bookIO.getResolver())

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

        // about half of 80%〜90%.

        val height = (metrics.heightPixels*0.40/metrics.density).dp
        val width = (metrics.widthPixels*0.45/metrics.density).dp

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

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        viewModel = ModelViewFactory(application = application).create(BookListActivityViewModel::class.java)
        return super.onCreateView(name, context, attrs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefManager.init(applicationContext)
        setContent {
            PngNoteTheme {
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
                        Image(painterResource(id = R.mipmap.ic_launcher), null)
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

        try {
            lastUri?.let {
                return openRootDir(it)
            }
        } catch(_: Exception) {
            toast("Can't open dir. Please re-open.")
        }
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
fun BookList(bookDirs: LiveData<List<FastFile>>, thumbnails: LiveData<List<Thumbnail>>, bookSize : Pair<Dp, Dp>,  gotoBook : (dir: FastFile)->Unit) {
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
        Canvas(modifier= Modifier
            .size(bookSize.first, bookSize.second)
            .padding(5.dp, 10.dp)) {
            val blendMode = thumbnail.bg?.let { bg->

                drawImage(bg.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                BlendMode.Multiply
            } ?: BlendMode.SrcOver
            drawImage(thumbnail.page.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()), blendMode=blendMode)
        }
        Text(bookName, fontSize = 20.sp)
    }
}
