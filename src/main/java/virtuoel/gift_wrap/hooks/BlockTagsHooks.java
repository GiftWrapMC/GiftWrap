package virtuoel.gift_wrap.hooks;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class BlockTagsHooks
{
	public static TagKey<Block> create(Identifier id)
	{
		return TagKey.of(RegistryKeys.BLOCK, id);
	}
}
