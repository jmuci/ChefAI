package com.tenmilelabs.chefai.core.di

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageModelPolicyTest {

    private val filesDir = "/data/user/0/com.tenmilelabs.chefai/files"

    private fun allowed(data: Any?, allowCleartext: Boolean = false) =
        isAllowedImageModel(data, filesDir, allowCleartext)

    @Test
    fun `https urls and the app's own files load`() {
        assertThat(allowed("https://cdn.example/cake.jpg")).isTrue()
        assertThat(allowed("file://$filesDir/recipe_images/abc")).isTrue()
        assertThat(allowed("$filesDir/saved_plan_images/abc")).isTrue()
    }

    @Test
    fun `server-supplied local schemes are blocked`() {
        assertThat(allowed("content://com.android.contacts/photo/1")).isFalse()
        assertThat(allowed("android.resource://com.tenmilelabs.chefai/drawable/x")).isFalse()
        assertThat(allowed("file:///sdcard/Download/x.png")).isFalse()
        assertThat(allowed("file:///data/user/0/com.tenmilelabs.chefai/databases/ChefAI.db")).isFalse()
    }

    @Test
    fun `a path can't climb out of the files directory`() {
        assertThat(allowed("file://$filesDir/../databases/ChefAI.db")).isFalse()
        assertThat(allowed("$filesDir/recipe_images/../../shared_prefs/x.xml")).isFalse()
    }

    @Test
    fun `a sibling directory sharing the prefix is not the files directory`() {
        assertThat(allowed("file://${filesDir}_evil/x.png")).isFalse()
    }

    @Test
    fun `cleartext is allowed only when asked for`() {
        assertThat(allowed("http://10.0.2.2:8080/img")).isFalse()
        assertThat(allowed("http://10.0.2.2:8080/img", allowCleartext = true)).isTrue()
    }

    @Test
    fun `non-string models built by the app pass`() {
        assertThat(allowed(java.io.File("/anything"))).isTrue()
    }
}
