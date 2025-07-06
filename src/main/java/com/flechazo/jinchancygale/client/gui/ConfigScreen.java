package com.flechazo.jinchancygale.client.gui;

import com.flechazo.jinchancygale.config.ConfigManager;
import com.flechazo.jinchancygale.network.NetworkHandler;
import com.flechazo.jinchancygale.network.module.ConfigPacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.widget.ForgeSlider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigScreen extends Screen {
    // UI layout constants
    private static final int ITEM_HEIGHT = 25;
    private static final int MARGIN = 10;
    private static final int LABEL_WIDTH = 150;
    private static final int CONTROL_WIDTH = 100;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_TOP = 30;
    private static final int PANEL_BOTTOM_MARGIN = 50;
    // Stores widget references for current page
    private final Map<String, AbstractWidget> configWidgets = new HashMap<>();
    // Stores modified configuration values across pages
    private final Map<String, Object> modifiedConfigCache = new HashMap<>();
    // Original configuration entries
    private final List<Map.Entry<String, Object>> configEntries;
    private final boolean isClient;
    private ConfigScreen origin = null;
    // Pagination variables
    private int currentPage = 0;
    private int itemsPerPage = 0;
    private int totalPages = 0;
    private int panelHeight = 0;

    public ConfigScreen(Map<String, Object> serverConfig, boolean isClient) {
        super(Component.literal("JinChancy Gale Config"));
        this.configEntries = new ArrayList<>(serverConfig.entrySet());
        this.isClient = isClient;
    }

    @Override
    protected void init() {
        super.init();

        // Calculate panel height based on screen size
        panelHeight = height - PANEL_TOP - PANEL_BOTTOM_MARGIN;

        // Calculate how many items can fit per page
        itemsPerPage = Math.max(1, panelHeight / ITEM_HEIGHT);
        totalPages = (int) Math.ceil((double) configEntries.size() / itemsPerPage);

        // Create page navigation buttons
        createNavigationButtons();

        // Create config widgets for current page
        createPageWidgets();
    }

    private void createNavigationButtons() {
        // Previous page button
        addRenderableWidget(Button.builder(
                        Component.translatable("config.jinchancy_gale.prev"),
                        button -> {
                            if (currentPage > 0) {
                                saveCurrentPageChanges();
                                currentPage--;
                                clearWidgets();
                                init();
                            }
                        })
                .bounds(width / 2 - 130, height - 30, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        // Page indicator
        addRenderableWidget(new StringWidget(
                width / 2 - 30,
                height - 30,
                60,
                20,
                Component.literal((currentPage + 1) + "/" + totalPages),
                font
        ));

        // Next page button
        addRenderableWidget(Button.builder(
                        Component.translatable("config.jinchancy_gale.next"),
                        button -> {
                            if (currentPage < totalPages - 1) {
                                saveCurrentPageChanges();
                                currentPage++;
                                clearWidgets();
                                init();
                            }
                        })
                .bounds(width / 2 + 70, height - 30, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        // Unified Y position for action buttons
        int actionY = height - 60;
        // Reduced button width for better fit
        int actionButtonWidth = 80;
        // Calculate total width of action buttons
        int totalWidth = 4 * actionButtonWidth + 3 * 10; // 4 buttons with 10px spacing
        // Starting X position for centered layout
        int startX = (width - totalWidth) / 2;

        // Switch button
        addRenderableWidget(Button.builder(
                        Component.translatable(isClient ?
                                "config.jinchancy_gale.switch_to_server" :
                                "config.jinchancy_gale.switch_to_client"),
                        button -> {
                            if (origin != null) {
                                Minecraft.getInstance().setScreen(origin);
                            } else {
                                origin = new ConfigScreen(ConfigManager.createSyncData(true), true);
                                origin.origin = this;
                                Minecraft.getInstance().setScreen(origin);
                            }
                        })
                .bounds(startX, actionY, actionButtonWidth, BUTTON_HEIGHT)
                .build());

        // Save button
        addRenderableWidget(Button.builder(
                                Component.translatable("config.jinchancy_gale.save"),
                                button -> saveConfig()
                        )
                        .bounds(startX + actionButtonWidth + 10, actionY, actionButtonWidth, BUTTON_HEIGHT)
                        .build()
        );

        // Reset button
        addRenderableWidget(Button.builder(
                                Component.translatable("config.jinchancy_gale.reset_default"),
                                button -> resetToDefaults()
                        )
                        .bounds(startX + 2 * (actionButtonWidth + 10), actionY, actionButtonWidth, BUTTON_HEIGHT)
                        .build()
        );

        // Cancel button
        addRenderableWidget(Button.builder(
                                Component.translatable("config.jinchancy_gale.cancel"),
                                button -> {
                                    onClose();
                                }
                        )
                        .bounds(startX + 3 * (actionButtonWidth + 10), actionY, actionButtonWidth, BUTTON_HEIGHT)
                        .build()
        );
    }


    private void resetToDefaults() {
        // clear cache
        modifiedConfigCache.clear();

        if (isClient) {
            // load default value from client
            applyData(ConfigManager.defaultValues);
        } else {
            // send load default value request to server
            ConfigPacket packet = ConfigPacket.reSyncRequest();
            NetworkHandler.sendToServer(packet);
        }
    }

    private void createPageWidgets() {
        // Calculate start and end indices for current page
        int startIdx = currentPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, configEntries.size());

        // Create config items for current page
        int yPos = PANEL_TOP;
        for (int i = startIdx; i < endIdx; i++) {
            Map.Entry<String, Object> entry = configEntries.get(i);
            String key = entry.getKey();

            // Use cached value if modified, otherwise use original value
            Object value = modifiedConfigCache.containsKey(key) ?
                    modifiedConfigCache.get(key) :
                    entry.getValue();

            // Add label
            String label = formatConfigKey(key);
            AbstractWidget labelWidget = new StringWidget(
                    MARGIN,
                    yPos,
                    LABEL_WIDTH,
                    font.lineHeight,
                    Component.literal(label),
                    font
            );
            addRenderableWidget(labelWidget);

            // Create control widget based on value type
            AbstractWidget controlWidget = createControlWidget(key, value, yPos);
            addRenderableWidget(controlWidget);
            configWidgets.put(key, controlWidget);

            yPos += ITEM_HEIGHT;
        }
    }

    private AbstractWidget createControlWidget(String key, Object value, int yPos) {
        int controlX = MARGIN + LABEL_WIDTH + 10;

        if (value instanceof Boolean) {
            return new Checkbox(
                    controlX,
                    yPos,
                    CONTROL_WIDTH,
                    20,
                    Component.literal(""),
                    (Boolean) value,
                    true
            );
        } else if (value instanceof Integer || value instanceof Long) {
            long min = 0;
            long max = 10000;
            Pair<Number, Number> range = ConfigManager.getRange(key);
            if (range != null) {
                min = range.getFirst().longValue();
                max = range.getSecond().longValue();
            }

            return new ForgeSlider(
                    controlX, yPos, CONTROL_WIDTH, 20,
                    Component.literal(""), Component.literal(""),
                    min, max, ((Number) value).longValue(), true
            );
        } else if (value instanceof Float || value instanceof Double) {
            double min = 0;
            double max = 10000;
            Pair<Number, Number> range = ConfigManager.getRange(key);
            if (range != null) {
                min = range.getFirst().doubleValue();
                max = range.getSecond().doubleValue();
            }
            return new ForgeSlider(
                    controlX, yPos, CONTROL_WIDTH, 20,
                    Component.literal(""), Component.literal(""),
                    min, max, ((Number) value).doubleValue(),
                    0.01, 2, true
            );
        } else if (value instanceof String) {
            EditBox editBox = new EditBox(
                    font,
                    controlX,
                    yPos,
                    CONTROL_WIDTH,
                    20,
                    Component.literal("")
            );
            editBox.setValue((String) value);
            return editBox;
        } else {
            return Button.builder(
                            Component.literal("Unsupported"),
                            button -> {
                            }
                    )
                    .bounds(controlX, yPos, CONTROL_WIDTH, 20)
                    .build();
        }
    }

    /**
     * Saves current page widget values to cache only if they differ from original
     * Called before page navigation
     */
    private void saveCurrentPageChanges() {
        for (Map.Entry<String, AbstractWidget> entry : configWidgets.entrySet()) {
            String key = entry.getKey();
            AbstractWidget widget = entry.getValue();
            Object originalValue = getOriginalValue(key);

            if (widget instanceof Checkbox checkbox) {
                boolean currentValue = checkbox.selected();
                if (originalValue instanceof Boolean && (Boolean) originalValue != currentValue) {
                    modifiedConfigCache.put(key, currentValue);
                }
            } else if (widget instanceof ForgeSlider slider) {
                Number currentValue = slider.getValue();
                if (originalValue instanceof Number original) {
                    if (Math.abs(original.doubleValue() - currentValue.doubleValue()) > 0.0001) {
                        modifiedConfigCache.put(key, ConfigManager.tryParse(originalValue.getClass(), currentValue));
                    }
                }
            } else if (widget instanceof EditBox editBox) {
                String currentValue = editBox.getValue();
                if (originalValue instanceof String && !originalValue.equals(currentValue)) {
                    modifiedConfigCache.put(key, currentValue);
                }
            }
        }
    }

    /**
     * Gets the original value for a configuration key
     */
    private Object getOriginalValue(String key) {
        for (Map.Entry<String, Object> entry : configEntries) {
            if (entry.getKey().equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String formatConfigKey(String key) {
        return key.replaceAll("([A-Z])", " $1")
                .replaceAll("^\\s+", "")
                .replaceAll("\\s+", " ");
    }

    private void saveConfig() {
        // Save current page before final save
        saveCurrentPageChanges();
        if (isClient) {
            ConfigManager.syncValue(modifiedConfigCache, true);
        } else {
            // Send all cached modifications to server
            ConfigPacket packet = ConfigPacket.createForUpdate(modifiedConfigCache);
            NetworkHandler.sendToServer(packet);
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        delOrigin();
    }

    private void delOrigin() {
        if (this.origin != null) {
            this.origin.origin = null;
            this.origin = null;
        }
    }

    public void updateConfig(Map<String, Object> configData) {
        applyData(configData);
        clearWidgets();
        init();
    }

    public void applyData(Map<String, Object> configData) {
        configData.forEach((key, value) -> {
            Object originalValue = getOriginalValue(key);
            if (originalValue instanceof Boolean && value instanceof Boolean && originalValue != value) {
                modifiedConfigCache.put(key, value);
            } else if (originalValue instanceof Number && value instanceof Number) {
                double original = ((Number) originalValue).doubleValue();
                if (Math.abs(original - ((Number) value).doubleValue()) > 0.0001) {
                    modifiedConfigCache.put(key, value);
                }
            } else if (originalValue instanceof String && value instanceof String && !originalValue.equals(value)) {
                modifiedConfigCache.put(key, value);
            }
        });
    }
}
