package com.domburg.newnote


import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.applyCanvas
import androidx.lifecycle.*
import com.domburg.newnote.preferences.PrefManager
import com.domburg.newnote.utils.FastFile
import com.domburg.newnote.utils.toast
import dagger.hilt.android.HiltAndroidApp
import io.github.karino2.pngnote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class Thumbnail private constructor(
    val page: Bitmap,
    val bg: Bitmap,
    val thumbSize: Pair<Int, Int>
) {

    override
    fun toString(): String {
        return "page size is ${page.width} by ${page.height}, background size is ${bg.width} by ${bg.height}, thumbsize is ${thumbSize.first} by ${thumbSize.second}"
    }

    fun toBitmap(): Bitmap {
//        var target = ImageBitmap(thumbSize.first, thumbSize.second, ImageBitmapConfig.Argb8888)
//        var comboImage = Canvas(target)

        val paint = android.graphics.Paint()
            .apply { blendMode = android.graphics.BlendMode.MULTIPLY }

        val result = bg.applyCanvas {

            this.drawBitmap(page, null, Rect(0, 0, bg.width, bg.height), null)

        }

        return bg

    }

    companion object {
        /** Blank Page Foreground */
        private fun blankBitmapFg(size: Pair<Int, Int>): Bitmap =
            Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888).apply {
                eraseColor(
                    android.graphics.Color.LTGRAY
                )
            }

        /** Blank Page Background */
        private fun blankBitmapBg(size: Pair<Int, Int>): Bitmap {
            Log.i("@@", "blankBitmapBg created with size: ${size.first} by ${size.second}")

            return Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888).apply {
                eraseColor(
                    android.graphics.Color.WHITE
                )
            }
        }

        private fun loadBitmapThumbnail(
            file: FastFile,
            sampleSize: Int,
            resolver: ContentResolver
        ): Bitmap {
            return resolver.openFileDescriptor(file.uri, "r").use {
                val option = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor, null, option)
            }
        }

        private fun loadThumbnail(
            bookDir: FastFile,
            displayName: String,
            resolver: ContentResolver,
            applicationContext: Application,
            size: Pair<Int, Int>
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

        fun fromFastFileDirect(
            source: FastFile,
            resolver: ContentResolver,
            size: Pair<Int, Int> = Pair(200, 234),
            applicationContext: Application
        ): Thumbnail {
            Log.i("@@", "fromFastFileDirect Fastfile is: ${source.uri}")
            Log.i("@@", "fromFastFileDirect Size to Create is: ${size.first} by ${size.second}")
            val page = loadThumbnail(source, "0000.png", resolver, applicationContext, size)
                ?: blankBitmapFg(size)
            val bg = loadThumbnail(source, "0000-bg.png", resolver, applicationContext, size)
                ?: blankBitmapBg(size)

            Log.i("@@", "fromFastFileDirect pageSize created is: ${page.width} by ${page.height}")
            Log.i("@@", "fromFastFileDirect bgSize  created is: ${bg.width} by ${bg.height}")


            return Thumbnail(page, page, size)
        }

        fun empty(size: Pair<Int, Int> = Pair(100, 100)): Thumbnail {
            return Thumbnail(blankBitmapFg(size), blankBitmapBg(size), size)
        }
    }
}

@HiltAndroidApp
class BookListActivity : ComponentActivity() {

    private lateinit var viewModel: BookListActivityViewModel

    private var _url: Uri? = null

    private val getRootDirUrl =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            // if cancel, null coming.
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
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
    private val thumbnails = Transformations.switchMap(files) { flist ->
        liveData {
            emit(flist.map { Thumbnail.empty(size = bookSizeInt) })
            withContext(lifecycleScope.coroutineContext + Dispatchers.IO) {
                val thumbs = flist.map {
                    Thumbnail.fromFastFileDirect(
                        it,
                        bookIO.getResolver(),
                        Pair(486, 894),
                        application
                    )

                }
                thumbs.map { Log.i("@@", "Thumbs generated : ${it.toString()}") }
                withContext(Dispatchers.Main) {
                    emit(thumbs)
                }
            }
        }
    }

    private fun reloadBookList(url: Uri) {
        listFiles(url).also { flist ->
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

        val height = (metrics.heightPixels * 0.40 / metrics.density).dp
        val width = (metrics.widthPixels * 0.45 / metrics.density).dp

        Pair(width, height)
    }

    private val bookSizeInt by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // about half of 80%〜90%.

        val height = (metrics.heightPixels * 0.40).toInt()
        val width = (metrics.widthPixels * 0.45).toInt()

        Pair(width, height)
    }

    private val bookIO by lazy { BookIO(contentResolver) }

    override fun onRestart() {
        super.onRestart()

        // return from other activity, etc.
        if (true == files.value?.isNotEmpty()) {
            reloadBookList(_url!!)
        }
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        viewModel =
            ModelViewFactory(application = application).create(BookListActivityViewModel::class.java)
        return super.onCreateView(name, context, attrs)
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
        PrefManager.init(applicationContext)
        setContent {
            PngNoteTheme {
                Column {
                    val showDialog = rememberSaveable { mutableStateOf(false) }
                    TopAppBar(title = { Text("Book List") }, actions = {
                        IconButton(onClick = { showDialog.value = true }) {
                            Icon(Icons.Filled.Add, "New Book")
                        }
                        IconButton(onClick = { getRootDirUrl.launch(null) }) {
                            Icon(Icons.Filled.Settings, "Settings")
                        }
                    }, navigationIcon = {
                        Image(painterResource(id = R.mipmap.ic_launcher), null)
                    })
                    if (showDialog.value) {
                        NewBookPopup(
                            onNewBook = { addNewBook(it) },
                            onDismiss = { showDialog.value = false })
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
        } catch (_: Exception) {
            toast("Can't open dir. Please re-open.")
        }
        // UI Action
        getRootDirUrl.launch(null)
    }

    private fun addNewBook(newBookName: String) {
        val rootDir =
            _url?.let { FastFile.fromTreeUri(this, it) } ?: throw Exception("Can't open dir")
        try {
            rootDir.createDirectory(newBookName)
            openRootDir(_url!!)
        } catch (_: Exception) {
            toast("Can't create book directory ($newBookName).")
        }
    }

}


@Composable
fun NewBookPopup(onNewBook: (bookName: String) -> Unit, onDismiss: () -> Unit) {
    var textState by remember { mutableStateOf("") }
    val requester = FocusRequester()
    val buttonColors = booxTextButtonColors()
    AlertDialog(
        modifier = Modifier.border(width = 1.dp, MaterialTheme.colors.onPrimary),
        onDismissRequest = onDismiss,
        text = {
            Column {
                TextField(value = textState, onValueChange = { textState = it },
                    modifier = Modifier
                        .border(width = 1.dp, MaterialTheme.colors.onPrimary)
                        .fillMaxWidth()
                        .focusRequester(requester),
                    placeholder = { Text("New book name") })
                DisposableEffect(Unit) {
                    requester.requestFocus()
                    onDispose {}
                }
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.border(width = 1.dp, MaterialTheme.colors.onPrimary),
                colors = buttonColors, onClick = {
                    onDismiss()
                    if (textState != "") {
                        onNewBook(textState)
                    }
                }) {
                Text("CREATE")
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.border(width = 1.dp, MaterialTheme.colors.onPrimary),
                colors = buttonColors, onClick = onDismiss
            ) {
                Text("CANCEL")
                Spacer(modifier = Modifier.width(5.dp))
            }
        }

    )
}

val blankBitmap: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
    eraseColor(
        android.graphics.Color.LTGRAY
    )
}


@Composable
fun BookList(
    bookDirs: LiveData<List<FastFile>>,
    thumbnails: LiveData<List<Thumbnail>>,
    bookSize: Pair<Dp, Dp>,
    gotoBook: (dir: FastFile) -> Unit
) {
    val bookListState = bookDirs.observeAsState(emptyList())
    val thumbnailListState = thumbnails.observeAsState(emptyList())

    Column(
        modifier = Modifier
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val rowNum = (bookListState.value.size + 1) / 2
        0.until(rowNum).forEach { rowIdx ->
            TwoBook(bookListState.value, thumbnailListState.value, rowIdx * 2, bookSize, gotoBook)
        }
    }
}

@Composable
fun TwoBook(
    books: List<FastFile>,
    thumbnails: List<Thumbnail>,
    leftIdx: Int,
    bookSize: Pair<Dp, Dp>,
    gotoBook: (dir: FastFile) -> Unit
) {
    Row {
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(5.dp), border = BorderStroke(2.dp, Color(0))
        ) {
            val book = books[leftIdx]
            Book(book.name, bookSize, thumbnails[leftIdx], onOpenBook = { gotoBook(book) })
        }
        if (leftIdx + 1 < books.size) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(5.dp), border = BorderStroke(2.dp, Color(0))
            ) {
                val book = books[leftIdx + 1]
                Book(book.name, bookSize, thumbnails[leftIdx + 1], onOpenBook = { gotoBook(book) })
            }
        } else {
            Card(modifier = Modifier.weight(1f)) {}
        }
    }
}

@Composable
fun Book(bookName: String, bookSize: Pair<Dp, Dp>, thumbnail: Thumbnail, onOpenBook: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .clickable(onClick = onOpenBook)
    ) {
        ThumbnailImage(thumbnail = thumbnail, size = bookSize)
        Text(bookName, fontSize = 20.sp)
    }
}

@Composable
fun ThumbnailImage(thumbnail: Thumbnail, size: Pair<Dp, Dp>) {
    Canvas(
        modifier = Modifier
            .size(size.first, size.second)
            .padding(5.dp, 10.dp)
    ) {
        val blendMode = thumbnail.bg.let { bg ->
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

