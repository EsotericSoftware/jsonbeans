/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.esotericsoftware.jsonbeans;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.esotericsoftware.jsonbeans.JsonValue.PrettyPrintSettings;
import com.esotericsoftware.jsonbeans.ObjectMap.Entry;
import com.esotericsoftware.jsonbeans.OrderedMap.OrderedMapValues;

/** Reads/writes Java objects to/from JSON, automatically. See the wiki for usage:
 * https://github.com/libgdx/libgdx/wiki/Reading-%26-writing-JSON
 * @author Nathan Sweet */
public class Json {
	static private final boolean debug = false;

	private JsonWriter writer;
	private String typeName = "class";
	private boolean usePrototypes = true;
	private OutputType outputType;
	private boolean quoteLongValues;
	private boolean ignoreUnknownFields;
	private boolean enumNames = true;
	private JsonSerializer defaultSerializer;
	private final ObjectMap<Class, OrderedMap<String, FieldMetadata>> typeToFields = new ObjectMap();
	private final ObjectMap<String, Class> tagToClass = new ObjectMap();
	private final ObjectMap<Class, String> classToTag = new ObjectMap();
	private final ObjectMap<Class, JsonSerializer> classToSerializer = new ObjectMap();
	private final ObjectMap<Class, Object[]> classToDefaultValues = new ObjectMap();
	private final Object[] equals1 = {null}, equals2 = {null};

	public Json () {
		outputType = OutputType.minimal;
	}

	public Json (OutputType outputType) {
		this.outputType = outputType;
	}

	/** When true, fields in the JSON that are not found on the class will not throw a {@link JsonException}. Default is false. */
	public void setIgnoreUnknownFields (boolean ignoreUnknownFields) {
		this.ignoreUnknownFields = ignoreUnknownFields;
	}

	/** @see JsonWriter#setOutputType(OutputType) */
	public void setOutputType (OutputType outputType) {
		this.outputType = outputType;
	}

	/** @see JsonWriter#setQuoteLongValues(boolean) */
	public void setQuoteLongValues (boolean quoteLongValues) {
		this.quoteLongValues = quoteLongValues;
	}

	/** When true, {@link Enum#name()} is used to write enum values. When false, {@link Enum#toString()} is used which may not be
	 * unique. Default is true. */
	public void setEnumNames (boolean enumNames) {
		this.enumNames = enumNames;
	}

	/** Sets a tag to use instead of the fully qualifier class name. This can make the JSON easier to read. */
	public void addClassTag (String tag, Class type) {
		tagToClass.put(tag, type);
		classToTag.put(type, tag);
	}

	/** Returns the class for the specified tag, or null. */
	public Class getClass (String tag) {
		return tagToClass.get(tag);
	}

	/** Returns the tag for the specified class, or null. */
	public String getTag (Class type) {
		return classToTag.get(type);
	}

	/** Sets the name of the JSON field to store the Java class name or class tag when required to avoid ambiguity during
	 * deserialization. Set to null to never output this information, but be warned that deserialization may fail. Default is
	 * "class". */
	public void setTypeName (String typeName) {
		this.typeName = typeName;
	}

	/** Sets the serializer to use when the type being deserialized is not known (null).
	 * @param defaultSerializer May be null. */
	public void setDefaultSerializer (JsonSerializer defaultSerializer) {
		this.defaultSerializer = defaultSerializer;
	}

	/** Registers a serializer to use for the specified type instead of the default behavior of serializing all of an objects
	 * fields. */
	public <T> void setSerializer (Class<T> type, JsonSerializer<T> serializer) {
		classToSerializer.put(type, serializer);
	}

	public <T> JsonSerializer<T> getSerializer (Class<T> type) {
		return classToSerializer.get(type);
	}

	/** When true, field values that are identical to a newly constructed instance are not written. Default is true. */
	public void setUsePrototypes (boolean usePrototypes) {
		this.usePrototypes = usePrototypes;
	}

	/** Sets the type of elements in a collection. When the element type is known, the class for each element in the collection
	 * does not need to be written unless different from the element type. */
	public void setElementType (Class type, String fieldName, Class elementType) {
		ObjectMap<String, FieldMetadata> fields = getFields(type);
		FieldMetadata metadata = fields.get(fieldName);
		if (metadata == null) throw new JsonException("Field not found: " + fieldName + " (" + type.getName() + ")");
		metadata.elementType = elementType;
	}

	private OrderedMap<String, FieldMetadata> getFields (Class type) {
		OrderedMap<String, FieldMetadata> fields = typeToFields.get(type);
		if (fields != null) return fields;

		ArrayList<Field> allFields = new ArrayList();
		Class nextClass = type;
		while (nextClass != Object.class) {
			Collections.addAll(allFields, nextClass.getDeclaredFields());
			nextClass = nextClass.getSuperclass();
		}

		OrderedMap<String, FieldMetadata> nameToField = new OrderedMap();
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

	/** @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public String toJson (Object object, Class knownType, Class elementType) {
		StringWriter buffer = new StringWriter();
		toJson(object, knownType, elementType, buffer);
		return buffer.toString();
	}

	public void toJson (Object object, File file) {
		toJson(object, object == null ? null : object.getClass(), null, file);
	}

	/** @param knownType May be null if the type is unknown. */
	public void toJson (Object object, Class knownType, File file) {
		toJson(object, knownType, null, file);
	}

	/** @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public void toJson (Object object, Class knownType, Class elementType, File file) {
		Writer writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
			toJson(object, knownType, elementType, writer);
		} catch (Exception ex) {
			throw new JsonException("Error writing file: " + file, ex);
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

	/** @param knownType May be null if the type is unknown. */
	public void toJson (Object object, Class knownType, Writer writer) {
		toJson(object, knownType, null, writer);
	}

	/** @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public void toJson (Object object, Class knownType, Class elementType, Writer writer) {
		setWriter(writer);
		try {
			writeValue(object, knownType, elementType);
		} finally {
			if (this.writer != null) {
				try {
					this.writer.close();
				} catch (IOException ignored) {
				}
			}
			this.writer = null;
		}
	}

	/** Sets the writer where JSON output will be written. This is only necessary when not using the toJson methods. */
	public void setWriter (Writer writer) {
		if (!(writer instanceof JsonWriter)) writer = new JsonWriter(writer);
		this.writer = (JsonWriter)writer;
		this.writer.setOutputType(outputType);
		this.writer.setQuoteLongValues(quoteLongValues);
	}

	public JsonWriter getWriter () {
		return writer;
	}

	/** Writes all fields of the specified object to the current JSON object. */
	public void writeFields (Object object) {
		Class type = object.getClass();

		Object[] defaultValues = getDefaultValues(type);

		OrderedMap<String, FieldMetadata> fields = getFields(type);
		int i = 0;
		for (FieldMetadata metadata : new OrderedMapValues<FieldMetadata>(fields)) {
			Field field = metadata.field;
			try {
				Object value = field.get(object);
				if (defaultValues != null) {
					Object defaultValue = defaultValues[i++];
					if (value == null && defaultValue == null) continue;
					if (value != null && defaultValue != null) {
						if (value.equals(defaultValue)) continue;
						if (value.getClass().isArray() && defaultValue.getClass().isArray()) {
							equals1[0] = value;
							equals2[0] = defaultValue;
							if (Arrays.deepEquals(equals1, equals2)) continue;
						}
					}
				}

				if (debug) System.out.println("Writing field: " + field.getName() + " (" + type.getName() + ")");
				writer.name(field.getName());
				writeValue(value, field.getType(), metadata.elementType);
			} catch (IllegalAccessException ex) {
				throw new JsonException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
			} catch (JsonException ex) {
				ex.addTrace(field + " (" + type.getName() + ")");
				throw ex;
			} catch (Exception runtimeEx) {
				JsonException ex = new JsonException(runtimeEx);
				ex.addTrace(field + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	private Object[] getDefaultValues (Class type) {
		if (!usePrototypes) return null;
		if (classToDefaultValues.containsKey(type)) return classToDefaultValues.get(type);
		Object object;
		try {
			object = newInstance(type);
		} catch (Exception ex) {
			classToDefaultValues.put(type, null);
			return null;
		}

		ObjectMap<String, FieldMetadata> fields = getFields(type);
		Object[] values = new Object[fields.size];
		classToDefaultValues.put(type, values);

		int i = 0;
		for (FieldMetadata metadata : fields.values()) {
			Field field = metadata.field;
			try {
				values[i++] = field.get(object);
			} catch (IllegalAccessException ex) {
				throw new JsonException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
			} catch (JsonException ex) {
				ex.addTrace(field + " (" + type.getName() + ")");
				throw ex;
			} catch (RuntimeException runtimeEx) {
				JsonException ex = new JsonException(runtimeEx);
				ex.addTrace(field + " (" + type.getName() + ")");
				throw ex;
			}
		}
		return values;
	}

	/** @see #writeField(Object, String, String, Class) */
	public void writeField (Object object, String name) {
		writeField(object, name, name, null);
	}

	/** @param elementType May be null if the type is unknown.
	 * @see #writeField(Object, String, String, Class) */
	public void writeField (Object object, String name, Class elementType) {
		writeField(object, name, name, elementType);
	}

	/** @see #writeField(Object, String, String, Class) */
	public void writeField (Object object, String fieldName, String jsonName) {
		writeField(object, fieldName, jsonName, null);
	}

	/** Writes the specified field to the current JSON object.
	 * @param elementType May be null if the type is unknown. */
	public void writeField (Object object, String fieldName, String jsonName, Class elementType) {
		Class type = object.getClass();
		ObjectMap<String, FieldMetadata> fields = getFields(type);
		FieldMetadata metadata = fields.get(fieldName);
		if (metadata == null) throw new JsonException("Field not found: " + fieldName + " (" + type.getName() + ")");
		Field field = metadata.field;
		if (elementType == null) elementType = metadata.elementType;
		try {
			if (debug) System.out.println("Writing field: " + field.getName() + " (" + type.getName() + ")");
			writer.name(jsonName);
			writeValue(field.get(object), field.getType(), elementType);
		} catch (IllegalAccessException ex) {
			throw new JsonException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
		} catch (JsonException ex) {
			ex.addTrace(field + " (" + type.getName() + ")");
			throw ex;
		} catch (Exception runtimeEx) {
			JsonException ex = new JsonException(runtimeEx);
			ex.addTrace(field + " (" + type.getName() + ")");
			throw ex;
		}
	}

	/** Writes the value as a field on the current JSON object, without writing the actual class.
	 * @param value May be null.
	 * @see #writeValue(String, Object, Class, Class) */
	public void writeValue (String name, Object value) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
		if (value == null)
			writeValue(value, null, null);
		else
			writeValue(value, value.getClass(), null);
	}

	/** Writes the value as a field on the current JSON object, writing the class of the object if it differs from the specified
	 * known type.
	 * @param value May be null.
	 * @param knownType May be null if the type is unknown.
	 * @see #writeValue(String, Object, Class, Class) */
	public void writeValue (String name, Object value, Class knownType) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
		writeValue(value, knownType, null);
	}

	/** Writes the value as a field on the current JSON object, writing the class of the object if it differs from the specified
	 * known type. The specified element type is used as the default type for collections.
	 * @param value May be null.
	 * @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public void writeValue (String name, Object value, Class knownType, Class elementType) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
		writeValue(value, knownType, elementType);
	}

	/** Writes the value, without writing the class of the object.
	 * @param value May be null. */
	public void writeValue (Object value) {
		if (value == null)
			writeValue(value, null, null);
		else
			writeValue(value, value.getClass(), null);
	}

	/** Writes the value, writing the class of the object if it differs from the specified known type.
	 * @param value May be null.
	 * @param knownType May be null if the type is unknown. */
	public void writeValue (Object value, Class knownType) {
		writeValue(value, knownType, null);
	}

	/** Writes the value, writing the class of the object if it differs from the specified known type. The specified element type
	 * is used as the default type for collections.
	 * @param value May be null.
	 * @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public void writeValue (Object value, Class knownType, Class elementType) {
		try {
			if (value == null) {
				writer.value(null);
				return;
			}

			if ((knownType != null && knownType.isPrimitive()) || knownType == String.class || knownType == Integer.class
				|| knownType == Boolean.class || knownType == Float.class || knownType == Long.class || knownType == Double.class
				|| knownType == Short.class || knownType == Byte.class || knownType == Character.class) {
				writer.value(value);
				return;
			}

			Class actualType = value.getClass();

			if (actualType.isPrimitive() || actualType == String.class || actualType == Integer.class || actualType == Boolean.class
				|| actualType == Float.class || actualType == Long.class || actualType == Double.class || actualType == Short.class
				|| actualType == Byte.class || actualType == Character.class) {
				writeObjectStart(actualType, null);
				writeValue("value", value);
				writeObjectEnd();
				return;
			}

			if (value instanceof JsonSerializable) {
				writeObjectStart(actualType, knownType);
				((JsonSerializable)value).write(this);
				writeObjectEnd();
				return;
			}

			JsonSerializer serializer = classToSerializer.get(actualType);
			if (serializer != null) {
				serializer.write(this, value, knownType);
				return;
			}

			// JSON array special cases.
			if (value instanceof ArrayList) {
				if (knownType != null && actualType != knownType && actualType != ArrayList.class)
					throw new JsonException("Serialization of an Array other than the known type is not supported.\n" + "Known type: "
						+ knownType + "\nActual type: " + actualType);
				writeArrayStart();
				ArrayList array = (ArrayList)value;
				for (int i = 0, n = array.size(); i < n; i++)
					writeValue(array.get(i), elementType, null);
				writeArrayEnd();
				return;
			}
			if (value instanceof Collection) {
				if (typeName != null && actualType != ArrayList.class && (knownType == null || knownType != actualType)) {
					writeObjectStart(actualType, knownType);
					writeArrayStart("items");
					for (Object item : (Collection)value)
						writeValue(item, elementType, null);
					writeArrayEnd();
					writeObjectEnd();
				} else {
					writeArrayStart();
					for (Object item : (Collection)value)
						writeValue(item, elementType, null);
					writeArrayEnd();
				}
				return;
			}
			if (actualType.isArray()) {
				if (elementType == null) elementType = actualType.getComponentType();
				int length = Array.getLength(value);
				writeArrayStart();
				for (int i = 0; i < length; i++)
					writeValue(Array.get(value, i), elementType, null);
				writeArrayEnd();
				return;
			}

			// JSON object special cases.
			if (value instanceof ObjectMap) {
				if (knownType == null) knownType = ObjectMap.class;
				writeObjectStart(actualType, knownType);
				for (Entry entry : ((ObjectMap<?, ?>)value).entries()) {
					writer.name(convertToString(entry.key));
					writeValue(entry.value, elementType, null);
				}
				writeObjectEnd();
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

			// Enum special case.
			if (Enum.class.isAssignableFrom(actualType)) {
				if (typeName != null && (knownType == null || knownType != actualType)) {
					// Ensures that enums with specific implementations (abstract logic) serialize correctly.
					if (actualType.getEnumConstants() == null) actualType = actualType.getSuperclass();

					writeObjectStart(actualType, null);
					writer.name("value");
					writer.value(convertToString((Enum)value));
					writeObjectEnd();
				} else {
					writer.value(convertToString((Enum)value));
				}
				return;
			}

			writeObjectStart(actualType, knownType);
			writeFields(value);
			writeObjectEnd();
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
	}

	public void writeObjectStart (String name) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
		writeObjectStart();
	}

	/** @param knownType May be null if the type is unknown. */
	public void writeObjectStart (String name, Class actualType, Class knownType) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
		writeObjectStart(actualType, knownType);
	}

	public void writeObjectStart () {
		try {
			writer.object();
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
	}

	/** Starts writing an object, writing the actualType to a field if needed.
	 * @param knownType May be null if the type is unknown. */
	public void writeObjectStart (Class actualType, Class knownType) {
		try {
			writer.object();
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
		if (knownType == null || knownType != actualType) writeType(actualType);
	}

	public void writeObjectEnd () {
		try {
			writer.pop();
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
	}

	public void writeArrayStart (String name) {
		try {
			writer.name(name);
			writer.array();
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
	}

	public void writeArrayStart () {
		try {
			writer.array();
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
	}

	public void writeArrayEnd () {
		try {
			writer.pop();
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
	}

	public void writeType (Class type) {
		if (typeName == null) return;
		String className = getTag(type);
		if (className == null) className = type.getName();
		try {
			writer.set(typeName, className);
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
		if (debug) System.out.println("Writing type: " + type.getName());
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, Reader reader) {
		return (T)readValue(type, null, new JsonReader().parse(reader));
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, Class elementType, Reader reader) {
		return (T)readValue(type, elementType, new JsonReader().parse(reader));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, InputStream input) {
		return (T)readValue(type, null, new JsonReader().parse(input));
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, Class elementType, InputStream input) {
		return (T)readValue(type, elementType, new JsonReader().parse(input));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, File file) {
		try {
			return (T)readValue(type, null, new JsonReader().parse(file));
		} catch (Exception ex) {
			throw new JsonException("Error reading file: " + file, ex);
		}
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, Class elementType, File file) {
		try {
			return (T)readValue(type, elementType, new JsonReader().parse(file));
		} catch (Exception ex) {
			throw new JsonException("Error reading file: " + file, ex);
		}
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, char[] data, int offset, int length) {
		return (T)readValue(type, null, new JsonReader().parse(data, offset, length));
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, Class elementType, char[] data, int offset, int length) {
		return (T)readValue(type, elementType, new JsonReader().parse(data, offset, length));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, String json) {
		return (T)readValue(type, null, new JsonReader().parse(json));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T fromJson (Class<T> type, Class elementType, String json) {
		return (T)readValue(type, elementType, new JsonReader().parse(json));
	}

	public void readField (Object object, String name, JsonValue jsonData) {
		readField(object, name, name, null, jsonData);
	}

	public void readField (Object object, String name, Class elementType, JsonValue jsonData) {
		readField(object, name, name, elementType, jsonData);
	}

	public void readField (Object object, String fieldName, String jsonName, JsonValue jsonData) {
		readField(object, fieldName, jsonName, null, jsonData);
	}

	/** @param elementType May be null if the type is unknown. */
	public void readField (Object object, String fieldName, String jsonName, Class elementType, JsonValue jsonMap) {
		Class type = object.getClass();
		ObjectMap<String, FieldMetadata> fields = getFields(type);
		FieldMetadata metadata = fields.get(fieldName);
		if (metadata == null) throw new JsonException("Field not found: " + fieldName + " (" + type.getName() + ")");
		Field field = metadata.field;
		if (elementType == null) elementType = metadata.elementType;
		readField(object, field, jsonName, elementType, jsonMap);
	}

	/** @param object May be null if the field is static.
	 * @param elementType May be null if the type is unknown. */
	public void readField (Object object, Field field, String jsonName, Class elementType, JsonValue jsonMap) {
		JsonValue jsonValue = jsonMap.get(jsonName);
		if (jsonValue == null) return;
		try {
			field.set(object, readValue(field.getType(), elementType, jsonValue));
		} catch (IllegalAccessException ex) {
			throw new JsonException("Error accessing field: " + field.getName() + " (" + field.getDeclaringClass().getName() + ")",
				ex);
		} catch (JsonException ex) {
			ex.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
			throw ex;
		} catch (RuntimeException runtimeEx) {
			JsonException ex = new JsonException(runtimeEx);
			ex.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
			throw ex;
		}
	}

	public void readFields (Object object, JsonValue jsonMap) {
		Class type = object.getClass();
		ObjectMap<String, FieldMetadata> fields = getFields(type);
		for (JsonValue child = jsonMap.child; child != null; child = child.next) {
			FieldMetadata metadata = fields.get(child.name().replace(" ", "_"));
			if (metadata == null) {
				if (ignoreUnknownFields) {
					if (debug) System.out.println("Ignoring unknown field: " + child.name() + " (" + type.getName() + ")");
					continue;
				} else
					throw new JsonException("Field not found: " + child.name() + " (" + type.getName() + ")");
			}
			Field field = metadata.field;
			try {
				field.set(object, readValue(field.getType(), metadata.elementType, child));
			} catch (IllegalAccessException ex) {
				throw new JsonException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
			} catch (JsonException ex) {
				ex.addTrace(field.getName() + " (" + type.getName() + ")");
				throw ex;
			} catch (RuntimeException runtimeEx) {
				JsonException ex = new JsonException(runtimeEx);
				ex.addTrace(field.getName() + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T readValue (String name, Class<T> type, JsonValue jsonMap) {
		return (T)readValue(type, null, jsonMap.get(name));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T readValue (String name, Class<T> type, T defaultValue, JsonValue jsonMap) {
		JsonValue jsonValue = jsonMap.get(name);
		if (jsonValue == null) return defaultValue;
		return (T)readValue(type, null, jsonValue);
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public <T> T readValue (String name, Class<T> type, Class elementType, JsonValue jsonMap) {
		return (T)readValue(type, elementType, jsonMap.get(name));
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public <T> T readValue (String name, Class<T> type, Class elementType, T defaultValue, JsonValue jsonMap) {
		JsonValue jsonValue = jsonMap.get(name);
		if (jsonValue == null) return defaultValue;
		return (T)readValue(type, elementType, jsonValue);
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public <T> T readValue (Class<T> type, Class elementType, T defaultValue, JsonValue jsonData) {
		return (T)readValue(type, elementType, jsonData);
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public <T> T readValue (Class<T> type, JsonValue jsonData) {
		return (T)readValue(type, null, jsonData);
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public <T> T readValue (Class<T> type, Class elementType, JsonValue jsonData) {
		if (jsonData == null) return null;

		if (jsonData.isObject()) {
			String className = typeName == null ? null : jsonData.getString(typeName, null);
			if (className != null) {
				jsonData.remove(typeName);
				type = getClass(className);
				if (type == null) {
					try {
						type = (Class<T>)Class.forName(className);
					} catch (ClassNotFoundException ex) {
						throw new JsonException(ex);
					}
				}
			}

			if (type == null) {
				if (defaultSerializer != null) return (T)defaultSerializer.read(this, jsonData, type);
				return (T)jsonData;
			}

			if (type == String.class || type == Integer.class || type == Boolean.class || type == Float.class || type == Long.class
				|| type == Double.class || type == Short.class || type == Byte.class || type == Character.class
				|| Enum.class.isAssignableFrom(type)) {
				return readValue("value", type, jsonData);
			}

			if (typeName != null && Collection.class.isAssignableFrom(type)) {
				// JSON object wrapper to specify type.
				jsonData = jsonData.get("items");
				if (jsonData == null)
					throw new JsonException("Unable to convert object to collection: " + jsonData + " (" + type.getName() + ")");
			} else {
				JsonSerializer serializer = classToSerializer.get(type);
				if (serializer != null) return (T)serializer.read(this, jsonData, type);

				Object object = newInstance(type);

				if (object instanceof JsonSerializable) {
					((JsonSerializable)object).read(this, jsonData);
					return (T)object;
				}

				// JSON object special cases.
				if (object instanceof ObjectMap) {
					ObjectMap result = (ObjectMap)object;
					for (JsonValue child = jsonData.child; child != null; child = child.next)
						result.put(child.name(), readValue(elementType, null, child));
					return (T)result;
				}
				if (object instanceof Map) {
					Map result = (Map)object;
					for (JsonValue child = jsonData.child; child != null; child = child.next)
						result.put(child.name(), readValue(elementType, null, child));
					return (T)result;
				}

				readFields(object, jsonData);
				return (T)object;
			}
		}

		if (type != null) {
			JsonSerializer serializer = classToSerializer.get(type);
			if (serializer != null) return (T)serializer.read(this, jsonData, type);

			if (JsonSerializable.class.isAssignableFrom(type)) {
				// A Serializable may be read as an array, string, etc, even though it will be written as an object.
				Object object = newInstance(type);
				((JsonSerializable)object).read(this, jsonData);
				return (T)object;
			}
		}

		if (jsonData.isArray()) {
			// JSON array special cases.
			if (type == null || type == Object.class) type = (Class<T>)ArrayList.class;
			if (Collection.class.isAssignableFrom(type)) {
				Collection result = type.isInterface() ? new ArrayList() : (Collection)newInstance(type);
				for (JsonValue child = jsonData.child; child != null; child = child.next)
					result.add(readValue(elementType, null, child));
				return (T)result;
			}
			if (type.isArray()) {
				Class componentType = type.getComponentType();
				if (elementType == null) elementType = componentType;
				Object result = Array.newInstance(componentType, jsonData.size);
				int i = 0;
				for (JsonValue child = jsonData.child; child != null; child = child.next)
					Array.set(result, i++, readValue(elementType, null, child));
				return (T)result;
			}
			throw new JsonException("Unable to convert value to required type: " + jsonData + " (" + type.getName() + ")");
		}

		if (jsonData.isNumber()) {
			try {
				if (type == null || type == float.class || type == Float.class) return (T)(Float)jsonData.asFloat();
				if (type == int.class || type == Integer.class) return (T)(Integer)jsonData.asInt();
				if (type == long.class || type == Long.class) return (T)(Long)jsonData.asLong();
				if (type == double.class || type == Double.class) return (T)(Double)jsonData.asDouble();
				if (type == String.class) return (T)jsonData.asString();
				if (type == short.class || type == Short.class) return (T)(Short)jsonData.asShort();
				if (type == byte.class || type == Byte.class) return (T)(Byte)jsonData.asByte();
			} catch (NumberFormatException ignored) {
			}
			jsonData = new JsonValue(jsonData.asString());
		}

		if (jsonData.isBoolean()) {
			try {
				if (type == null || type == boolean.class || type == Boolean.class) return (T)(Boolean)jsonData.asBoolean();
			} catch (NumberFormatException ignored) {
			}
			jsonData = new JsonValue(jsonData.asString());
		}

		if (jsonData.isString()) {
			String string = jsonData.asString();
			if (type == null || type == String.class) return (T)string;
			try {
				if (type == int.class || type == Integer.class) return (T)Integer.valueOf(string);
				if (type == float.class || type == Float.class) return (T)Float.valueOf(string);
				if (type == long.class || type == Long.class) return (T)Long.valueOf(string);
				if (type == double.class || type == Double.class) return (T)Double.valueOf(string);
				if (type == short.class || type == Short.class) return (T)Short.valueOf(string);
				if (type == byte.class || type == Byte.class) return (T)Byte.valueOf(string);
			} catch (NumberFormatException ignored) {
			}
			if (type == boolean.class || type == Boolean.class) return (T)Boolean.valueOf(string);
			if (type == char.class || type == Character.class) return (T)(Character)string.charAt(0);
			if (Enum.class.isAssignableFrom(type)) {
				Enum[] constants = (Enum[])type.getEnumConstants();
				for (int i = 0, n = constants.length; i < n; i++) {
					Enum e = constants[i];
					if (string.equals(convertToString(e))) return (T)e;
				}
			}
			if (type == CharSequence.class) return (T)string;
			throw new JsonException("Unable to convert value to required type: " + jsonData + " (" + type.getName() + ")");
		}

		return null;
	}

	private String convertToString (Enum e) {
		return enumNames ? e.name() : e.toString();
	}

	private String convertToString (Object object) {
		if (object instanceof Enum) return convertToString((Enum)object);
		if (object instanceof Class) return ((Class)object).getName();
		return String.valueOf(object);
	}

	protected Object newInstance (Class type) {
		try {
			return type.newInstance();
		} catch (Exception ex) {
			try {
				// Try a private constructor.
				Constructor constructor = type.getDeclaredConstructor();
				constructor.setAccessible(true);
				return constructor.newInstance();
			} catch (SecurityException ignored) {
			} catch (IllegalAccessException ignored) {
				if (Enum.class.isAssignableFrom(type)) {
					if (type.getEnumConstants() == null) type = type.getSuperclass();
					return type.getEnumConstants()[0];
				}
				if (type.isArray())
					throw new JsonException("Encountered JSON object when expected array of type: " + type.getName(), ex);
				else if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers()))
					throw new JsonException("Class cannot be created (non-static member class): " + type.getName(), ex);
				else
					throw new JsonException("Class cannot be created (missing no-arg constructor): " + type.getName(), ex);
			} catch (Exception privateConstructorException) {
				ex = privateConstructorException;
			}
			throw new JsonException("Error constructing instance of class: " + type.getName(), ex);
		}
	}

	public String prettyPrint (Object object) {
		return prettyPrint(object, 0);
	}

	public String prettyPrint (String json) {
		return prettyPrint(json, 0);
	}

	public String prettyPrint (Object object, int singleLineColumns) {
		return prettyPrint(toJson(object), singleLineColumns);
	}

	public String prettyPrint (String json, int singleLineColumns) {
		return new JsonReader().parse(json).prettyPrint(outputType, singleLineColumns);
	}

	public String prettyPrint (Object object, PrettyPrintSettings settings) {
		return prettyPrint(toJson(object), settings);
	}

	public String prettyPrint (String json, PrettyPrintSettings settings) {
		return new JsonReader().parse(json).prettyPrint(settings);
	}

	static private class FieldMetadata {
		Field field;
		Class elementType;

		public FieldMetadata (Field field) {
			this.field = field;

			Type genericType = field.getGenericType();
			if (genericType instanceof ParameterizedType) {
				Type[] actualTypes = ((ParameterizedType)genericType).getActualTypeArguments();
				Type actualType = null;
				if (actualTypes.length == 1)
					actualType = actualTypes[0];
				else if (actualTypes.length == 2 && Map.class.isAssignableFrom(field.getType())) //
					actualType = actualTypes[1];
				if (actualType != null) {
					if (actualType instanceof Class)
						elementType = (Class)actualType;
					else if (actualType instanceof ParameterizedType)
						elementType = (Class)((ParameterizedType)actualType).getRawType();
				}
			}
		}
	}
}
