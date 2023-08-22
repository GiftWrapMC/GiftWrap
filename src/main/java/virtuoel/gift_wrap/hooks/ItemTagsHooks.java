package virtuoel.gift_wrap.hooks;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class ItemTagsHooks
{
	public static TagKey<Item> create(Identifier id)
	{
		return TagKey.of(RegistryKeys.ITEM, id);
	}
}
