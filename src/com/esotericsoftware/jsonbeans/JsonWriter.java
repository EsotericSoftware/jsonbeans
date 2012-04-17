
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
		if (current != null) {
			if (current.array) {
				if (!current.needsComma)
					current.needsComma = true;
				else
					writer.write(',');
			} else {
				if (!named) throw new IllegalStateException("Name must be set.");
				named = false;
			}
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
		stack.remove(stack.size() - 1).close();
		current = stack.size() == 0 ? null : stack.get(0);
		return this;
	}

	public void write (char[] cbuf, int off, int len) throws IOException {
		writer.write(cbuf, off, len);
	}

	public void flush () throws IOException {
		writer.flush();
	}

	public void close () throws IOException {
		while (stack.size() > 0)
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

	static public enum OutputType {
		/** Normal JSON, with all its quotes. */
		json,
		/** Like JSON, but names are only quoted if necessary. */
		javascript,
		/** Like JSON, but names and values are only quoted if necessary. */
		minimal;

		// FIXME Avian regex matcher isn't powerful enough
// static private Pattern javascriptPattern = Pattern.compile("[a-zA-Z_$][a-zA-Z_$0-9]*");
// static private Pattern minimalPattern = Pattern.compile("[a-zA-Z_$][^:}\\], ]*");

		public String quoteValue (String value) {
			value = value.replace("\\", "\\\\");
			// FIXME Avian regex matcher isn't powerful enough
// if (this == OutputType.minimal && !value.equals("true") && !value.equals("false") && !value.equals("null")
// && minimalPattern.matcher(value).matches()) return value;
			return '"' + value.replace("\"", "\\\"") + '"';
		}

		public String quoteName (String value) {
			value = value.replace("\\", "\\\\");
			switch (this) {
			case minimal:
				// FIXME Avian regex matcher isn't powerful enough
// if (minimalPattern.matcher(value).matches()) return value;
// return '"' + value.replace("\"", "\\\"") + '"';
			case javascript:
				// FIXME Avian regex matcher isn't powerful enough
// if (javascriptPattern.matcher(value).matches()) return value;
// return '"' + value.replace("\"", "\\\"") + '"';
			default:
				return '"' + value.replace("\"", "\\\"") + '"';
			}
		}
	}
}
