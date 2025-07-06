package com.flechazo.jinchancygale;

import com.flechazo.jinchancygale.config.ConfigManager;
import com.flechazo.jinchancygale.event.EventManager;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(JinchancyGale.MODID)
public class JinchancyGale {
    public static final String MODID = "jinchancy_gale";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JinchancyGale(FMLJavaModLoadingContext context) {
        EventManager.register();
        ConfigManager.register(context);
    }
}
