package virtuoel.gift_wrap.hooks;

import org.jetbrains.annotations.Nullable;

import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import virtuoel.gift_wrap.extensions.GiftWrapDyeColorExtensions;

public class DyeColorHooks
{
	@Nullable
	public static DyeColor getColor(ItemStack stack)
	{
		if (stack.getItem() instanceof DyeItem item)
			return item.getColor();
		
		for (int x = 0; x < DyeColor.BLACK.getId(); x++)
		{
			DyeColor color = DyeColor.byId(x);
			if (stack.isIn(((GiftWrapDyeColorExtensions) (Object) color).getTag()))
			{
				return color;
			}
		}
		
		return null;
	}
}
