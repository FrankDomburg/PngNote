package io.github.karino2.pngnote

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import io.github.karino2.pngnote.utils.FastFile
import java.util.*



class BookList(val dir: FastFile, val resolver: ContentResolver) {

}


class Book(val bookDir: FastFile, val pages: List<FastFile>, val bgImage: FastFile?) {
    fun addPage() : Book {
        // page name start form 0!
        val pngFile = BookPage.createEmptyFile(bookDir, pages.size)
        return Book(bookDir, pages + pngFile, bgImage)
    }

    fun getPage(idx: Int) = BookPage(pages[idx], idx)

    // Assign dummy size so that file.isEmpty becomes false.
    // We doesn't care actual size, just check whether it's empty or not.
    // So assign non-zero value is enough for our purpose.
    fun assignNonEmpty(pageIdx: Int): Book {
        if(!getPage(pageIdx).file.isEmpty)
            return this

        return  pages.mapIndexed { idx, file ->
            if(idx != pageIdx)
                file
            else
                file.copy(size=1000)
        }.toList().let { Book(bookDir, it, bgImage) }
    }

    val name : String
        get() = bookDir.name
}


data class BookPage(val file: FastFile, val idx: Int) {
    companion object {
        private fun newPageName(pageIdx: Int) : String {
            return "%04d.png".format(pageIdx)
        }

        fun createEmptyFile(bookDir: FastFile, idx: Int) : FastFile {
            val fileName = newPageName(idx)
            return bookDir.createFile("image/png", fileName) ?: throw Exception("Can't create file $fileName")
        }
    }
}

class BookIO(private val resolver: ContentResolver) {
    /**
     *
     * TODO Convenience method to keep the refactoring of Thumbnails smaller - disappears when making data layer?
     *
     */
    fun getResolver():ContentResolver{return resolver}

    private fun loadBitmap(file: FastFile) : Bitmap {
        return resolver.openFileDescriptor(file.uri, "r").use {
            BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor)
        }
    }

    private fun loadThumbnail(
        bookDir: FastFile,
        displayName: String
    ): Bitmap? {
        return bookDir.findFile(displayName)?.let { loadBitmapThumbnail(it, 3) }
    }

    fun loadThumbnail(bookDir: FastFile) : Bitmap? {
        return loadThumbnail(bookDir, "0000.png")
    }

    fun loadBgThumbnail(bookDir: FastFile) : Bitmap? {
        return loadThumbnail(bookDir, "background.png")
    }


    fun loadPageThumbnail(file: FastFile) = loadBitmapThumbnail(file, 4)
    fun loadBgForGrid(bookDir: FastFile) = bookDir.findFile("0000-bg.png")?.let { loadBitmapThumbnail(it, 4) }

    private fun loadBitmapThumbnail(file: FastFile, sampleSize: Int) :Bitmap {
        return resolver.openFileDescriptor(file.uri, "r").use {
            val option = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor, null, option)
        }
    }

    private fun isEmpty(file: FastFile) = file.isEmpty

    fun isPageEmpty(page: BookPage) = isEmpty(page.file)
    fun loadBitmap(page: BookPage) = loadBitmap(page.file)

    fun loadBitmapOrNull(page: BookPage) = if(isPageEmpty(page)) null else loadBitmap(page)

    fun loadBgOrNull(book: Book) = book.bgImage?.let { loadBitmap(it) }

    fun saveBitmap(page: BookPage, bitmap: Bitmap) {
        resolver.openOutputStream(page.file.uri, "wt").use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, it)
        }
    }

    // ex. 0009.png
    private val pageNamePat = "([0-9][0-9][0-9][0-9])\\.png".toRegex()

    fun loadBook(bookDir: FastFile) : Book {
        val pageMap = bookDir.listFiles()
            .filter {file ->
                pageNamePat.matches(file.name)
            }.map {file ->
                val res = pageNamePat.find(file.name)!!
                val pageIdx = res.groupValues[1].toInt()
                Pair(pageIdx, file)
            }.toMap()
        val lastPageIdx = if(pageMap.isEmpty()) 0 else pageMap.maxOf { it.key }
        val pages = (0 .. lastPageIdx).map {
            pageMap[it] ?: BookPage.createEmptyFile(bookDir, it)
        }
        val bgFile = bookDir.findFile("0000-bg.png")
        return Book(bookDir, pages, bgFile)
    }


}