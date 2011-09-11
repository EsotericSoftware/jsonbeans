
package com.esotericsoftware.jsonbeans;

import java.util.regex.Pattern;

public enum OutputType {
	json, javascript, minimal;

	static private Pattern javascriptPattern = Pattern.compile("[a-zA-Z_$][a-zA-Z_$0-9]*");
	static private Pattern minimalPattern = Pattern.compile("[a-zA-Z_$][^:}\\], ]*");

	public String quoteValue (String value) {
		value = value.replace("\\", "\\\\");
		if (this == OutputType.minimal && !value.equals("true") && !value.equals("false") && !value.equals("null")
			&& minimalPattern.matcher(value).matches()) return value;
		return '"' + value + '"';
	}

	public String quoteName (String value) {
		value = value.replace("\\", "\\\\");
		switch (this) {
		case minimal:
			if (minimalPattern.matcher(value).matches()) return value;
			return '"' + value + '"';
		case javascript:
			if (javascriptPattern.matcher(value).matches()) return value;
			return '"' + value + '"';
		default:
			return '"' + value + '"';
		}
	}
}
