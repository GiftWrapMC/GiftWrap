package virtuoel.gift_wrap.hooks;

import org.objectweb.asm.tree.MethodInsnNode;

import net.minecraft.item.ItemGroup;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.DyeColor;

public class StaticMethodPatcher
{
	public static boolean patch(final MethodInsnNode node)
	{
		if ("create".equals(node.name) && BlockTags.class.getName().equals(node.owner.replace('/', '.')))
		{
			node.owner = "virtuoel/gift_wrap/hooks/BlockTagsHooks";
			return true;
		}
		else if ("getColor".equals(node.name) && DyeColor.class.getName().equals(node.owner.replace('/', '.')))
		{
			node.owner = "virtuoel/gift_wrap/hooks/DyeColorHooks";
			return true;
		}
		else if ("create".equals(node.name) && FluidTags.class.getName().equals(node.owner.replace('/', '.')))
		{
			node.owner = "virtuoel/gift_wrap/hooks/FluidTagsHooks";
			return true;
		}
		else if ("builder".equals(node.name) && ItemGroup.class.getName().equals(node.owner.replace('/', '.')))
		{
			node.owner = "virtuoel/gift_wrap/hooks/ItemGroupHooks";
			return true;
		}
		else if ("create".equals(node.name) && ItemTags.class.getName().equals(node.owner.replace('/', '.')))
		{
			node.owner = "virtuoel/gift_wrap/hooks/ItemTagsHooks";
			return true;
		}
		
		return false;
	}
}
