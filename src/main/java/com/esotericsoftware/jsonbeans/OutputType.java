
package com.esotericsoftware.jsonbeans;

import java.util.regex.Pattern;

public enum OutputType {
	/** Normal JSON, with all its quotes. */
	json,
	/** Like JSON, but names are only quoted if necessary. */
	javascript,
	/** Like JSON, but names and values are only quoted if necessary. */
	minimal;

	static private Pattern javascriptPattern = Pattern.compile("[a-zA-Z_$][a-zA-Z_$0-9]*");
	static private Pattern minimalValuePattern = Pattern.compile("[a-zA-Z_$][^:}\\], ]*");
	static private Pattern minimalNamePattern = Pattern.compile("[a-zA-Z0-9_$][^:}\\], ]*");

	public String quoteValue (Object value) {
		if (value == null || value instanceof Number || value instanceof Boolean) return String.valueOf(value);
		String string = String.valueOf(value).replace("\\", "\\\\");
		if (this == OutputType.minimal && !string.equals("true") && !string.equals("false") && !string.equals("null")
			&& minimalValuePattern.matcher(string).matches()) return string;
		return '"' + string.replace("\"", "\\\"") + '"';
	}

	public String quoteName (String value) {
		value = value.replace("\\", "\\\\");
		switch (this) {
		case minimal:
			if (minimalNamePattern.matcher(value).matches()) return value;
			return '"' + value.replace("\"", "\\\"") + '"';
		case javascript:
			if (javascriptPattern.matcher(value).matches()) return value;
			return '"' + value.replace("\"", "\\\"") + '"';
		default:
			return '"' + value.replace("\"", "\\\"") + '"';
		}
	}
}
