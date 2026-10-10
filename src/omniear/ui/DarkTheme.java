package omniear.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.*;
import omniear.alerts.Priority;

/** One standard-JDK palette shared by native Swing controls and custom painting. */
public final class DarkTheme {
    public static final Color BACKGROUND = new Color(17, 22, 29), SURFACE = new Color(25, 33, 43);
    public static final Color TEXT = new Color(230, 237, 245), MUTED = new Color(166, 184, 204);
    public static final Color ACCENT = new Color(85, 220, 191), BORDER = new Color(58, 76, 95);
    private DarkTheme() { }

    public static void install() {
        MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
            @Override public String getName() { return "OmniEar Dark"; }
            @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(BORDER); }
            @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(45, 83, 89); }
            @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(37, 69, 75); }
            @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(MUTED); }
            @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(BORDER); }
            @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(SURFACE); }
            @Override protected ColorUIResource getBlack() { return new ColorUIResource(TEXT); }
            @Override protected ColorUIResource getWhite() { return new ColorUIResource(BACKGROUND); }
        });
        try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
        catch (UnsupportedLookAndFeelException e) { throw new IllegalStateException(e); }
        var font = new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 14);
        for (Object key : java.util.List.copyOf(UIManager.getDefaults().keySet())) {
            if (key.toString().endsWith(".font")) UIManager.put(key, font);
        }
        for (String key : new String[]{"Panel.background", "Viewport.background", "Table.background", "TextArea.background", "TextField.background", "FormattedTextField.background"}) UIManager.put(key, BACKGROUND);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.selectionBackground", new Color(38, 74, 85));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabInsets", new Insets(12, 20, 12, 20));
        UIManager.put("Button.margin", new Insets(8, 14, 8, 14));
        UIManager.put("ToolTip.background", SURFACE);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ProgressBar.selectionForeground", TEXT);
        UIManager.put("ProgressBar.selectionBackground", TEXT);
    }

    public static Color severity(Priority priority) {
        return switch (priority) {
            case P0 -> new Color(255, 137, 151);
            case P1 -> new Color(255, 201, 120);
            case P4 -> new Color(122, 194, 255);
            default -> MUTED;
        };
    }

    public static JPanel section(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JLabel heading = new JLabel(title); heading.setForeground(MUTED);
        panel.add(heading, BorderLayout.NORTH); panel.add(content, BorderLayout.CENTER);
        return panel;
    }
}
