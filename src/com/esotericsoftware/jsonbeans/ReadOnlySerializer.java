
package com.esotericsoftware.jsonbeans;

import com.esotericsoftware.jsonbeans.Json.Serializer;

public abstract class ReadOnlySerializer<T> implements Serializer<T> {
	public void write (Json json, T object, Class knownType) {
	}

	abstract public T read (Json json, JsonValue jsonData, Class type);
}
