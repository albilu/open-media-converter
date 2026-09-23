package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.gnome.gtk.ApplicationWindow;
import org.gnome.gtk.GtkBuilder;
import org.gnome.gtk.Widget;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;

/**
 * Regression tests for builder lifetime in {@link MainWindowJavaGi}: the
 * GtkApplicationWindow instantiated by the GtkBuilder is stripped of its child
 * and titlebar for reparenting and must then be destroyed so the empty shell
 * does not live for the whole session, and the builder reference must be
 * released once all widget references have been looked up.
 *
 * <p>
 * The window is a {@code CALLS_REAL_METHODS} mock (the real constructor needs
 * GTK) with GTK self-calls stubbed to no-ops; the builder is a mocked
 * construction so no native objects are created.
 * </p>
 */
class MainWindowLoadUiTest {

    @Test
    void loadUI_destroysStrippedBuilderWindowAfterReparenting() throws Exception {
        MainWindowJavaGi window = mock(MainWindowJavaGi.class, CALLS_REAL_METHODS);
        doNothing().when(window).setTitle(any());
        doNothing().when(window).setDefaultSize(anyInt(), anyInt());
        doNothing().when(window).setChild(any());
        doNothing().when(window).setTitlebar(any());

        ApplicationWindow builderWindow = mock(ApplicationWindow.class);
        Widget content = mock(Widget.class);
        Widget titlebar = mock(Widget.class);
        when(builderWindow.getChild()).thenReturn(content);
        when(builderWindow.getTitlebar()).thenReturn(titlebar);

        try (MockedConstruction<GtkBuilder> construction = mockConstruction(GtkBuilder.class,
                (builder, context) -> when(builder.getObject("mainWindow")).thenReturn(builderWindow))) {
            invoke(window, "loadUI");
        }

        InOrder order = inOrder(builderWindow);
        order.verify(builderWindow).setChild(null);
        order.verify(builderWindow).setTitlebar(null);
        order.verify(builderWindow).destroy();
    }

    @Test
    void setupWidgetReferences_releasesBuilderReference() throws Exception {
        MainWindowJavaGi window = mock(MainWindowJavaGi.class, CALLS_REAL_METHODS);
        GtkBuilder builder = mock(GtkBuilder.class);
        builderField().set(window, builder);

        invoke(window, "setupWidgetReferences");

        assertNull(builderField().get(window), "builder must be released once all widgets are looked up");
    }

    private static Field builderField() throws Exception {
        Field field = MainWindowJavaGi.class.getDeclaredField("builder");
        field.setAccessible(true);
        return field;
    }

    private static void invoke(MainWindowJavaGi window, String methodName) throws Exception {
        Method method = MainWindowJavaGi.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(window);
    }
}
