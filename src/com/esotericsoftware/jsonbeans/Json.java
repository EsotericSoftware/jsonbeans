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
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.esotericsoftware.jsonbeans.JsonValue.PrettyPrintSettings;
import com.esotericsoftware.jsonbeans.ObjectMap.Entry;

/** Reads/writes Java objects to/from JSON, automatically. See the wiki for usage:
 * https://github.com/libgdx/libgdx/wiki/Reading-and-writing-JSON
 * @author Nathan Sweet */
public class Json {
	static private final boolean debug = false;

	private JsonWriter writer;
	private String typeName = "class";
	private boolean usePrototypes = true;
	private OutputType outputType;
	private boolean quoteLongValues;
	private boolean ignoreUnknownFields;
	private boolean ignoreDeprecated;
	private boolean readDeprecated;
	private boolean enumNames = true;
	private boolean sortFields;
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

	public boolean getIgnoreUnknownFields () {
		return ignoreUnknownFields;
	}

	/** When true, fields with the {@link Deprecated} annotation will not be read or written. Default is false.
	 * @see #setReadDeprecated(boolean)
	 * @see #setDeprecated(Class, String, boolean) */
	public void setIgnoreDeprecated (boolean ignoreDeprecated) {
		this.ignoreDeprecated = ignoreDeprecated;
	}

	/** When true, fields with the {@link Deprecated} annotation will be read (but not written) when
	 * {@link #setIgnoreDeprecated(boolean)} is true. Default is false.
	 * @see #setDeprecated(Class, String, boolean) */
	public void setReadDeprecated (boolean readDeprecated) {
		this.readDeprecated = readDeprecated;
	}

	/** Default is {@link OutputType#minimal}.
	 * @see JsonWriter#setOutputType(OutputType) */
	public void setOutputType (OutputType outputType) {
		this.outputType = outputType;
	}

	/** Default is false.
	 * @see JsonWriter#setQuoteLongValues(boolean) */
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
	public @Null Class getClass (String tag) {
		return tagToClass.get(tag);
	}

	/** Returns the tag for the specified class, or null. */
	public @Null String getTag (Class type) {
		return classToTag.get(type);
	}

	/** Sets the name of the JSON field to store the Java class name or class tag when required to avoid ambiguity during
	 * deserialization. Set to null to never output this information, but be warned that deserialization may fail. Default is
	 * "class". */
	public void setTypeName (@Null String typeName) {
		this.typeName = typeName;
	}

	/** Sets the serializer to use when the type being deserialized is not known (null). */
	public void setDefaultSerializer (@Null JsonSerializer defaultSerializer) {
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
		FieldMetadata metadata = getFields(type).get(fieldName);
		if (metadata == null) throw new JsonException("Field not found: " + fieldName + " (" + type.getName() + ")");
		metadata.elementType = elementType;
	}

	/** The specified field will be treated as if it has or does not have the {@link Deprecated} annotation.
	 * @see #setIgnoreDeprecated(boolean)
	 * @see #setReadDeprecated(boolean) */
	public void setDeprecated (Class type, String fieldName, boolean deprecated) {
		FieldMetadata metadata = getFields(type).get(fieldName);
		if (metadata == null) throw new JsonException("Field not found: " + fieldName + " (" + type.getName() + ")");
		metadata.deprecated = deprecated;
	}

	/** When true, fields are sorted alphabetically when written, otherwise the source code order is used. Default is false. */
	public void setSortFields (boolean sortFields) {
		this.sortFields = sortFields;
	}

	private OrderedMap<String, FieldMetadata> getFields (Class type) {
		OrderedMap<String, FieldMetadata> fields = typeToFields.get(type);
		if (fields != null) return fields;

		ArrayList<Class> classHierarchy = new ArrayList();
		Class nextClass = type;
		while (nextClass != Object.class) {
			classHierarchy.add(nextClass);
			nextClass = nextClass.getSuperclass();
		}
		ArrayList<Field> allFields = new ArrayList();
		for (int i = classHierarchy.size() - 1; i >= 0; i--)
			Collections.addAll(allFields, classHierarchy.get(i).getDeclaredFields());

		OrderedMap<String, FieldMetadata> nameToField = new OrderedMap(allFields.size());
		for (int i = 0, n = allFields.size(); i < n; i++) {
			Field field = allFields.get(i);

			int modifiers = field.getModifiers();
			if (Modifier.isTransient(modifiers)) continue;
			if (Modifier.isStatic(modifiers)) continue;
			if (field.isSynthetic()) continue;

			if (!field.isAccessible()) {
				try {
					field.setAccessible(true);
				} catch (RuntimeException ex) {
					continue;
				}
			}

			nameToField.put(field.getName(), new FieldMetadata(field));
		}
		if (sortFields) Collections.sort(nameToField.keys);
		typeToFields.put(type, nameToField);
		return nameToField;
	}

	public String toJson (@Null Object object) {
		return toJson(object, object == null ? null : object.getClass(), (Class)null);
	}

	public String toJson (@Null Object object, @Null Class knownType) {
		return toJson(object, knownType, (Class)null);
	}

	/** @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public String toJson (@Null Object object, @Null Class knownType, @Null Class elementType) {
		StringWriter buffer = new StringWriter();
		toJson(object, knownType, elementType, buffer);
		return buffer.toString();
	}

	public void toJson (@Null Object object, File file) {
		toJson(object, object == null ? null : object.getClass(), null, file);
	}

	/** @param knownType May be null if the type is unknown. */
	public void toJson (@Null Object object, @Null Class knownType, File file) {
		toJson(object, knownType, null, file);
	}

	/** @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public void toJson (@Null Object object, @Null Class knownType, @Null Class elementType, File file) {
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

	public void toJson (@Null Object object, Writer writer) {
		toJson(object, object == null ? null : object.getClass(), null, writer);
	}

	/** @param knownType May be null if the type is unknown. */
	public void toJson (@Null Object object, @Null Class knownType, Writer writer) {
		toJson(object, knownType, null, writer);
	}

	/** @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public void toJson (@Null Object object, @Null Class knownType, @Null Class elementType, Writer writer) {
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
		int defaultIndex = 0;
		ArrayList<String> fieldNames = fields.orderedKeys();
		for (int i = 0, n = fieldNames.size(); i < n; i++) {
			FieldMetadata metadata = fields.get(fieldNames.get(i));
			if (ignoreDeprecated && metadata.deprecated) continue;
			Field field = metadata.field;
			try {
				Object value = field.get(object);
				if (defaultValues != null) {
					Object defaultValue = defaultValues[defaultIndex++];
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

	private @Null Object[] getDefaultValues (Class type) {
		if (!usePrototypes) return null;
		if (classToDefaultValues.containsKey(type)) return classToDefaultValues.get(type);
		Object object;
		try {
			object = newInstance(type);
		} catch (Exception ex) {
			classToDefaultValues.put(type, null);
			return null;
		}

		OrderedMap<String, FieldMetadata> fields = getFields(type);
		Object[] values = new Object[fields.size];
		classToDefaultValues.put(type, values);

		int defaultIndex = 0;
		ArrayList<String> fieldNames = fields.orderedKeys();
		for (int i = 0, n = fieldNames.size(); i < n; i++) {
			FieldMetadata metadata = fields.get(fieldNames.get(i));
			if (ignoreDeprecated && metadata.deprecated) continue;
			Field field = metadata.field;
			try {
				values[defaultIndex++] = field.get(object);
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
	public void writeField (Object object, String name, @Null Class elementType) {
		writeField(object, name, name, elementType);
	}

	/** @see #writeField(Object, String, String, Class) */
	public void writeField (Object object, String fieldName, String jsonName) {
		writeField(object, fieldName, jsonName, null);
	}

	/** Writes the specified field to the current JSON object.
	 * @param elementType May be null if the type is unknown. */
	public void writeField (Object object, String fieldName, String jsonName, @Null Class elementType) {
		Class type = object.getClass();
		FieldMetadata metadata = getFields(type).get(fieldName);
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
	public void writeValue (String name, @Null Object value) {
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
	public void writeValue (String name, @Null Object value, @Null Class knownType) {
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
	public void writeValue (String name, @Null Object value, @Null Class knownType, @Null Class elementType) {
		try {
			writer.name(name);
		} catch (IOException ex) {
			throw new JsonException(ex);
		}
		writeValue(value, knownType, elementType);
	}

	/** Writes the value, without writing the class of the object.
	 * @param value May be null. */
	public void writeValue (@Null Object value) {
		if (value == null)
			writeValue(value, null, null);
		else
			writeValue(value, value.getClass(), null);
	}

	/** Writes the value, writing the class of the object if it differs from the specified known type.
	 * @param value May be null.
	 * @param knownType May be null if the type is unknown. */
	public void writeValue (@Null Object value, @Null Class knownType) {
		writeValue(value, knownType, null);
	}

	/** Writes the value, writing the class of the object if it differs from the specified known type. The specified element type
	 * is used as the default type for collections.
	 * @param value May be null.
	 * @param knownType May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown. */
	public void writeValue (@Null Object value, @Null Class knownType, @Null Class elementType) {
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
					throw new JsonException("Serialization of an Array other than the known type is not supported.\n" //
						+ "Known type: " + knownType + "\nActual type: " + actualType);
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
	public void writeObjectStart (String name, Class actualType, @Null Class knownType) {
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
	public void writeObjectStart (Class actualType, @Null Class knownType) {
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
	public @Null <T> T fromJson (Class<T> type, Reader reader) {
		return readValue(type, null, new JsonReader().parse(reader));
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, Class elementType, Reader reader) {
		return readValue(type, elementType, new JsonReader().parse(reader));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, InputStream input) {
		return readValue(type, null, new JsonReader().parse(input));
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, Class elementType, InputStream input) {
		return readValue(type, elementType, new JsonReader().parse(input));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, File file) {
		try {
			return readValue(type, null, new JsonReader().parse(file));
		} catch (Exception ex) {
			throw new JsonException("Error reading file: " + file, ex);
		}
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, Class elementType, File file) {
		try {
			return readValue(type, elementType, new JsonReader().parse(file));
		} catch (Exception ex) {
			throw new JsonException("Error reading file: " + file, ex);
		}
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, char[] data, int offset, int length) {
		return readValue(type, null, new JsonReader().parse(data, offset, length));
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, Class elementType, char[] data, int offset, int length) {
		return readValue(type, elementType, new JsonReader().parse(data, offset, length));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, String json) {
		return readValue(type, null, new JsonReader().parse(json));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T fromJson (Class<T> type, Class elementType, String json) {
		return readValue(type, elementType, new JsonReader().parse(json));
	}

	public void readField (Object object, String name, JsonValue jsonData) {
		readField(object, name, name, null, jsonData);
	}

	public void readField (Object object, String name, @Null Class elementType, JsonValue jsonData) {
		readField(object, name, name, elementType, jsonData);
	}

	public void readField (Object object, String fieldName, String jsonName, JsonValue jsonData) {
		readField(object, fieldName, jsonName, null, jsonData);
	}

	/** @param elementType May be null if the type is unknown. */
	public void readField (Object object, String fieldName, String jsonName, @Null Class elementType, JsonValue jsonMap) {
		Class type = object.getClass();
		FieldMetadata metadata = getFields(type).get(fieldName);
		if (metadata == null) throw new JsonException("Field not found: " + fieldName + " (" + type.getName() + ")");
		Field field = metadata.field;
		if (elementType == null) elementType = metadata.elementType;
		readField(object, field, jsonName, elementType, jsonMap);
	}

	/** @param object May be null if the field is static.
	 * @param elementType May be null if the type is unknown. */
	public void readField (@Null Object object, Field field, String jsonName, @Null Class elementType, JsonValue jsonMap) {
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
			ex.addTrace(jsonValue.trace());
			ex.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
			throw ex;
		}
	}

	public void readFields (Object object, JsonValue jsonMap) {
		Class type = object.getClass();
		OrderedMap<String, FieldMetadata> fields = getFields(type);
		for (JsonValue child = jsonMap.child; child != null; child = child.next) {
			FieldMetadata metadata = fields.get(child.name().replace(" ", "_"));
			if (metadata == null) {
				if (child.name.equals(typeName)) continue;
				if (ignoreUnknownFields || ignoreUnknownField(type, child.name)) {
					if (debug) System.out.println("Ignoring unknown field: " + child.name + " (" + type.getName() + ")");
					continue;
				} else {
					JsonException ex = new JsonException("Field not found: " + child.name + " (" + type.getName() + ")");
					ex.addTrace(child.trace());
					throw ex;
				}
			} else {
				if (ignoreDeprecated && !readDeprecated && metadata.deprecated) continue;
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
				ex.addTrace(child.trace());
				ex.addTrace(field.getName() + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	/** Called for each unknown field name encountered by {@link #readFields(Object, JsonValue)} when {@link #ignoreUnknownFields}
	 * is false to determine whether the unknown field name should be ignored.
	 * @param type The object type being read.
	 * @param fieldName A field name encountered in the JSON for which there is no matching class field.
	 * @return true if the field name should be ignored and an exception won't be thrown by
	 *         {@link #readFields(Object, JsonValue)}. */
	protected boolean ignoreUnknownField (Class type, String fieldName) {
		return false;
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T readValue (String name, @Null Class<T> type, JsonValue jsonMap) {
		return readValue(type, null, jsonMap.get(name));
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T readValue (String name, @Null Class<T> type, T defaultValue, JsonValue jsonMap) {
		JsonValue jsonValue = jsonMap.get(name);
		if (jsonValue == null) return defaultValue;
		return readValue(type, null, jsonValue);
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T readValue (String name, @Null Class<T> type, @Null Class elementType, JsonValue jsonMap) {
		return readValue(type, elementType, jsonMap.get(name));
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T readValue (String name, @Null Class<T> type, @Null Class elementType, T defaultValue, JsonValue jsonMap) {
		JsonValue jsonValue = jsonMap.get(name);
		return readValue(type, elementType, defaultValue, jsonValue);
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T readValue (@Null Class<T> type, @Null Class elementType, T defaultValue, JsonValue jsonData) {
		if (jsonData == null) return defaultValue;
		return readValue(type, elementType, jsonData);
	}

	/** @param type May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T readValue (@Null Class<T> type, JsonValue jsonData) {
		return readValue(type, null, jsonData);
	}

	/** @param type May be null if the type is unknown.
	 * @param elementType May be null if the type is unknown.
	 * @return May be null. */
	public @Null <T> T readValue (@Null Class<T> type, @Null Class elementType, JsonValue jsonData) {
		if (jsonData == null) return null;

		if (jsonData.isObject()) {
			String className = typeName == null ? null : jsonData.getString(typeName, null);
			if (className != null) {
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

			if (typeName != null && Collection.class.isAssignableFrom(type)) {
				// JSON object wrapper to specify type.
				jsonData = jsonData.get("items");
				if (jsonData == null)
					throw new JsonException("Unable to convert object to collection: " + jsonData + " (" + type.getName() + ")");
			} else {
				JsonSerializer serializer = classToSerializer.get(type);
				if (serializer != null) return (T)serializer.read(this, jsonData, type);

				if (type == String.class || type == Integer.class || type == Boolean.class || type == Float.class
					|| type == Long.class || type == Double.class || type == Short.class || type == Byte.class
					|| type == Character.class || Enum.class.isAssignableFrom(type)) {
					return readValue("value", type, jsonData);
				}

				Object object = newInstance(type);

				if (object instanceof JsonSerializable) {
					((JsonSerializable)object).read(this, jsonData);
					return (T)object;
				}

				// JSON object special cases.
				if (object instanceof ObjectMap) {
					ObjectMap result = (ObjectMap)object;
					for (JsonValue child = jsonData.child; child != null; child = child.next)
						result.put(child.name, readValue(elementType, null, child));
					return (T)result;
				}
				if (object instanceof Map) {
					Map result = (Map)object;
					for (JsonValue child = jsonData.child; child != null; child = child.next) {
						if (child.name.equals(typeName)) continue;
						result.put(child.name, readValue(elementType, null, child));
					}
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

	/** Each field on the <code>to</code> object is set to the value for the field with the same name on the <code>from</code>
	 * object. The <code>to</code> object must have at least all the fields of the <code>from</code> object with the same name and
	 * type. */
	public void copyFields (Object from, Object to) {
		OrderedMap<String, FieldMetadata> toFields = getFields(to.getClass());
		for (ObjectMap.Entry<String, FieldMetadata> entry : getFields(from.getClass())) {
			FieldMetadata toField = toFields.get(entry.key);
			Field fromField = entry.value.field;
			if (toField == null) throw new JsonException("To object is missing field: " + entry.key);
			try {
				toField.field.set(to, fromField.get(from));
			} catch (IllegalAccessException ex) {
				throw new JsonException("Error copying field: " + fromField.getName(), ex);
			}
		}
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

	public String prettyPrint (@Null Object object) {
		return prettyPrint(object, 0);
	}

	public String prettyPrint (String json) {
		return prettyPrint(json, 0);
	}

	public String prettyPrint (@Null Object object, int singleLineColumns) {
		return prettyPrint(toJson(object), singleLineColumns);
	}

	public String prettyPrint (String json, int singleLineColumns) {
		return new JsonReader().parse(json).prettyPrint(outputType, singleLineColumns);
	}

	public String prettyPrint (@Null Object object, PrettyPrintSettings settings) {
		return prettyPrint(toJson(object), settings);
	}

	public String prettyPrint (String json, PrettyPrintSettings settings) {
		return new JsonReader().parse(json).prettyPrint(settings);
	}

	static private class FieldMetadata {
		final Field field;
		Class elementType;
		boolean deprecated;

		public FieldMetadata (Field field) {
			this.field = field;
			int index = (ObjectMap.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType())) ? 1 : 0;
			this.elementType = getElementType(field, index);
			deprecated = field.isAnnotationPresent(Deprecated.class);
		}

		/** If the type of the field is parameterized, returns the Class object representing the parameter type at the specified
		 * index, null otherwise. */
		static private @Null Class getElementType (Field field, int index) {
			Type genericType = field.getGenericType();
			if (genericType instanceof ParameterizedType) {
				Type[] actualTypes = ((ParameterizedType)genericType).getActualTypeArguments();
				if (actualTypes.length - 1 >= index) {
					Type actualType = actualTypes[index];
					if (actualType instanceof Class)
						return (Class)actualType;
					else if (actualType instanceof ParameterizedType)
						return (Class)((ParameterizedType)actualType).getRawType();
					else if (actualType instanceof GenericArrayType) {
						Type componentType = ((GenericArrayType)actualType).getGenericComponentType();
						if (componentType instanceof Class) return Array.newInstance((Class)componentType, 0).getClass();
					}
				}
			}
			return null;
		}
	}
}
