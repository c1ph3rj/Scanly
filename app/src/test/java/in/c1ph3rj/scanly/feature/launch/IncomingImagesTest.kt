package `in`.c1ph3rj.scanly.feature.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingImagesTest {

    private val ownPackage = "in.c1ph3rj.scanly"

    @Test
    fun isImageShare_acceptsSingleAndMultipleImageShares() {
        assertTrue(IncomingImages.isImageShare("android.intent.action.SEND", "image/jpeg"))
        assertTrue(IncomingImages.isImageShare("android.intent.action.SEND_MULTIPLE", "image/*"))
        assertTrue(IncomingImages.isImageShare("android.intent.action.SEND", "IMAGE/PNG"))
    }

    @Test
    fun isImageShare_rejectsOtherActionsAndTypes() {
        assertFalse(IncomingImages.isImageShare("android.intent.action.VIEW", "image/jpeg"))
        assertFalse(IncomingImages.isImageShare("android.intent.action.SEND", "application/pdf"))
        assertFalse(IncomingImages.isImageShare("android.intent.action.SEND_MULTIPLE", "*/*"))
        assertFalse(IncomingImages.isImageShare("android.intent.action.SEND", null))
        assertFalse(IncomingImages.isImageShare(null, "image/jpeg"))
    }

    @Test
    fun isAcceptedSource_acceptsOtherAppsContentUris() {
        assertTrue(IncomingImages.isAcceptedSource("content", "com.google.android.apps.photos.contentprovider", ownPackage))
        assertTrue(IncomingImages.isAcceptedSource("content", "media", ownPackage))
        assertTrue(IncomingImages.isAcceptedSource("CONTENT", "com.whatsapp.provider.media", ownPackage))
    }

    @Test
    fun isAcceptedSource_rejectsFileUrisAndOwnProviders() {
        assertFalse(IncomingImages.isAcceptedSource("file", null, ownPackage))
        assertFalse(IncomingImages.isAcceptedSource("file", "", ownPackage))
        assertFalse(IncomingImages.isAcceptedSource("content", null, ownPackage))
        assertFalse(IncomingImages.isAcceptedSource("content", "in.c1ph3rj.scanly.fileprovider", ownPackage))
        assertFalse(IncomingImages.isAcceptedSource("content", "in.c1ph3rj.scanly", ownPackage))
        // Cross-profile form of our own provider.
        assertFalse(IncomingImages.isAcceptedSource("content", "10@in.c1ph3rj.scanly.fileprovider", ownPackage))
    }

    @Test
    fun isAcceptedSource_doesNotConfusePackagesThatShareAPrefix() {
        assertTrue(IncomingImages.isAcceptedSource("content", "in.c1ph3rj.scanlyclone.provider", ownPackage))
    }
}
