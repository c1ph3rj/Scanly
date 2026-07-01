package `in`.c1ph3rj.scanly.data.library

import android.net.Uri
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryCatalog
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryMarker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySession @Inject constructor() {
    @Volatile private var treeUri: Uri? = null
    @Volatile private var marker: LibraryMarker? = null
    @Volatile private var catalog: LibraryCatalog? = null

    fun connect(treeUri: Uri, marker: LibraryMarker, catalog: LibraryCatalog) {
        this.treeUri = treeUri
        this.marker = marker
        this.catalog = catalog
    }

    fun disconnect() {
        treeUri = null
        marker = null
        catalog = null
    }

    fun requireTreeUri(): Uri = treeUri ?: error("Scanly library is not connected.")
    fun requireMarker(): LibraryMarker = marker ?: error("Scanly library is not connected.")
    fun requireCatalog(): LibraryCatalog = catalog ?: error("Scanly library is not connected.")
    fun updateCatalog(catalog: LibraryCatalog) { this.catalog = catalog }
}

