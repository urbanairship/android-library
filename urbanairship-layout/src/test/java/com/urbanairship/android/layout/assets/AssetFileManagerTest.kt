/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.assets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AssetFileManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val rootFolderName = "test-asset-cache"
    private val root = File(context.cacheDir, rootFolderName)
    private val fileManager = DefaultAssetFileManager(context, rootFolderName)

    @Test
    public fun testEnsureCacheDirectory_createsSubDirectoryUnderRoot() {
        val dir = fileManager.ensureCacheDirectory("schedule-id")

        assertEquals(File(root, "schedule-id").canonicalFile, dir.canonicalFile)
        assertTrue(dir.exists())
    }

    @Test
    public fun testEnsureCacheDirectory_allowsIdentifierWithDotsThatStaysUnderRoot() {
        val dir = fileManager.ensureCacheDirectory("schedule..id")

        assertEquals(File(root, "schedule..id").canonicalFile, dir.canonicalFile)
        assertTrue(dir.exists())
    }

    @Test
    public fun testEnsureCacheDirectory_rejectsIdentifierThatTraversesAboveRoot() {
        try {
            fileManager.ensureCacheDirectory("../../etc")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Invalid cache identifier"))
        }

        assertFalse(File(context.cacheDir, "etc").exists())
    }

    @Test
    public fun testEnsureCacheDirectory_rejectsIdentifierThatTraversesToSibling() {
        val other = File(root, "sibling")
        other.mkdirs()

        try {
            fileManager.ensureCacheDirectory("../sibling")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Invalid cache identifier"))
        }
    }

    @Test
    public fun testEnsureCacheDirectory_rejectsEmptyIdentifier() {
        try {
            fileManager.ensureCacheDirectory("")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Invalid cache identifier"))
        }
    }

    @Test
    public fun testClearAssets_deletesSubDirectoryUnderRoot() {
        val dir = fileManager.ensureCacheDirectory("schedule-id")
        val file = File(dir, "asset.txt")
        file.writeText("asset contents")
        assertTrue(file.exists())

        fileManager.clearAssets("schedule-id")

        assertFalse(dir.exists())
    }

    @Test
    public fun testClearAssets_rejectsIdentifierThatTraversesAboveRoot() {
        val victimDir = File(context.cacheDir, "databases")
        victimDir.mkdirs()
        val victim = File(victimDir, "airship.db")
        victim.writeText("pretend this is the automation database")
        assertTrue(victim.exists())

        try {
            fileManager.clearAssets("../databases")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Invalid cache identifier"))
        }

        assertTrue(victim.exists())
    }

    @Test
    public fun testClearAssets_rejectsEmptyIdentifier() {
        fileManager.ensureCacheDirectory("schedule-id")
        assertTrue(root.exists())

        try {
            fileManager.clearAssets("")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Invalid cache identifier"))
        }

        assertTrue(root.exists())
    }
}
