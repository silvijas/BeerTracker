package com.beertracker

import com.beertracker.data.BeerPhotoStore
import java.io.File
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BeerPhotoStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store() = BeerPhotoStore(temporaryFolder.root)

    private fun fileAt(uri: String) = File(URI(uri))

    @Test
    fun `save writes the bytes and returns a file uri that points at them`() {
        val uri = store().save("hello".byteInputStream())
        assertTrue(uri.startsWith("file:"))
        assertEquals("hello", fileAt(uri).readText())
    }

    @Test
    fun `every save gets its own file`() {
        val store = store()
        assertNotEquals(store.save("a".byteInputStream()), store.save("b".byteInputStream()))
    }

    @Test
    fun `save leaves no partial file behind`() {
        val store = store()
        val uri = store.save("a".byteInputStream())
        val directory = fileAt(uri).parentFile!!
        assertEquals(0, directory.listFiles()!!.count { it.name.endsWith(".part") })
    }

    @Test
    fun `newPhotoFile creates an empty file inside the photo directory`() {
        val file = store().newPhotoFile()
        assertTrue(file.exists())
        assertEquals(0L, file.length())
        assertEquals("beer-photos", file.parentFile?.name)
    }

    @Test
    fun `delete removes the file and tolerates null and unknown uris`() {
        val store = store()
        val uri = store.save("a".byteInputStream())
        store.delete(uri)
        assertFalse(fileAt(uri).exists())
        store.delete(null)
        store.delete("file:///nowhere/missing.jpg")
        store.delete("not a uri at all")
    }

    @Test
    fun `deleteOrphans keeps referenced files and removes the rest`() {
        val store = store()
        val kept = store.save("keep".byteInputStream())
        val dropped = store.save("drop".byteInputStream())
        store.deleteOrphans(setOf(kept))
        assertTrue(fileAt(kept).exists())
        assertFalse(fileAt(dropped).exists())
    }

    @Test
    fun `deleteOrphans removes an abandoned camera file`() {
        val store = store()
        val abandoned = store.newPhotoFile()
        store.deleteOrphans(emptySet())
        assertFalse(abandoned.exists())
    }

    @Test
    fun `deleteOrphans on an empty store does nothing and does not throw`() {
        store().deleteOrphans(emptySet())
    }
}
