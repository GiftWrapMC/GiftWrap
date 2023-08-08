package virtuoel.gift_wrap;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;

public interface TomlLoaderValue extends LoaderValue
{
	@Override
	default LObject asObject()
	{
		throw new ClassCastException("Cannot coerce loader value to an Object");
	}
	
	@Override
	default LArray asArray()
	{
		throw new ClassCastException("Cannot coerce loader value to an Array");
	}
	
	@Override
	default String asString()
	{
		throw new ClassCastException("Cannot coerce loader value to a String");
	}
	
	@Override
	default Number asNumber()
	{
		throw new ClassCastException("Cannot coerce loader value to a Number");
	}
	
	@Override
	default boolean asBoolean()
	{
		throw new ClassCastException("Cannot coerce loader value to a boolean");
	}
	
	final class BooleanImpl implements TomlLoaderValue
	{
		private final String location;
		private final boolean value;
		
		BooleanImpl(String location, boolean value)
		{
			this.location = location;
			this.value = value;
		}
		
		@Override
		public boolean asBoolean()
		{
			return value;
		}
		
		@Override
		public LType type()
		{
			return LType.BOOLEAN;
		}
		
		@Override
		public String location()
		{
			return this.location;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			return obj instanceof BooleanImpl && value == ((BooleanImpl) obj).value;
		}
		
		@Override
		public int hashCode()
		{
			return Boolean.hashCode(value);
		}
		
		@Override
		public String toString()
		{
			return value + " @ " + location();
		}
	}
	
	final class ObjectImpl extends AbstractMap<String, LoaderValue> implements TomlLoaderValue, LObject
	{
		private final String location;
		private final Map<String, LoaderValue> value;
		
		ObjectImpl(String location, Map<String, TomlLoaderValue> value)
		{
			this.location = location;
			this.value = Collections.unmodifiableMap(value);
		}
		
		@Override
		public LObject asObject()
		{
			return this;
		}
		
		@Override
		public LType type()
		{
			return LType.OBJECT;
		}
		
		@Override
		public String location()
		{
			return this.location;
		}
		
		@Override
		public Set<Map.Entry<String, LoaderValue>> entrySet()
		{
			return this.value.entrySet();
		}
		
		@Nullable
		@Override
		public TomlLoaderValue get(Object key)
		{
			return (TomlLoaderValue) this.value.get(key);
		}
		
		@Override
		public boolean isEmpty()
		{
			return this.value.isEmpty();
		}
		
		@Override
		public int size()
		{
			return this.value.size();
		}
		
		@Override
		public Set<String> keySet()
		{
			return this.value.keySet();
		}
		
		@Override
		public Collection<LoaderValue> values()
		{
			return this.value.values();
		}
	}
}
