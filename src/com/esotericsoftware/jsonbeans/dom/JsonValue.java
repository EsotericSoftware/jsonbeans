/*******************************************************************************
 * Copyright (c) 2011, Nathan Sweet <nathan.sweet@gmail.com>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package com.esotericsoftware.jsonbeans.dom;

/** @author Nathan Sweet */
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
