package virtuoel.gift_wrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;

import net.minecraft.item.ItemGroup;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.DyeColor;

public class GiftWrapModScanner
{
	public static final Map<String, String> SHADOWED_FIELD_NAMES = new HashMap<>();
	
	public static void scanModClasses(Path modRoot, ModMetadataExt metadata, boolean shouldPatch)
	{
		final String accessWidener = metadata.id() + ".accesswidener";
		final Path accessWidenerPath = modRoot.resolve(accessWidener);
		if (Files.exists(accessWidenerPath))
		{
			metadata.accessWideners().add(accessWidener);
		}
		
		final Map<String, Collection<AdapterLoadableClassEntry>> entrypoints = metadata.getEntrypoints();
		final Collection<AdapterLoadableClassEntry> initEntrypoints = new ArrayList<>();
		
		final Map<String, String> modClasses = new HashMap<>();
		final Map<String, String> mixinClasses = new HashMap<>();
		
		try
		{
			Files.walk(modRoot).forEach(p ->
			{
				final String fileName = modRoot.relativize(p).toString();
				
				if (!fileName.endsWith(".class"))
				{
					return;
				}
				
				byte[] patchedBytes = null;
				try (final InputStream in = Files.newInputStream(p))
				{
					boolean[] patched = { false };
					ClassWriter writer = shouldPatch ? new ClassWriter(0) : null;
					ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, writer)
					{
						@Override
						public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible)
						{
							if ("Lnet/minecraftforge/fml/common/Mod;".equals(descriptor))
							{
								modClasses.put(fileName, fileName.substring(0, fileName.length() - 6).replace('/', '.'));
							}
							
							if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor))
							{
								mixinClasses.put(fileName, fileName.substring(0, fileName.length() - 6).replace('/', '.'));
							}
							
							return super.visitAnnotation(descriptor, visible);
						}
						
						@Override
						public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value)
						{
							if (shouldPatch)
							{
								if (mixinClasses.containsKey(fileName))
								{
									if (name.length() > 3 && name.startsWith("f_") && name.endsWith("_") && SHADOWED_FIELD_NAMES.containsKey(name))
									{
										patched[0] = true;
										return super.visitField(access, SHADOWED_FIELD_NAMES.get(name), descriptor, signature, value);
									}
								}
							}
							
							return super.visitField(access, name, descriptor, signature, value);
						}
						
						@Override
						public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions)
						{
							return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions))
							{
								@Override
								public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible)
								{
									return super.visitAnnotation(annotationDescriptor, visible);
								}
								
								@Override
								public void visitMethodInsn(int opcode, String owner, String insnName, String descriptor, boolean isInterface)
								{
									if (shouldPatch)
									{
										// TODO make better and only run once
										if ("builder".equals(insnName) && ItemGroup.class.getName().equals(owner.replace('/', '.')))
										{
											super.visitMethodInsn(opcode, "virtuoel/gift_wrap/hooks/ItemGroupHooks", insnName, descriptor, isInterface);
											patched[0] = true;
											return;
										}
										else if ("create".equals(insnName) && BlockTags.class.getName().equals(owner.replace('/', '.')))
										{
											super.visitMethodInsn(opcode, "virtuoel/gift_wrap/hooks/BlockTagsHooks", insnName, descriptor, isInterface);
											patched[0] = true;
											return;
										}
										else if ("create".equals(insnName) && ItemTags.class.getName().equals(owner.replace('/', '.')))
										{
											super.visitMethodInsn(opcode, "virtuoel/gift_wrap/hooks/ItemTagsHooks", insnName, descriptor, isInterface);
											patched[0] = true;
											return;
										}
										else if ("getColor".equals(insnName) && DyeColor.class.getName().equals(owner.replace('/', '.')))
										{
											super.visitMethodInsn(opcode, "virtuoel/gift_wrap/hooks/DyeColorHooks", insnName, descriptor, isInterface);
											patched[0] = true;
											return;
										}
										else if ("create".equals(insnName) && FluidTags.class.getName().equals(owner.replace('/', '.')))
										{
											super.visitMethodInsn(opcode, "virtuoel/gift_wrap/hooks/FluidTagsHooks", insnName, descriptor, isInterface);
											patched[0] = true;
											return;
										}
									}
									super.visitMethodInsn(opcode, owner, insnName, descriptor, isInterface);
								}
							};
						}
					};
					
					ClassReader reader = new ClassReader(in);
					reader.accept(classVisitor, 0);
					
					if (shouldPatch && patched[0])
					{
						patchedBytes = writer.toByteArray();
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				
				if (patchedBytes != null)
				{
					try (final OutputStream out = Files.newOutputStream(p))
					{
						out.write(patchedBytes);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			});
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		for (final String modClass : modClasses.values())
		{
			try
			{
				initEntrypoints.add(AdapterLoadableClassEntry.class.getConstructor(String.class, String.class).newInstance("gift_wrap", modClass));
			}
			catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e)
			{
				e.printStackTrace();
			}
		}
		
		entrypoints.put("init", initEntrypoints);
	}
}
