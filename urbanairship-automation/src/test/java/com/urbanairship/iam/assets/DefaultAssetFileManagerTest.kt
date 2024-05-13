package com.urbanairship.iam.assets

import android.content.Context
import androidx.core.net.toFile
import androidx.core.net.toUri
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
        val root = manager.getRootDirectory().toFile()
        if (root.exists()) {
            root.delete()
        }
    }

    @Test
    public fun testEnsureCacheRootDirectory() {
        var uri = manager.getRootDirectory()
        val rootDirectory = uri.toFile()

        println("uri path: ${uri.path}")
        assertTrue(uri.path?.endsWith(testRoot) == true)
        assertTrue(rootDirectory.exists())

        assertTrue(rootDirectory.delete())
        assertFalse(rootDirectory.exists())

        uri = manager.getRootDirectory()

        assertTrue(uri.path?.endsWith(testRoot) == true)
        assertTrue(rootDirectory.exists())
    }

    @Test
    public fun testClearAssetsSuccess() {
        val identifier = "test-id"
        val file = manager.ensureCacheDirectory(identifier)
        assertTrue(file.exists())

        manager.clearAssets(identifier)
        assertFalse(file.exists())
    }

    @Test
    public fun testMoveAssetSuccess() {
        val identifier = "test-id"
        val dir = manager.ensureCacheDirectory(identifier)

        val fromFile = File(dir, "test.txt")
        fromFile.writeBytes("test".toByteArray())

        assertTrue(fromFile.exists())

        val toFile = File(dir, "to-file.txt")
        assertFalse(toFile.exists())

        manager.moveAsset(fromFile.toUri(), toFile.toUri())

        assertFalse(fromFile.exists())
        assertTrue(toFile.exists())

        assertTrue(manager.assetItemExists(toFile.toUri()))
    }
}
