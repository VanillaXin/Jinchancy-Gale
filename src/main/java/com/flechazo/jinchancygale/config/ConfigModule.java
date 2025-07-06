package com.flechazo.jinchancygale.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.jetbrains.annotations.NotNull;

public interface ConfigModule {
    String name();

    default <T> T get(String keyName, T defaultValue, @NotNull CommentedFileConfig config) {
        if (!config.contains(keyName)) {
            config.set(keyName, defaultValue);
            return defaultValue;
        }

        return config.get(keyName);
    }

    default String[] category() {
        return new String[]{};
    }
}