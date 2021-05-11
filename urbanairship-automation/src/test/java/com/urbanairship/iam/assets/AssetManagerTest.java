/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Callable;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AssetManager}.
 */
@RunWith(AndroidJUnit4.class)
public class AssetManagerTest {

    private AssetManager assetManager;
    private AssetCache mockCache;
    private PrepareAssetsDelegate mockAssetsDelegate;
    private CachePolicyDelegate mockCachePolicyDelegate;

    private InAppMessage MESSAGE = InAppMessage.newBuilder()
                                               .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                               .build();

    @Before
    public void setup() {
        mockCache = mock(AssetCache.class);
        mockAssetsDelegate = mock(PrepareAssetsDelegate.class);
        mockCachePolicyDelegate = mock(CachePolicyDelegate.class);
        assetManager = new AssetManager(mockCache);
        assetManager.setPrepareAssetDelegate(mockAssetsDelegate);
        assetManager.setCachePolicyDelegate(mockCachePolicyDelegate);
    }

    /**
     * Test preparing assets when a new message is scheduled.
     */
    @Test
    public void testSchedule() {
        final InAppMessage extendedMessage = InAppMessage.newBuilder(MESSAGE)
                                                         .addAction("cool", JsonValue.wrap("story"))
                                                         .build();

        Assets mockAssets = mock(Assets.class);
        when(mockCache.getAssets("some-id")).thenReturn(mockAssets);
        when(mockCachePolicyDelegate.shouldCacheOnSchedule("some-id", extendedMessage)).thenReturn(true);

        assetManager.onSchedule("some-id", new Callable<InAppMessage>() {
            @Override
            public InAppMessage call() {
                return extendedMessage;
            }
        });

        verify(mockAssetsDelegate).onSchedule("some-id", extendedMessage, mockAssets);
    }

    /**
     * Test skipping preparing assets when a new message is scheduled.
     */
    @Test
    public void testSkipSchedule() {
        Assets mockAssets = mock(Assets.class);
        when(mockCache.getAssets("some-id")).thenReturn(mockAssets);
        when(mockCachePolicyDelegate.shouldCacheOnSchedule("some-id", MESSAGE)).thenReturn(false);

        assetManager.onSchedule("Some-id", new Callable<InAppMessage>() {
            @Override
            public InAppMessage call() {
                return MESSAGE;
            }
        });

        verifyZeroInteractions(mockAssetsDelegate);
    }

    /**
     * Test preparing assets when a message is being prepared for display.
     */
    @Test
    public void testPrepare() {
        Assets mockAssets = mock(Assets.class);
        when(mockCache.getAssets("some-id")).thenReturn(mockAssets);
        when(mockAssetsDelegate.onPrepare("some-id", MESSAGE, mockAssets)).thenReturn(AssetManager.PREPARE_RESULT_RETRY);

        int result = assetManager.onPrepare("some-id", MESSAGE);
        assertEquals(AssetManager.PREPARE_RESULT_RETRY, result);
        verify(mockAssetsDelegate).onPrepare("some-id", MESSAGE, mockAssets);
    }

    /**
     * Test clearing assets after the message is finished displaying.
     */
    @Test
    public void testClearAssetsDisplayFinished() {
        when(mockCachePolicyDelegate.shouldPersistCacheAfterDisplay("some-id", MESSAGE)).thenReturn(false);
        assetManager.onDisplayFinished("some-id", MESSAGE);
        verify(mockCache).releaseAssets("some-id", true);
    }

    /**
     * Test keeping assets after the message is finished displaying.
     */
    @Test
    public void testPersistAfterDisplay() {
        when(mockCachePolicyDelegate.shouldPersistCacheAfterDisplay("some-id", MESSAGE)).thenReturn(true);
        assetManager.onDisplayFinished("some-id", MESSAGE);
        verifyZeroInteractions(mockAssetsDelegate);
        verify(mockCache).releaseAssets("some-id", false);
    }

    /**
     * Test clearing assets after the schedule is complete.
     */
    @Test
    public void testClearOnScheduleFinished() {
        assetManager.onFinish("some-id");
        verify(mockCache).releaseAssets("some-id", true);
    }

}
