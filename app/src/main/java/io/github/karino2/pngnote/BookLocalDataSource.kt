package io.github.karino2.pngnote

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import io.github.karino2.pngnote.data.preferences.PrefManager
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import java.io.FileDescriptor
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookLocalDataSource (
    private val ioDispatcher : CoroutineDispatcher,
    private val resolver: ContentResolver
) : BookRepositoryStorage
{

    @Inject
    lateinit var prefManager : PrefManager

    /**
     * This implementation assumes the existence of a Singleton PrefManager
     * which is OK, I guess, especially when moving to dependency injection,
     * which would help testing this function, too
     *
     * Boolean probably not right, when there could also be an issue with
     * storage permissions (for example)
     *
     */
    override fun authorizeAccess(): Outcome<Any> {
        prefManager.getUri()?.let {
            return if (convertUriToFileObject(uri = it, file = "caniwritehere.tst").canWrite()) {
                Outcome.Success(200)
            } else {
                Outcome.NotAccessible(401)
            }
        } ?: return Outcome.NoAuthDataReceived(400)
    }

    private val pageNamePat = "([0-9][0-9][0-9][0-9])".toRegex()
    private val pageNameExt = ".png"
    private val pageNameBgExt = "-bg"
    private val pageNameDefaultBg = "background.png"


    /**
     * Converts an Uri in to a file object
     *
     * @param uri assumes that the uri is a path object
     * @param file if given, it appends it into the object,
     * if not, it basically describes a path on the file system
     *
     */
    private fun convertUriToFileObject(uri: Uri, file: String?) : File {
        val appendPath = if(file == null) { "" } else { "/$file" }
        return File(URI(uri.scheme.toString(), uri.userInfo, uri.host, uri.port, appendPath, "", ""))
    }

    /**
     * Creates a file for a given directory path and page. it does not check it's availibity
     *
     * @param uri path based uri
     * @param page zero based page number
     *  page zero is considered the cover
     *  page negative refers to the default background file as defined in [pageNameDefaultBg]
     * @param background indicates if the background for that page needs to be retrieved by
     *  appending [pageNameBgExt]
     *
     */
    private fun composeAccess(uri: Uri, page: Int, background: Boolean): File {
        val fileName =
                if (page < 0 ) { pageNameDefaultBg } else { "" } +
                page.toString().format(pageNamePat) +
                if(background){pageNameBgExt} else{""} + pageNameExt
        return convertUriToFileObject(uri, fileName)
    }

    private fun getBitmapFromUri(uri: Uri): Outcome<Bitmap> {
        val parcelFileDescriptor =
            resolver.openFileDescriptor(uri, "r") ?: return Outcome.NotFound(null) // Null means file not found

        val fileDescriptor: FileDescriptor? = parcelFileDescriptor.fileDescriptor
        val image: Bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor.close()

        return Outcome.Success(image)
    }

    /**
     *
     * Loads the foreground and the background full-size
     *
     *   Three options for background (higher preference first)
     *   - The page number + '-bg'
     *   - 0000 + '-bg'
     *   - a file called background.
     *
     *   Returns [Outcome.NotFound] when no option found so that the viewmodel can handle it
     *
     */
    private fun getPage(uri: Uri, page: Int) : ThumbnailNewStyle {
        var background : File? = composeAccess(uri, page, true)
        background?.let {
            if (!it.exists()) {
                background = composeAccess(uri, 0, true)
                if (!it.exists()) {
                    background = composeAccess(uri, -1, false)
                    if (!it.exists()) {
                        background = null
                    }
                }
            }
        }
        var foreground: File? = composeAccess(uri, 0, false)
        foreground?.let {
            if (!it.exists()) {
                foreground = null
            }
        }

        val fgBitmap: Outcome<Bitmap> = foreground?.let {
            getBitmapFromUri(it.toUri())
        } ?: Outcome.NotFound(null)

        val bgBitmap: Outcome<Bitmap> = background?.let {
            getBitmapFromUri(it.toUri())
        } ?: Outcome.NotFound(null)

        // I think the only outcomes are [Outcome.Success] or [Outcome.NotFound]
        // doesn't need extra actions or state , it will be replaced
        // by a placeholder in the viewmodel

        return ThumbnailNewStyle(foreground = fgBitmap, background = bgBitmap )
    }

    /**
     * Retrieves all books at the last opened location
     * Structure is:
     * - Book = Directory
     * - Cover = ####.png
     * - Template = ####-bg.png
     */
    override fun getListOfBooks(): List<BooksAsStored> {
        val directory = prefManager.getUri()?.path
        val result = mutableListOf<BooksAsStored>()

        directory?.let {
            File(directory).walk().forEach {
                 if (it.isDirectory) {
                    result.add(
                        BooksAsStored(
                            uri = it.toUri(),
                            thumbnail = getPage(it.toUri(), 0),
                            name = it.name
                        )
                    )
                 }
            }
        }
        return result
    }
}

