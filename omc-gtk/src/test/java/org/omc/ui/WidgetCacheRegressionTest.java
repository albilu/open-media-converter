package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.gnome.gtk.ColumnView;
import org.gnome.gtk.Label;
import org.gnome.gtk.ProgressBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omc.controller.ApplicationWorkflowController;

/**
 * Regression tests for the file-list widget cache leak (P2 audit slice).
 *
 * <p>
 * Recycled rows re-bind to other files while stale cache entries still point
 * at those widgets, so unbind must evict by widget identity - never blindly.
 */
@ExtendWith(MockitoExtension.class)
class WidgetCacheRegressionTest {

    @Mock
    private ColumnView columnView;

    @Mock
    private ApplicationWorkflowController controller;

    private FileListView fileListView;
    private Map<String, Object> cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        var mockColumns = mock(org.gnome.gio.ListModel.class);
        when(mockColumns.getNItems()).thenReturn(0);
        when(columnView.getColumns()).thenReturn(mockColumns);
        fileListView = new FileListView(columnView, controller);

        Field cacheField = FileListView.class.getDeclaredField("progressWidgetCache");
        cacheField.setAccessible(true);
        cache = (Map<String, Object>) cacheField.get(fileListView);
    }

    private Object widgets(ProgressBar bar, Label status, Label label) throws Exception {
        Class<?> type = Class.forName("org.omc.ui.FileListView$ProgressWidgets");
        Constructor<?> ctor = type.getDeclaredConstructor(ProgressBar.class, Label.class, Label.class);
        ctor.setAccessible(true);
        return ctor.newInstance(bar, status, label);
    }

    private Object invoke(String method, Class<?>[] types, Object... args) throws Exception {
        Method m = FileListView.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(fileListView, args);
    }

    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    @Test
    void releaseStatusLabel_matchingWidget_nullsSlotKeepsBar() throws Exception {
        ProgressBar bar = mock(ProgressBar.class);
        Label status = mock(Label.class);
        Label label = mock(Label.class);
        cache.put("f1", widgets(bar, status, label));

        invoke("releaseStatusLabel", new Class<?>[] { String.class, Label.class }, "f1", status);

        assertTrue(cache.containsKey("f1"), "entry with live bar must survive");
        Object kept = cache.get("f1");
        assertEquals(bar, field(kept, "progressBar"));
        assertNull(field(kept, "statusLabel"));
    }

    @Test
    void releaseStatusLabel_recycledRow_doesNotTouchNewEntry() throws Exception {
        Label staleLabel = mock(Label.class);
        Label newLabel = mock(Label.class);
        // Row recycled: cache now points at the NEW file's widgets
        cache.put("f1", widgets(null, newLabel, null));

        // Stale unbind for the same key with the OLD widget must be a no-op
        invoke("releaseStatusLabel", new Class<?>[] { String.class, Label.class }, "f1", staleLabel);

        assertEquals(newLabel, field(cache.get("f1"), "statusLabel"));
    }

    @Test
    void releaseStatusLabel_lastWidget_removesKey() throws Exception {
        Label status = mock(Label.class);
        cache.put("f1", widgets(null, status, null));

        invoke("releaseStatusLabel", new Class<?>[] { String.class, Label.class }, "f1", status);

        assertTrue(cache.isEmpty(), "fully unbound file must not linger in the cache");
    }

    @Test
    void releaseProgressWidgets_matchingPair_evictsWhenStatusGone() throws Exception {
        ProgressBar bar = mock(ProgressBar.class);
        Label label = mock(Label.class);
        Label status = mock(Label.class);
        cache.put("f1", widgets(bar, status, label));

        // Status column unbound first, then the progress column unbinds
        invoke("releaseStatusLabel", new Class<?>[] { String.class, Label.class }, "f1", status);
        invoke("releaseProgressWidgets",
                new Class<?>[] { String.class, ProgressBar.class, Label.class }, "f1", bar, label);

        assertTrue(cache.isEmpty());
    }
}
