package virtuoel.gift_wrap.extensions;

import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

public interface GiftWrapItemGroupBuilder
{
	default ItemGroup.Builder self()
	{
		return (ItemGroup.Builder) this;
	}
	
	default ItemGroup.Builder withTabsBefore(Identifier... tabs)
	{
		// TODO
		return self();
	}
	
	@SuppressWarnings("unchecked")
	default ItemGroup.Builder withTabsBefore(RegistryKey<ItemGroup>... tabs)
	{
		// TODO
		return self();
	}
}
