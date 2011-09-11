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

package com.esotericsoftware.jsonbeans;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

/** Builder style API for emitting JSON.
 * @author Nathan Sweet */
public class JsonWriter extends Writer {
	Writer writer;
	private final ArrayList<JsonObject> stack = new ArrayList();
	private JsonObject current;
	private boolean named;
	private OutputType outputType = OutputType.json;

	public JsonWriter (Writer writer) {
		this.writer = writer;
	}

	public void setOutputType (OutputType outputType) {
		this.outputType = outputType;
	}

	public JsonWriter name (String name) throws IOException {
		if (current == null || current.array) throw new IllegalStateException("Current item must be an object.");
		if (!current.needsComma)
			current.needsComma = true;
		else
			writer.write(',');
		writer.write(outputType.quoteName(name));
		writer.write(':');
		named = true;
		return this;
	}

	public JsonWriter object () throws IOException {
		if (current != null) {
			if (current.array) {
				if (!current.needsComma)
					current.needsComma = true;
				else
					writer.write(',');
			} else {
				if (!named && !current.array) throw new IllegalStateException("Name must be set.");
				named = false;
			}
		}
		stack.add(current = new JsonObject(false));
		return this;
	}

	public JsonWriter array () throws IOException {
		if (current != null) {
			if (current.array) {
				if (!current.needsComma)
					current.needsComma = true;
				else
					writer.write(',');
			} else {
				if (!named && !current.array) throw new IllegalStateException("Name must be set.");
				named = false;
			}
		}
		stack.add(current = new JsonObject(true));
		return this;
	}

	public JsonWriter value (Object value) throws IOException {
		if (current == null) throw new IllegalStateException("Current item must be an object or value.");
		if (current.array) {
			if (!current.needsComma)
				current.needsComma = true;
			else
				writer.write(',');
		} else {
			if (!named) throw new IllegalStateException("Name must be set.");
			named = false;
		}
		if (value == null || value instanceof Number || value instanceof Boolean) {
			writer.write(String.valueOf(value));
		} else {
			writer.write(outputType.quoteValue(value.toString()));
		}
		return this;
	}

	public JsonWriter object (String name) throws IOException {
		return name(name).object();
	}

	public JsonWriter array (String name) throws IOException {
		return name(name).array();
	}

	public JsonWriter set (String name, Object value) throws IOException {
		return name(name).value(value);
	}

	public JsonWriter pop () throws IOException {
		if (named) throw new IllegalStateException("Expected an object, array, or value since a name was set.");
		int lastIndex = stack.size() - 1;
		stack.remove(lastIndex).close();
		current = lastIndex == 0 ? null : stack.get(lastIndex - 1);
		return this;
	}

	public void write (char[] cbuf, int off, int len) throws IOException {
		writer.write(cbuf, off, len);
	}

	public void flush () throws IOException {
		writer.flush();
	}

	public void close () throws IOException {
		while (!stack.isEmpty())
			pop();
		writer.close();
	}

	private class JsonObject {
		final boolean array;
		boolean needsComma;

		JsonObject (boolean array) throws IOException {
			this.array = array;
			writer.write(array ? '[' : '{');
		}

		void close () throws IOException {
			writer.write(array ? ']' : '}');
		}
	}
}
