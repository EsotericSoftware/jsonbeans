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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.esotericsoftware.jsonbeans.dom.JsonArray;
import com.esotericsoftware.jsonbeans.dom.JsonMap;
import com.esotericsoftware.jsonbeans.dom.JsonObject;
import com.esotericsoftware.jsonbeans.dom.JsonValue;

/** Reads/writes Java objects to/from JSON, automatically.
 * @author Nathan Sweet */
public class Json {
	private static final boolean debug = false;

	private JsonWriter writer;
	private String typeName = "class";
	private boolean usePrototypes = true;
	private OutputType outputType = OutputType.json;
	private final HashMap<Class, HashMap<String, FieldMetadata>> typeToFields = new HashMap();
	private final HashMap<String, Class> tagToClass = new HashMap();
	private final HashMap<Class, String> classToTag = new HashMap();
	private final HashMap<Class, Serializer> classToSerializer = new HashMap();
	private final HashMap<Class, Object[]> classToDefaultValues = new HashMap();
	private boolean ignoreUnknownFields;

	public void setIgnoreUnknownFields (boolean ignoreUnknownFields) {
		this.ignoreUnknownFields = ignoreUnknownFields;
	}

	public void setOutputType (OutputType outputType) {
		if (outputType == null) throw new IllegalArgumentException("outputType cannot be null.");
		this.outputType = outputType;
	}

	public void addClassTag (String tag, Class type) {
		if (tag == null) throw new IllegalArgumentException("tag cannot be null.");
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		tagToClass.put(tag, type);
		classToTag.put(type, tag);
	}

	/** Sets the name of the JSON field to store the Java class name or class tag when required to avoid ambiguity during
	 * deserialization. Set to null to never output this information, but be warned that deserialization may fail. */
	public void setTypeName (String typeName) {
		this.typeName = typeName;
	}

	public <T> void setSerializer (Class<T> type, Serializer<T> serializer) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		if (serializer == null) throw new IllegalArgumentException("serializer cannot be null.");
		classToSerializer.put(type, serializer);
	}

	public void setUsePrototypes (boolean usePrototypes) {
		this.usePrototypes = usePrototypes;
	}

	public void setElementType (Class type, String fieldName, Class elementType) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		if (fieldName == null) throw new IllegalArgumentException("fieldName cannot be null.");
		if (elementType == null) throw new IllegalArgumentException("elementType cannot be null.");
		HashMap<String, FieldMetadata> fields = typeToFields.get(type);
		if (fields == null) fields = cacheFields(type);
		FieldMetadata metadata = fields.get(fieldName);
		if (metadata == null) throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
		metadata.elementType = elementType;
	}

	private HashMap<String, FieldMetadata> cacheFields (Class type) {
		ArrayList<Field> allFields = new ArrayList();
		Class nextClass = type;
		while (nextClass != Object.class) {
			Collections.addAll(allFields, nextClass.getDeclaredFields());
			nextClass = nextClass.getSuperclass();
		}

		HashMap<String, FieldMetadata> nameToField = new HashMap();
		for (int i = 0, n = allFields.size(); i < n; i++) {
			Field field = allFields.get(i);

			int modifiers = field.getModifiers();
			if (Modifier.isTransient(modifiers)) continue;
			if (Modifier.isStatic(modifiers)) continue;
			if (field.isSynthetic()) continue;

			if (!field.isAccessible()) {
				try {
					field.setAccessible(true);
				} catch (AccessControlException ex) {
					continue;
				}
			}

			nameToField.put(field.getName(), new FieldMetadata(field));
		}
		typeToFields.put(type, nameToField);
		return nameToField;
	}

	public String toJson (Object object) {
		return toJson(object, object == null ? null : object.getClass(), (Class)null);
	}

	public String toJson (Object object, Class knownType) {
		return toJson(object, knownType, (Class)null);
	}

	public String toJson (Object object, Class knownType, Class elementType) {
		StringWriter buffer = new StringWriter();
		toJson(object, knownType, elementType, buffer);
		return buffer.toString();
	}

	public void toJson (Object object, File file) {
		toJson(object, object == null ? null : object.getClass(), null, file);
	}

	public void toJson (Object object, Class knownType, File file) {
		toJson(object, knownType, null, file);
	}

	public void toJson (Object object, Class knownType, Class elementType, File file) {
		Writer writer = null;
		try {
			writer = new FileWriter(file);
			toJson(object, knownType, elementType, writer);
		} catch (Exception ex) {
			throw new SerializationException("Error writing file: " + file, ex);
		} finally {
			try {
				if (writer != null) writer.close();
			} catch (IOException ignored) {
			}
		}
	}

	public void toJson (Object object, Writer writer) {
		toJson(object, object == null ? null : object.getClass(), null, writer);
	}

	public void toJson (Object object, Class knownType, Writer writer) {
		toJson(object, knownType, null, writer);
	}

	public void toJson (Object object, Class knownType, Class elementType, Writer writer) {
		if (!(writer instanceof JsonWriter)) {
			this.writer = new JsonWriter(writer);
			((JsonWriter)this.writer).setOutputType(outputType);
		}
		writeValue(object, knownType, elementType);
	}

	public void writeFields (Object object) {
		Class type = object.getClass();

		Object[] defaultValues = getDefaultValues(type);

		HashMap<String, FieldMetadata> fields = typeToFields.get(type);
		if (fields == null) fields = cacheFields(type);
		int i = 0;
		for (FieldMetadata metadata : fields.values()) {
			Field field = metadata.field;
			try {
				Object value = field.get(object);

				if (defaultValues != null) {
					Object defaultValue = defaultValues[i++];
					if (value == null && defaultValue == null) continue;
					if (value != null && defaultValue != null && value.equals(defaultValue)) continue;
				}

				if (debug) System.out.println("Writing field: " + field.getName() + " (" + type.getName() + ")");
				writer.name(field.getName());
				writeValue(value, field.getType(), metadata.elementType);
			} catch (IllegalAccessException ex) {
				throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
			} catch (SerializationException ex) {
				ex.addTrace(field + " (" + type.getName() + ")");
				throw ex;
			} catch (Exception runtimeEx) {
				SerializationException ex = new SerializationException(runtimeEx);
				ex.addTrace(field + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	private Object[] getDefaultValues (Class type) {
		if (!usePrototypes) return null;
		Object[] values = classToDefaultValues.get(type);
		if (values == null) {
			Object object = newInstance(type);

			HashMap<String, FieldMetadata> fields = typeToFields.get(type);
			if (fields == null) fields = cacheFields(type);

			values = new Object[fields.size()];
			classToDefaultValues.put(type, values);

			int i = 0;
			for (FieldMetadata metadata : fields.values()) {
				Field field = metadata.field;
				try {
					values[i++] = field.get(object);
				} catch (IllegalAccessException ex) {
					throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
				} catch (SerializationException ex) {
					ex.addTrace(field + " (" + type.getName() + ")");
					throw ex;
				} catch (RuntimeException runtimeEx) {
					SerializationException ex = new SerializationException(runtimeEx);
					ex.addTrace(field + " (" + type.getName() + ")");
					throw ex;
				}
			}
		}
		return values;
	}

	public void writeField (Object object, String name) {
		writeField(object, name, name, null);
	}

	public void writeField (Object object, String name, Class elementType) {
		writeField(object, name, name, elementType);
	}

	public void writeField (Object object, String fieldName, String jsonName) {
		writeField(object, fieldName, jsonName, null);
	}

	public void writeField (Object object, String fieldName, String jsonName, Class elementType) {
		Class type = object.getClass();
		HashMap<String, FieldMetadata> fields = typeToFields.get(type);
		if (fields == null) fields = cacheFields(type);
		FieldMetadata metadata = fields.get(fieldName);
		if (metadata == null) throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
		Field field = metadata.field;
		if (elementType == null) elementType = metadata.elementType;
		try {
			if (debug) System.out.println("Writing field: " + field.getName() + " (" + type.getName() + ")");
			writer.name(jsonName);
			writeValue(field.get(object), field.getType(), elementType);
		} catch (IllegalAccessException ex) {
			throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
		} catch (SerializationException ex) {
			ex.addTrace(field + " (" + type.getName() + ")");
			throw ex;
		} catch (Exception runtimeEx) {
			SerializationException ex = new SerializationException(runtimeEx);
			ex.addTrace(field + " (" + type.getName() + ")");
			throw ex;
		}
	}

	public void writeValue (String name, Object value) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
		writeValue(value, value.getClass(), null);
	}

	public void writeValue (String name, Object value, Class knownType) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
		writeValue(value, knownType, null);
	}

	public void writeValue (String name, Object value, Class knownType, Class elementType) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
		writeValue(value, knownType, elementType);
	}

	public void writeValue (Object value) {
		writeValue(value, value.getClass(), null);
	}

	public void writeValue (Object value, Class knownType) {
		writeValue(value, knownType, null);
	}

	public void writeValue (Object value, Class knownType, Class elementType) {
		try {
			if (value == null) {
				writer.value(null);
				return;
			}

			Class actualType = value.getClass();

			if (actualType.isPrimitive() || actualType == String.class || actualType == Integer.class || actualType == Boolean.class
				|| actualType == Float.class || actualType == Long.class || actualType == Double.class || actualType == Short.class
				|| actualType == Byte.class || actualType == Character.class) {
				writer.value(value);
				return;
			}

			if (value instanceof Serializable) {
				writeObjectStart(actualType, knownType);
				((Serializable)value).write(this);
				writeObjectEnd();
				return;
			}

			Serializer serializer = classToSerializer.get(actualType);
			if (serializer != null) {
				serializer.write(this, value, knownType);
				return;
			}

			if (value instanceof Collection) {
				if (knownType != null && actualType != knownType)
					throw new SerializationException("Serialization of a Collection other than the known type is not supported.\n"
						+ "Known type: " + knownType + "\nActual type: " + actualType);
				writeArrayStart();
				for (Object item : (Collection)value)
					writeValue(item, elementType, null);
				writeArrayEnd();
				return;
			}

			if (actualType.isArray()) {
				if (elementType == null) elementType = actualType.getComponentType();
				int length = java.lang.reflect.Array.getLength(value);
				writeArrayStart();
				for (int i = 0; i < length; i++)
					writeValue(java.lang.reflect.Array.get(value, i), elementType, null);
				writeArrayEnd();
				return;
			}

			if (value instanceof Map) {
				if (knownType == null) knownType = HashMap.class;
				writeObjectStart(actualType, knownType);
				for (Map.Entry entry : ((Map<?, ?>)value).entrySet()) {
					writer.name(convertToString(entry.getKey()));
					writeValue(entry.getValue(), elementType, null);
				}
				writeObjectEnd();
				return;
			}

			if (actualType.isEnum()) {
				writer.value(value);
				return;
			}

			writeObjectStart(actualType, knownType);
			writeFields(value);
			writeObjectEnd();
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
	}

	public void writeObjectStart (String name) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
		writeObjectStart();
	}

	public void writeObjectStart (String name, Class actualType, Class knownType) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
		writeObjectStart(actualType, knownType);
	}

	public void writeObjectStart () {
		try {
			writer.object();
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
	}

	public void writeObjectStart (Class actualType, Class knownType) {
		try {
			writer.object();
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
		if (knownType == null || knownType != actualType) writeType(actualType);
	}

	public void writeObjectEnd () {
		try {
			writer.pop();
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
	}

	public void writeArrayStart (String name) {
		try {
			writer.name(name);
			writer.array();
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
	}

	public void writeArrayStart () {
		try {
			writer.array();
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
	}

	public void writeArrayEnd () {
		try {
			writer.pop();
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
	}

	public void writeType (Class type) {
		if (typeName == null) return;
		String className = classToTag.get(type);
		if (className == null) className = type.getName();
		try {
			writer.set(typeName, className);
		} catch (IOException ex) {
			throw new SerializationException(ex);
		}
		if (debug) System.out.println("Writing type: " + type.getName());
	}

	public <T> T fromJson (Class<T> type, Reader reader) {
		return (T)readValue(type, null, new JsonReader().parse(reader));
	}

	public <T> T fromJson (Class<T> type, Class elementType, Reader reader) {
		return (T)readValue(type, elementType, new JsonReader().parse(reader));
	}

	public <T> T fromJson (Class<T> type, InputStream input) {
		return (T)readValue(type, null, new JsonReader().parse(input));
	}

	public <T> T fromJson (Class<T> type, Class elementType, InputStream input) {
		return (T)readValue(type, elementType, new JsonReader().parse(input));
	}

	public <T> T fromJson (Class<T> type, File file) {
		try {
			return (T)readValue(type, null, new JsonReader().parse(file));
		} catch (Exception ex) {
			throw new SerializationException("Error reading file: " + file, ex);
		}
	}

	public <T> T fromJson (Class<T> type, Class elementType, File file) {
		try {
			return (T)readValue(type, elementType, new JsonReader().parse(file));
		} catch (Exception ex) {
			throw new SerializationException("Error reading file: " + file, ex);
		}
	}

	public <T> T fromJson (Class<T> type, char[] data, int offset, int length) {
		return (T)readValue(type, null, new JsonReader().parse(data, offset, length));
	}

	public <T> T fromJson (Class<T> type, Class elementType, char[] data, int offset, int length) {
		return (T)readValue(type, elementType, new JsonReader().parse(data, offset, length));
	}

	public <T> T fromJson (Class<T> type, String json) {
		return (T)readValue(type, null, new JsonReader().parse(json));
	}

	public <T> T fromJson (Class<T> type, Class elementType, String json) {
		return (T)readValue(type, elementType, new JsonReader().parse(json));
	}

	public void readField (Object object, String name, JsonObject jsonData) {
		readField(object, name, name, null, jsonData);
	}

	public void readField (Object object, String name, Class elementType, JsonObject jsonData) {
		readField(object, name, name, elementType, jsonData);
	}

	public void readField (Object object, String fieldName, String jsonName, JsonObject jsonData) {
		readField(object, fieldName, jsonName, null, jsonData);
	}

	public void readField (Object object, String fieldName, String jsonName, Class elementType, JsonObject jsonData) {
		JsonMap jsonMap = (JsonMap)jsonData;
		Class type = object.getClass();
		HashMap<String, FieldMetadata> fields = typeToFields.get(type);
		if (fields == null) fields = cacheFields(type);
		FieldMetadata metadata = fields.get(fieldName);
		if (metadata == null) throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
		Field field = metadata.field;
		JsonObject jsonValue = jsonMap.get(jsonName);
		if (jsonValue == null) return;
		if (elementType == null) elementType = metadata.elementType;
		try {
			field.set(object, readValue(field.getType(), elementType, jsonValue));
		} catch (IllegalAccessException ex) {
			throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
		} catch (SerializationException ex) {
			ex.addTrace(field.getName() + " (" + type.getName() + ")");
			throw ex;
		} catch (RuntimeException runtimeEx) {
			SerializationException ex = new SerializationException(runtimeEx);
			ex.addTrace(field.getName() + " (" + type.getName() + ")");
			throw ex;
		}
	}

	public void readFields (Object object, JsonObject jsonData) {
		JsonMap jsonMap = (JsonMap)jsonData;
		Class type = object.getClass();
		HashMap<String, FieldMetadata> fields = typeToFields.get(type);
		if (fields == null) fields = cacheFields(type);
		for (Entry<String, JsonObject> entry : jsonMap.entrySet()) {
			FieldMetadata metadata = fields.get(entry.getKey());
			Field field = metadata.field;
			if (ignoreUnknownFields) {
				if (debug) System.out.println("Ignoring unknown field: " + entry.getKey() + " (" + type.getName() + ")");
			} else if (field == null)
				throw new SerializationException("Field not found: " + entry.getKey() + " (" + type.getName() + ")");
			if (entry.getValue() == null) continue;
			try {
				field.set(object, readValue(field.getType(), metadata.elementType, entry.getValue()));
			} catch (IllegalAccessException ex) {
				throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
			} catch (SerializationException ex) {
				ex.addTrace(field.getName() + " (" + type.getName() + ")");
				throw ex;
			} catch (RuntimeException runtimeEx) {
				SerializationException ex = new SerializationException(runtimeEx);
				ex.addTrace(field.getName() + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	public <T> T readValue (String name, Class<T> type, JsonObject jsonData) {
		JsonMap jsonMap = (JsonMap)jsonData;
		return (T)readValue(type, null, jsonMap.get(name));
	}

	public <T> T readValue (String name, Class<T> type, T defaultValue, JsonObject jsonData) {
		JsonMap jsonMap = (JsonMap)jsonData;
		JsonObject jsonValue = jsonMap.get(name);
		if (jsonValue == null) return defaultValue;
		return (T)readValue(type, null, jsonValue);
	}

	public <T> T readValue (String name, Class<T> type, Class elementType, JsonObject jsonData) {
		JsonMap jsonMap = (JsonMap)jsonData;
		return (T)readValue(type, elementType, jsonMap.get(name));
	}

	public <T> T readValue (String name, Class<T> type, Class elementType, T defaultValue, JsonObject jsonData) {
		JsonMap jsonMap = (JsonMap)jsonData;
		JsonObject jsonValue = jsonMap.get(name);
		if (jsonValue == null) return defaultValue;
		return (T)readValue(type, elementType, jsonValue);
	}

	public <T> T readValue (Class<T> type, Class elementType, T defaultValue, JsonObject jsonData) {
		return (T)readValue(type, elementType, jsonData);
	}

	public <T> T readValue (Class<T> type, JsonObject jsonData) {
		return (T)readValue(type, null, jsonData);
	}

	public <T> T readValue (Class<T> type, Class elementType, JsonObject jsonData) {
		if (jsonData instanceof JsonMap) {
			JsonMap jsonMap = (JsonMap)jsonData;

			JsonObject className = typeName == null ? null : jsonMap.remove(typeName);
			if (className instanceof JsonValue) {
				try {
					type = (Class<T>)Class.forName(className.toString());
				} catch (ClassNotFoundException ex) {
					type = tagToClass.get(className);
					if (type == null) throw new SerializationException(ex);
				}
			}

			Object object;
			if (type != null) {
				Serializer serializer = classToSerializer.get(type);
				if (serializer != null) return (T)serializer.read(this, jsonMap, type);

				object = newInstance(type);

				if (object instanceof Serializable) {
					((Serializable)object).read(this, jsonMap);
					return (T)object;
				}
			} else
				object = new HashMap();

			if (object instanceof HashMap) {
				HashMap result = (HashMap)object;
				for (Entry<String, JsonObject> entry : jsonMap.entrySet())
					result.put(entry.getKey(), readValue(elementType, null, entry.getValue()));
				return (T)result;
			}

			readFields(object, jsonMap);
			return (T)object;
		}

		if (type != null) {
			Serializer serializer = classToSerializer.get(type);
			if (serializer != null) return (T)serializer.read(this, jsonData, type);
		}

		if (jsonData instanceof JsonArray) {
			JsonArray array = (JsonArray)jsonData;
			if (type == null || type.isAssignableFrom(ArrayList.class)) {
				ArrayList newList = new ArrayList(array.size());
				for (int i = 0, n = array.size(); i < n; i++)
					newList.add(readValue(elementType, null, array.get(i)));
				return (T)newList;
			}
			if (type.isArray()) {
				Class componentType = type.getComponentType();
				if (elementType == null) elementType = componentType;
				Object newArray = java.lang.reflect.Array.newInstance(componentType, array.size());
				for (int i = 0, n = array.size(); i < n; i++)
					java.lang.reflect.Array.set(newArray, i, readValue(elementType, null, array.get(i)));
				return (T)newArray;
			}
			throw new SerializationException("Unable to convert value to required type: " + jsonData + " (" + type.getName() + ")");
		}

		if (jsonData instanceof JsonValue) {
			JsonValue value = (JsonValue)jsonData;
			if (type == null) return (T)value.get();
			if (type == String.class) return (T)value.toString();
			try {
				if (type == float.class || type == Float.class) return (T)(Float)value.toFloat();
				if (type == int.class || type == Integer.class) return (T)(Integer)value.toInt();
				if (type == long.class || type == Long.class) return (T)(Long)value.toLong();
				if (type == double.class || type == Double.class) return (T)(Double)value.toDouble();
				if (type == short.class || type == Short.class) return (T)(Short)value.toShort();
				if (type == byte.class || type == Byte.class) return (T)(Byte)value.toByte();
			} catch (NumberFormatException ignored) {
			}
			if (type == boolean.class || type == Boolean.class) return (T)(Boolean)value.toBoolean();
			if (type == char.class || type == Character.class) return (T)(Character)value.toChar();
			String string = value.toString();
			if (type.isEnum()) {
				Object[] constants = type.getEnumConstants();
				for (int i = 0, n = constants.length; i < n; i++)
					if (string.equals(constants[i].toString())) return (T)constants[i];
			}
			if (type == CharSequence.class) return (T)string;
			throw new SerializationException("Unable to convert value to required type: " + jsonData + " (" + type.getName() + ")");
		}

		return null;
	}

	protected String convertToString (Object object) {
		if (object instanceof Class) return ((Class)object).getName();
		return String.valueOf(object);
	}

	private Object newInstance (Class type) {
		try {
			return type.newInstance();
		} catch (Exception ex) {
			try {
				// Try a private constructor.
				Constructor constructor = type.getDeclaredConstructor();
				constructor.setAccessible(true);
				return constructor.newInstance();
			} catch (SecurityException ignored) {
			} catch (NoSuchMethodException ignored) {
				if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers()))
					throw new SerializationException("Class cannot be created (non-static member class): " + type.getName(), ex);
				else
					throw new SerializationException("Class cannot be created (missing no-arg constructor): " + type.getName(), ex);
			} catch (Exception privateConstructorException) {
				ex = privateConstructorException;
			}
			throw new SerializationException("Error constructing instance of class: " + type.getName(), ex);
		}
	}

	public String prettyPrint (Object object) {
		return prettyPrint(object, false);
	}

	public String prettyPrint (String json) {
		return prettyPrint(json, false);
	}

	public String prettyPrint (Object object, boolean fieldsOnSameLine) {
		return prettyPrint(toJson(object), fieldsOnSameLine);
	}

	public String prettyPrint (String json, boolean fieldsOnSameLine) {
		StringBuilder buffer = new StringBuilder(512);
		prettyPrint(new JsonReader().parse(json), buffer, 0, fieldsOnSameLine);
		return buffer.toString();
	}

	private void prettyPrint (JsonObject object, StringBuilder buffer, int indent, boolean fieldsOnSameLine) {
		if (object instanceof JsonMap) {
			JsonMap map = (JsonMap)object;
			if (map.size() == 0) {
				buffer.append("{}");
			} else {
				boolean newLines = !fieldsOnSameLine || !isFlat(map);
				buffer.append(newLines ? "{\n" : "{ ");
				int i = 0;
				for (Entry<String, JsonObject> entry : map.entrySet()) {
					if (newLines) indent(indent, buffer);
					buffer.append(outputType.quoteName((String)entry.getKey()));
					buffer.append(": ");
					prettyPrint(entry.getValue(), buffer, indent + 1, fieldsOnSameLine);
					if (i++ < map.size() - 1) buffer.append(",");
					buffer.append(newLines ? '\n' : ' ');
				}
				if (newLines) indent(indent - 1, buffer);
				buffer.append('}');
			}
		} else if (object instanceof JsonArray) {
			JsonArray array = (JsonArray)object;
			if (array.isEmpty()) {
				buffer.append("[]");
			} else {
				boolean newLines = !fieldsOnSameLine || !isFlat(array);
				buffer.append(newLines ? "[\n" : "[ ");
				for (int i = 0, n = array.size(); i < n; i++) {
					if (newLines) indent(indent, buffer);
					prettyPrint(array.get(i), buffer, indent + 1, fieldsOnSameLine);
					if (i < n - 1) buffer.append(",");
					buffer.append(newLines ? '\n' : ' ');
				}
				if (newLines) indent(indent - 1, buffer);
				buffer.append(']');
			}
		} else if (object == null) {
			buffer.append("null");
		} else if (object instanceof JsonValue) {
			JsonValue value = (JsonValue)object;
			switch (value.getType()) {
			case string:
				buffer.append(outputType.quoteValue(value.toString()));
				break;
			case number:
				Float floatValue = value.toFloat();
				int intValue = floatValue.intValue();
				buffer.append(floatValue - intValue == 0 ? intValue : object);
				break;
			case bool:
				buffer.append(value.toBoolean());
				break;
			}
		} else
			throw new SerializationException("Unknown object type: " + object.getClass());
	}

	static private boolean isFlat (JsonMap map) {
		for (Entry entry : map.entrySet()) {
			if (entry.getValue() instanceof JsonMap) return false;
			if (entry.getValue() instanceof JsonArray) return false;
		}
		return true;
	}

	static private boolean isFlat (JsonArray array) {
		for (int i = 0, n = array.size(); i < n; i++) {
			JsonObject value = array.get(i);
			if (value instanceof JsonMap) return false;
			if (value instanceof JsonArray) return false;
		}
		return true;
	}

	static private void indent (int count, StringBuilder buffer) {
		for (int i = 0; i < count; i++)
			buffer.append('\t');
	}

	static private class FieldMetadata {
		public Field field;
		public Class elementType;

		public FieldMetadata (Field field) {
			this.field = field;
		}
	}

	static public interface Serializer<T> {
		public void write (Json json, T object, Class knownType);

		public T read (Json json, JsonObject jsonData, Class type);
	}

	static public interface Serializable {
		public void write (Json json);

		public void read (Json json, JsonMap jsonMap);
	}
}
