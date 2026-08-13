package com.tenmilelabs.chefai.recipes.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the pure subsampling arithmetic behind [RecipeImageStore.writeFromUri]. Decoding itself
 * needs a real `ImageDecoder`, so it is exercised in `RecipeImageStoreTest` (androidTest).
 */
class RecipeImageStoreSampleSizeTest {

    @Test
    fun `image already within the target edge is not subsampled`() {
        assertEquals(1, sampleSizeFor(width = 1600, height = 1200))
    }

    @Test
    fun `image exactly at the target edge is not subsampled`() {
        assertEquals(1, sampleSizeFor(width = 2048, height = 2048))
    }

    @Test
    fun `12MP camera photo is subsampled to fit`() {
        // 4032x3024 / 2 = 2016x1512, under the 2048 target.
        assertEquals(2, sampleSizeFor(width = 4032, height = 3024))
    }

    @Test
    fun `subsampling keys off the longest edge, not the width`() {
        assertEquals(2, sampleSizeFor(width = 3024, height = 4032))
    }

    @Test
    fun `very large image steps down by successive powers of two`() {
        // 8000 / 4 = 2000, under the target; /2 would leave 4000.
        assertEquals(4, sampleSizeFor(width = 8000, height = 6000))
    }
}
