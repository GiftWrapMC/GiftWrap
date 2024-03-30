package virtuoel.gift_wrap;

import java.lang.reflect.Field;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

import com.terraformersmc.modmenu.util.mod.Mod;

import net.minecraft.text.Text;

public class GiftWrap implements ModInitializer
{
	@Override
	public void onInitialize(final ModContainer mod)
	{
		if (QuiltLoader.isModLoaded("modmenu"))
		{
			try
			{
				final Field text = Mod.Badge.class.getDeclaredField("text");
				text.setAccessible(true);
				text.set(Mod.Badge.PATCHWORK_FORGE, Text.literal("NeoForge"));
				
				final Field outlineColor = Mod.Badge.class.getDeclaredField("outlineColor");
				outlineColor.setAccessible(true);
				outlineColor.set(Mod.Badge.PATCHWORK_FORGE, 0xFFE68C37);
				
				final Field fillColor = Mod.Badge.class.getDeclaredField("fillColor");
				fillColor.setAccessible(true);
				fillColor.set(Mod.Badge.PATCHWORK_FORGE, 0xFFA44E37);
			}
			catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
			{
				e.printStackTrace();
			}
		}
	}
}
