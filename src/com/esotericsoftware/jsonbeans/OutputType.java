
package com.esotericsoftware.jsonbeans;

import java.util.regex.Pattern;

public enum OutputType {
	/** Normal JSON, with all its quotes. */
	json,
	/** Like JSON, but names are only quoted if necessary. */
	javascript,
	/** Like JSON, but:
	 * <ul>
	 * <li>Names only require quotes if they start with <code>space</code> or any of <code>":,}/</code> or they contain
	 * <code>//</code> or <code>/*</code> or <code>:</code>.
	 * <li>Values only require quotes if they start with <code>space</code> or any of <code>":,{[]/</code> or they contain
	 * <code>//</code> or <code>/*</code> or any of <code>}],</code> or they are equal to <code>true</code>, <code>false</code> ,
	 * or <code>null</code>.
	 * <li>Newlines are treated as commas, making commas optional in many cases.
	 * <li>C style comments may be used: <code>//...</code> or <code>/*...*<b></b>/</code>
	 * </ul> */
	minimal;

	static private Pattern javascriptPattern = Pattern.compile("^[a-zA-Z_$][a-zA-Z_$0-9]*$");
	static private Pattern minimalNamePattern = Pattern.compile("^[^\":,}/ ][^:]*$");
	static private Pattern minimalValuePattern = Pattern.compile("^[^\":,{\\[\\]/ ][^}\\],]*$");

	public String quoteValue (Object value) {
		if (value == null || value instanceof Number || value instanceof Boolean) return String.valueOf(value);
		String string = String.valueOf(value).replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
		if (this == OutputType.minimal && !string.equals("true") && !string.equals("false") && !string.equals("null")
			&& !string.contains("//") && !string.contains("/*")) {
			int length = string.length();
			if (length > 0 && string.charAt(length - 1) != ' ' && minimalValuePattern.matcher(string).matches()) return string;
		}
		return '"' + string.replace("\"", "\\\"") + '"';
	}

	public String quoteName (String value) {
		value = value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
		switch (this) {
		case minimal:
			if (!value.contains("//") && !value.contains("/*") && minimalNamePattern.matcher(value).matches()) return value;
		case javascript:
			if (javascriptPattern.matcher(value).matches()) return value;
		}
		return '"' + value.replace("\"", "\\\"") + '"';
	}
}
