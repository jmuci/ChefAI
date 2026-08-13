package com.tenmilelabs.chefai.recipes.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class RecipeImageStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = RecipeImageStore(context, Dispatchers.IO)
    private val scratch = File(context.cacheDir, "image-store-test").apply { mkdirs() }

    @After
    fun tearDown() = runTest {
        store.deleteAll()
        scratch.deleteRecursively()
    }

    /** Writes a solid-colour JPEG of the given size into the scratch dir and returns its file uri. */
    private fun writeJpeg(name: String, width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF3366CC.toInt())
        }
        val file = File(scratch, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun decodeStored(path: String): Bitmap =
        requireNotNull(BitmapFactory.decodeFile(path)) { "stored file at $path should decode" }

    @Test
    fun writeFromUri_storesImageAtTheRecipeKeyedPath() = runTest {
        val recipeId = UUID.randomUUID()

        val path = store.writeFromUri(recipeId, writeJpeg("small.jpg", 800, 600).toString())

        assertNotNull("a path should be returned", path)
        val stored = File(requireNotNull(path))
        assertTrue("file should exist on disk", stored.exists())
        assertEquals(recipeId.toString(), stored.name)
        assertTrue("no leftover .tmp", File(stored.parentFile, "$recipeId.tmp").exists().not())
    }

    @Test
    fun writeFromUri_leavesAnAlreadySmallImageAtItsOriginalSize() = runTest {
        val path = requireNotNull(
            store.writeFromUri(UUID.randomUUID(), writeJpeg("small.jpg", 800, 600).toString())
        )

        val decoded = decodeStored(path)
        assertEquals(800, decoded.width)
        assertEquals(600, decoded.height)
    }

    @Test
    fun writeFromUri_downscalesAnOversizedPhotoBelowTheTargetEdge() = runTest {
        // Stand-in for a camera original: well past the 2048px target on the long edge.
        val path = requireNotNull(
            store.writeFromUri(UUID.randomUUID(), writeJpeg("big.jpg", 4032, 3024).toString())
        )

        val decoded = decodeStored(path)
        assertTrue(
            "longest edge ${max(decoded.width, decoded.height)} should be within the 2048 target",
            max(decoded.width, decoded.height) <= 2048
        )
        assertEquals(
            "aspect ratio should be preserved",
            4f / 3f,
            decoded.width.toFloat() / decoded.height.toFloat(),
            0.01f
        )
    }

    @Test
    fun writeFromUri_returnsNullForAUriThatIsNotAnImage() = runTest {
        val notAnImage = File(scratch, "notes.txt").apply { writeText("definitely not a jpeg") }

        val path = store.writeFromUri(UUID.randomUUID(), Uri.fromFile(notAnImage).toString())

        assertNull(path)
    }

    @Test
    fun writeFromUri_returnsNullForAMissingFile() = runTest {
        val missing = Uri.fromFile(File(scratch, "gone.jpg"))

        assertNull(store.writeFromUri(UUID.randomUUID(), missing.toString()))
    }

    @Test
    fun writeFromUri_replacesAPreviousImageForTheSameRecipe() = runTest {
        val recipeId = UUID.randomUUID()
        store.writeFromUri(recipeId, writeJpeg("first.jpg", 800, 600).toString())

        val path = requireNotNull(
            store.writeFromUri(recipeId, writeJpeg("second.jpg", 400, 400).toString())
        )

        val decoded = decodeStored(path)
        assertEquals(400, decoded.width)
        assertEquals(400, decoded.height)
    }

    @Test
    fun delete_removesTheStoredImage() = runTest {
        val recipeId = UUID.randomUUID()
        val path = requireNotNull(
            store.writeFromUri(recipeId, writeJpeg("small.jpg", 800, 600).toString())
        )

        store.delete(recipeId)

        assertTrue("file should be gone", File(path).exists().not())
    }
}
