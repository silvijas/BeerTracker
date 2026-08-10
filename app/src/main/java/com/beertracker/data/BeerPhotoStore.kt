package com.beertracker.data

import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.UUID

/**
 * Owns the photos the user attaches to their beers. Files live in app
 * private storage, so no storage permission is needed and the media scanner
 * never sees them, and they are referenced by `file://` URI in
 * `TriedBeer.photoUri`.
 *
 * Superseded files are NOT deleted eagerly when a photo is replaced or
 * removed on the form: the user can still abandon that edit, and the saved
 * row would then point at a file already deleted. Reclamation is
 * [deleteOrphans], run at app start, which only removes files no row
 * references. Deleting a beer is different and does delete eagerly, because
 * the row is definitively gone by then.
 */
class BeerPhotoStore(root: File) {

    private val directory = File(root, DIRECTORY)

    /**
     * An empty file for the camera to write into. Created up front because
     * ActivityResultContracts.TakePicture needs a destination URI before the
     * picture is taken.
     */
    fun newPhotoFile(): File {
        directory.mkdirs()
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        file.createNewFile()
        return file
    }

    fun uriFor(file: File): String = file.toURI().toString()

    /**
     * Copies a picked photo in, so the user deleting the original from their
     * gallery can never blank the beer. Written under a temporary name and
     * renamed on success, so a failed copy leaves no half file behind.
     */
    fun save(input: InputStream): String {
        directory.mkdirs()
        val target = File(directory, "${UUID.randomUUID()}.jpg")
        val partial = File(directory, "${target.name}.part")
        try {
            input.use { source -> partial.outputStream().use { source.copyTo(it) } }
            partial.renameTo(target)
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
        return uriFor(target)
    }

    fun delete(uri: String?) {
        fileOf(uri)?.delete()
    }

    /** Removes every photo file that no saved beer points at. */
    fun deleteOrphans(referenced: Set<String>) {
        val keep = referenced.mapNotNull { fileOf(it)?.canonicalPath }.toSet()
        directory.listFiles()?.forEach { file ->
            if (file.canonicalPath !in keep) file.delete()
        }
    }

    /** A URI that is null, malformed, or not a file URI simply has no file. */
    private fun fileOf(uri: String?): File? = try {
        uri?.let { File(URI(it)) }
    } catch (error: IllegalArgumentException) {
        null
    } catch (error: java.net.URISyntaxException) {
        null
    }

    private companion object {
        const val DIRECTORY = "beer-photos"
    }
}
