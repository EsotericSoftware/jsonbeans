
package com.esotericsoftware.jsonbeans;

public interface JsonSerializable {
	public void write (Json json);

	public void read (Json json, JsonValue jsonData);
}
