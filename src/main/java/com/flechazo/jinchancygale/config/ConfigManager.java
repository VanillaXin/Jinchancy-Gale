package com.flechazo.jinchancygale.config;

import com.flechazo.jinchancygale.config.flag.ConfigInfo;
import com.flechazo.jinchancygale.config.flag.DoNotLoad;
import com.flechazo.jinchancygale.config.flag.DoNotSync;
import com.flechazo.jinchancygale.config.flag.RangeFlag;
import com.flechazo.jinchancygale.util.ClassLoaderUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class ConfigManager {
    public static final Map<ForgeConfigSpec.ConfigValue, Field> map = new HashMap<>();
    public static final Map<String, Object> defaultValues = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, init());
        context.getModEventBus().addListener(ConfigManager::onConfigLoad);
    }

    public static void onConfigLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getType() == ModConfig.Type.COMMON) {
            load(); // need to load after this is loaded
        }
    }

    private static ForgeConfigSpec init() {
        // first load all modules
        final Set<ConfigModule> configModules = new HashSet<>(ClassLoaderUtil.loadClasses("com.flechazo.modernfurniture.config.module", ConfigModule.class));
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        for (ConfigModule module : configModules) {
            Field[] fields = module.getClass().getDeclaredFields();

            builder.push(module.name());

            // load single instance field
            for (Field field : fields) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                    boolean skipLoad = field.getAnnotation(DoNotLoad.class) != null;
                    ConfigInfo configInfo = field.getAnnotation(ConfigInfo.class);
                    RangeFlag rangeFlag = field.getAnnotation(RangeFlag.class);

                    if (skipLoad || configInfo == null) {
                        continue;
                    }

                    field.setAccessible(true);

                    try {
                        Class<?> type = field.getType();

                        defaultValues.put(field.getName(), field.get(null));

                        ForgeConfigSpec.ConfigValue value = null;

                        if (type == boolean.class) {
                            value = builder.comment(configInfo.comment())
                                    .define(configInfo.name(), (boolean) field.get(null));
                        } else if (type == int.class && rangeFlag != null) {
                            value = builder.comment(configInfo.comment())
                                    .defineInRange(configInfo.name(), (int) field.get(null), Integer.parseInt(rangeFlag.min()), Integer.parseInt(rangeFlag.max()));
                        } else if (type == double.class && rangeFlag != null) {
                            value = builder.comment(configInfo.comment())
                                    .defineInRange(configInfo.name(), (double) field.get(null), Double.parseDouble(rangeFlag.min()), Double.parseDouble(rangeFlag.max()));
                        } else if (type == long.class && rangeFlag != null) {
                            value = builder.comment(configInfo.comment())
                                    .defineInRange(configInfo.name(), (long) field.get(null), Long.parseLong(rangeFlag.min()), Long.parseLong(rangeFlag.max()));
                        }
                        map.put(value, field); // put into map - wait for next process
                    } catch (IllegalAccessException e) {
                        LOGGER.error("Error loading config field: {}", field.getName());
                    }
                }
            }
            builder.pop();
        }
        return builder.build();
    }

    public static Object tryParse(Class<?> targetType, Object value) {
        if (!targetType.isAssignableFrom(value.getClass())) {
            try {
                if (targetType == int.class || targetType == Integer.class) {
                    return value instanceof Number ? ((Number) value).intValue() :
                            Integer.parseInt(value.toString());
                } else if (targetType == long.class || targetType == Long.class) {
                    return value instanceof Number ? ((Number) value).longValue() :
                            Long.parseLong(value.toString());
                } else if (targetType == double.class || targetType == Double.class) {
                    return value instanceof Number ? ((Number) value).doubleValue() :
                            Double.parseDouble(value.toString());
                } else if (targetType == float.class || targetType == Float.class) {
                    return value instanceof Number ? ((Number) value).floatValue() :
                            Float.parseFloat(value.toString());
                } else if (targetType == boolean.class || targetType == Boolean.class) {
                    return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
                } else if (targetType == String.class) {
                    return value.toString();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to transform value {}!", value);
                throw new IllegalFormatConversionException((char) 0, targetType);
            }
        }
        return value;
    }

    public static void load() { // load all fields
        map.forEach((value, field) -> {
            field.setAccessible(true);
            try {
                if (value != null)
                    field.set(null, value.get());
            } catch (IllegalAccessException e) {
                LOGGER.error("Error setting value to config field: {}", field.getName());
            }
        });
    }

    public static void syncValue(Map<String, Object> serverConfig, boolean flag) {
        map.forEach((value, field) -> {
            field.setAccessible(true);
            try {
                if (value != null) {
                    Object newValue = serverConfig.get(field.getName());
                    if (newValue != null) {
                        field.set(null, newValue);
                        if (flag) value.set(tryParse(field.get(null).getClass(), newValue));
                    }
                }
            } catch (IllegalAccessException e) {
                LOGGER.error("Error sync config field: {}", field.getName());
            }
        });
    }

    public static Field getField(String key) {
        return map.entrySet().stream().filter(entry -> entry.getValue().getName().equals(key)).findFirst().map(Map.Entry::getValue).orElse(null);
    }

    public static Pair<Number, Number> getRange(String key) {
        Field field = getField(key);
        if (field == null) return null;
        RangeFlag rangeFlag = field.getAnnotation(RangeFlag.class);
        return rangeFlag != null ? Pair.of(Integer.parseInt(rangeFlag.min()), Integer.parseInt(rangeFlag.max())) : null;
    }

    public static Map<String, Object> createSyncData(boolean getAll) {
        Map<String, Object> map = new HashMap<>();
        Map.copyOf(ConfigManager.map).forEach((configValue, field) -> {
            field.setAccessible(true);
            DoNotSync doNotSync = field.getAnnotation(DoNotSync.class);
            if ((!getAll) && doNotSync != null) return;
            map.put(field.getName(), configValue.get());
        });
        return map;
    }
}
