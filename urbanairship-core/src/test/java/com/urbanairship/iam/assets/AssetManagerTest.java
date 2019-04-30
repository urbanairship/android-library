/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import com.urbanairship.BaseTestCase;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageSchedule;
import com.urbanairship.iam.InAppMessageScheduleInfo;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AssetManager}.
 */
public class AssetManagerTest extends BaseTestCase {

    private AssetManager assetManager;
    private AssetCache mockCache;
    private PrepareAssetsDelegate mockAssetsDelegate;
    private CachePolicyDelegate mockCachePolicyDelegate;

    private InAppMessageSchedule schedule;

    @Before
    public void setup() {
        mockCache = mock(AssetCache.class);
        mockAssetsDelegate = mock(PrepareAssetsDelegate.class);
        mockCachePolicyDelegate = mock(CachePolicyDelegate.class);
        assetManager = new AssetManager(mockCache);
        assetManager.setPrepareAssetDelegate(mockAssetsDelegate);
        assetManager.setCachePolicyDelegate(mockCachePolicyDelegate);

        schedule = new InAppMessageSchedule("some-id", JsonMap.EMPTY_MAP, InAppMessageScheduleInfo.newBuilder()
                                                                                                  .addTrigger(Triggers.newActiveSessionTriggerBuilder().build())
                                                                                                  .setMessage(InAppMessage.newBuilder()
                                                                                                                          .setId("some-message-id")
                                                                                                                          .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                                                          .build())
                                                                                                  .build());
    }

    /**
     * Test preparing assets when a new message is scheduled.
     */
    @Test
    public void testSchedule() {
        final InAppMessage extendedMessage = InAppMessage.newBuilder(schedule.getInfo().getInAppMessage())
                                                         .addAction("cool", JsonValue.wrap("story"))
                                                         .build();

        Assets mockAssets = mock(Assets.class);
        when(mockCache.getAssets("some-id")).thenReturn(mockAssets);
        when(mockCachePolicyDelegate.shouldCacheOnSchedule(schedule)).thenReturn(true);

        assetManager.onSchedule(schedule, new Callable<InAppMessage>() {
            @Override
            public InAppMessage call() throws Exception {
                return extendedMessage;
            }
        });

        verify(mockAssetsDelegate).onSchedule(schedule, extendedMessage, mockAssets);
    }

    /**
     * Test skipping preparing assets when a new message is scheduled.
     */
    @Test
    public void testSkipSchedule() {
        Assets mockAssets = mock(Assets.class);
        when(mockCache.getAssets("some-id")).thenReturn(mockAssets);
        when(mockCachePolicyDelegate.shouldCacheOnSchedule(schedule)).thenReturn(false);

        assetManager.onSchedule(schedule, new Callable<InAppMessage>() {
            @Override
            public InAppMessage call() throws Exception {
                return schedule.getInfo().getInAppMessage();
            }
        });

        verifyZeroInteractions(mockAssetsDelegate);
    }

    /**
     * Test preparing assets when a message is being prepared for display.
     */
    @Test
    public void testPrepare() {
        final InAppMessage extendedMessage = InAppMessage.newBuilder(schedule.getInfo().getInAppMessage())
                                                         .addAction("cool", JsonValue.wrap("story"))
                                                         .build();

        Assets mockAssets = mock(Assets.class);
        when(mockCache.getAssets("some-id")).thenReturn(mockAssets);
        when(mockAssetsDelegate.onPrepare(schedule, schedule.getInfo().getInAppMessage(), mockAssets)).thenReturn(AssetManager.PREPARE_RESULT_RETRY);

        int result = assetManager.onPrepare(schedule, schedule.getInfo().getInAppMessage());
        assertEquals(AssetManager.PREPARE_RESULT_RETRY, result);
        verify(mockAssetsDelegate).onPrepare(schedule, extendedMessage, mockAssets);
    }

    /**
     * Test clearing assets after the message is finished displaying.
     */
    @Test
    public void testClearAssetsDisplayFinished() {
        when(mockCachePolicyDelegate.shouldPersistCacheAfterDisplay(schedule)).thenReturn(false);
        assetManager.onDisplayFinished(schedule);
        verify(mockCache).releaseAssets(schedule.getId(), true);
    }

    /**
     * Test keeping assets after the message is finished displaying.
     */
    @Test
    public void testPersistAfterDisplay() {
        when(mockCachePolicyDelegate.shouldPersistCacheAfterDisplay(schedule)).thenReturn(true);
        assetManager.onDisplayFinished(schedule);
        verifyZeroInteractions(mockAssetsDelegate);
        verify(mockCache).releaseAssets(schedule.getId(), false);
    }

    /**
     * Test clearing assets after the schedule is complete.
     */
    @Test
    public void testClearOnScheduleFinished() {
        assetManager.onScheduleFinished(schedule);
        verify(mockCache).releaseAssets(schedule.getId(), true);
    }

}