package virtuoel.gift_wrap.hooks;

import net.minecraft.item.ItemGroup;

public class ItemGroupHooks
{
	public static ItemGroup.Builder builder()
	{
		return new ItemGroup.Builder(ItemGroup.Row.TOP, 0);
	}
}
