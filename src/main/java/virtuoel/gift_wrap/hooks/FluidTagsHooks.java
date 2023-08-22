package virtuoel.gift_wrap.hooks;

import net.minecraft.fluid.Fluid;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class FluidTagsHooks
{
	public static TagKey<Fluid> create(Identifier id)
	{
		return TagKey.of(RegistryKeys.FLUID, id);
	}
}
