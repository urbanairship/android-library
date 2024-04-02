package com.urbanairship.automation.rewrite.inappmessage.assets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DefaultAssetFileManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testRoot = "test-root"
    private val manager = DefaultAssetFileManager(context, testRoot)

    @After
    public fun tearDown() {
        val root = File(manager.getRootDirectory())
        if (root.exists()) {
            root.delete()
        }
    }

    @Test
    public fun testEnsureCacheRootDirectory() {
        var uri = manager.getRootDirectory()
        assertTrue(uri.path.endsWith("$testRoot/"))
        var file = File(uri.path)
        assertTrue(file.exists())

        file.delete()
        uri = manager.getRootDirectory()
        assertTrue(uri.path.endsWith("$testRoot/"))
        file = File(uri.path)
        assertTrue(file.exists())
    }

    @Test
    public fun testClearAssetsSuccess() {
        val identifier = "test-id"
        val file = File(manager.ensureCacheDirectory(identifier))
        assertTrue(file.exists())

        manager.clearAssets(identifier)
        assertFalse(file.exists())
    }

    @Test
    public fun testMoveAssetSuccess() {
        val identifier = "test-id"
        val dir = File(manager.ensureCacheDirectory(identifier))

        val fromFile = File(dir, "test.txt")
        fromFile.writeBytes("test".toByteArray())

        assertTrue(fromFile.exists())

        val toFile = File(dir, "to-file.txt")
        assertFalse(toFile.exists())

        manager.moveAsset(fromFile.toURI(), toFile.toURI())

        assertFalse(fromFile.exists())
        assertTrue(toFile.exists())
    }
}
