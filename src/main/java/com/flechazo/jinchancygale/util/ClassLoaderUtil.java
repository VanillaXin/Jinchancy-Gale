package com.flechazo.jinchancygale.util;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ClassLoaderUtil {
    private static final Logger LOGGER = LogManager.getLogger();

    public static List<Class<?>> getClassesInPackage(String packageName) {
        return ModList.get().getModFiles().stream()
                .map(IModFileInfo::getFile)
                .flatMap(file -> file.getScanResult().getClasses().stream())  // 获取所有类信息
                .map(classData -> classData.clazz().getClassName())
                .filter(className -> className.startsWith(packageName))
                .map(className -> {
                    try {
                        return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
                    } catch (ClassNotFoundException e) {
                        LOGGER.error("Class not found: {}", className);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static <T> List<T> loadClasses(String packageName, Class<T> targetType) {
        List<Class<?>> classes = getClassesInPackage(packageName);
        return classes.stream()
                .filter(clazz -> {
                    try {
                        return targetType.isAssignableFrom(clazz) &&
                                !clazz.isInterface() &&
                                !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(clazz -> {
                    try {
                        return targetType.cast(clazz.getDeclaredConstructor().newInstance());
                    } catch (Exception e) {
                        LOGGER.error("Failed to instantiate {}: {}", clazz.getName(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
