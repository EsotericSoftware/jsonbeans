
package com.esotericsoftware.jsonbeans.dom;

public class JsonValue implements JsonObject {
	private String value;
	private JsonType type;

	public JsonValue (String value) {
		set(value);
	}

	public JsonValue (float value) {
		set(value);
	}

	public JsonValue (boolean value) {
		set(value);
	}

	public JsonValue (String value, JsonType type) {
		this.value = value;
		this.type = type;
	}

	public void set (String value) {
		this.value = value;
		type = JsonType.string;
	}

	public void set (float value) {
		this.value = Float.toString(value);
		type = JsonType.number;
	}

	public void set (boolean value) {
		this.value = Boolean.toString(value);
		type = JsonType.bool;
	}

	public Object get () {
		switch (type) {
		case string:
			return value;
		case number:
			return toFloat();
		case bool:
			return toBoolean();
		}
		return null;
	}

	public JsonType getType () {
		return type;
	}

	public void setType (JsonType type) {
		this.type = type;
	}

	public float toFloat () {
		return Float.parseFloat(value);
	}

	public int toInt () {
		return Integer.parseInt(value);
	}

	public double toDouble () {
		return Double.parseDouble(value);
	}

	public short toShort () {
		return Short.parseShort(value);
	}

	public byte toByte () {
		return Byte.parseByte(value);
	}

	public boolean toBoolean () {
		return Boolean.parseBoolean(value);
	}

	public long toLong () {
		return Long.parseLong(value);
	}

	public char toChar () {
		return value.charAt(0);
	}

	public String toString () {
		return value;
	}
}
