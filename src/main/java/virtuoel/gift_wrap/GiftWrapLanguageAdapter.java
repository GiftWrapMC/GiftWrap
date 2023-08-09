package virtuoel.gift_wrap;

import java.lang.reflect.InvocationTargetException;

import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.LanguageAdapterException;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

public class GiftWrapLanguageAdapter implements LanguageAdapter
{
	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException
	{
		if (type == ModInitializer.class)
		{
			@SuppressWarnings("unchecked")
			T t = (T) new ModInitializer()
			{
				@Override
				public void onInitialize(ModContainer mod)
				{
					try
					{
						Class.forName(value).getDeclaredConstructor().newInstance();
					}
					catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e)
					{
						throw new RuntimeException(e);
					}
					catch (Throwable e)
					{
						e.printStackTrace();
					}
				}
			};
			return t;
		}
		
		throw new LanguageAdapterException("Failed to create entrypoint for type " + type);
	}
}
