package virtuoel.gift_wrap.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.item.Item;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import virtuoel.gift_wrap.extensions.GiftWrapDyeColorExtensions;
import virtuoel.gift_wrap.hooks.ItemTagsHooks;

@Mixin(DyeColor.class)
public class DyeColorMixin implements GiftWrapDyeColorExtensions
{
	TagKey<Item> forge$tag = null;
	
	@Override
	public TagKey<Item> getTag()
	{
		if (forge$tag == null)
		{
			forge$tag = ItemTagsHooks.create(new Identifier("forge", "dyes/" + ((DyeColor) (Object) this).getName()));
		}
		
		return forge$tag;
	}
}
