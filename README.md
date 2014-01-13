## JsonBeans

Please use the [JsonBeans discussion group](http://groups.google.com/group/jsonbeans-users) for support.

- [Overview](#Overview)
- [Writing object graphs](#writing-object-graphs)
- [Reading object graphs](#reading-object-graphs)
- [Customizing serialization](#customizing-serialization)
- [Event based parsing](#event-based-parsing)

## Overview

JsonBeans is a lightweight library that makes it easy to serialize and deserialize Java object graphs to and from JSON. The JAR is 45k and has no dependencies. Four small classes make up the important parts of the library:

- `JsonWriter`: A builder style API for emitting JSON.
- `JsonReader`: Parses JSON and builds a DOM of `JsonValue` objects.
- `JsonValue`: Describes a JSON object, array, string, float, long, boolean, or null.
- `Json`: Reads and writes arbitrary object graphs using `JsonReader` and `JsonWriter`.

## Writing object graphs

The `Json` class uses reflection to automatically serialize objects to JSON. For example, here are two classes (getters/setters and constructors omitted):

```java
    public class Person {
       private String name;
       private int age;
       private ArrayList numbers;
    }
    
    public class PhoneNumber {
       private String name;
       private String number;
    }
```

Example object graph using these classes:

```java
    Person person = new Person();
    person.setName("Nate");
    person.setAge(31);
    ArrayList numbers = new ArrayList();
    numbers.add(new PhoneNumber("Home", "206-555-1234"));
    numbers.add(new PhoneNumber("Work", "425-555-4321"));
    person.setNumbers(numbers);
```

The JsonBeans code to serialize this object graph:

```java
    Json json = new Json();
    System.out.println(json.toJson(person));
```
```json {"numbers":[{"class":"com.example.PhoneNumber","name":"Home","number":"206-555-1234"},{"class":"com.example.PhoneNumber","name":"Work","number":"425-555-4321"}],"age":31,"name":"Nate"}
```

That is compact, but hardly legible. The `prettyPrint` method can be used:

```java
    Json json = new Json();
    System.out.println(json.prettyPrint(person));
```
```json
    {
    "name": "Nate",
    "age": 31,
    "numbers": [
       {
          "name": "Home",
          "class": "com.example.PhoneNumber",
          "number": "206-555-1234"
       },
       {
          "name": "Work",
          "class": "com.example.PhoneNumber",
          "number": "425-555-4321"
       }
    ]
    }
```

Note that the class for the `PhoneNumber` objects in the `ArrayList numbers` field appears in the JSON. This is required to recreate the object graph from the JSON because `ArrayList` can hold any type of object. Class names are only output when they are required for deserialization. If the field was `ArrayList<PhoneNumber> numbers` then class names would only appear when an item in the list extends `PhoneNumber`. If you know the concrete type or aren't using generics, you can avoid class names being written by telling the `Json` class the types:

```java
    Json json = new Json();
    json.setElementType(Person.class, "numbers", PhoneNumber.class);
    System.out.println(json.prettyPrint(person));
```
```json
    {
    "name": "Nate",
    "age": 31,
    "numbers": [
       {
          "name": "Home",
          "number": "206-555-1234"
       },
       {
          "name": "Work",
          "number": "425-555-4321"
       }
    ]
    }
```

When writing the class cannot be avoided, an alias can be given:

```java
    Json json = new Json();
    json.addClassTag("phoneNumber", PhoneNumber.class);
    System.out.println(json.prettyPrint(person));
```
```json
    {
    "name": "Nate",
    "age": 31,
    "numbers": [
       {
          "name": "Home",
          "class": "phoneNumber",
          "number": "206-555-1234"
       },
       {
          "name": "Work",
          "class": "phoneNumber",
          "number": "425-555-4321"
       }
    ]
    }
```

JsonBeans can write and read both JSON and a couple JSON-like formats. It supports "javascript", where the object property names are only quoted when needed. It also supports a "minimal" format, where both object property names and values are only quoted when needed.

```java
    Json json = new Json();
    json.setOutputType(OutputType.minimal);
    json.setElementType(Person.class, "numbers", PhoneNumber.class);
    System.out.println(json.prettyPrint(person));
```
```json
    {
    name: Nate,
    age: 31,
    numbers: [
       {
          name: Home,
          number: "206-555-1234"
       },
       {
          name: Work,
          number: "425-555-4321"
       }
    ]
    }
```

## Reading object graphs

The Json class uses reflection to automatically deserialize objects from JSON. Here is how to deserialize the JSON from the previous examples:

```java
    Json json = new Json();
    String text = json.toJson(person);
    Person person2 = json.fromJson(Person.class, text);
```

The type passed to `fromJson` is the type of the root of the object graph. From this, JsonBeans can determine the types of all the fields and all other objects encountered, recursively. The "knownType" and "elementType" of the root can be passed to `toJson`. This is useful if the type of the root object is not known:

```java
    Json json = new Json();
    json.setOutputType(OutputType.minimal);
    String text = json.toJson(person, Object.class);
    System.out.println(json.prettyPrint(text));
    Object person2 = json.fromJson(Object.class, text);
```
```json
    {
    class: com.example.Person,
    name: Nate,
    age: 31,
    numbers: [
       {
          name: Home,
          class: com.example.PhoneNumber,
          number: "206-555-1234"
       },
       {
          name: Work,
          class: com.example.PhoneNumber,
          number: "425-555-4321"
       }
    ]
    }
```

To read the JSON as a DOM of maps, arrays, and values, the `JsonReader` class can be used:

```java
    Json json = new Json();
    String text = json.toJson(person, Object.class);
    JsonValue root = new JsonReader().parse(text);
```

The `JsonValue` describes a JSON object, array, string, float, long, boolean, or null.

## Customizing serialization

Serialization can be customized by either having the class to be serialized implement the `Json.Serializable` interface, or by registering a `Json.Serializer` with the `Json` instance. This example writes the phone numbers as an object with a single field:

```java
    static public class PhoneNumber implements Json.Serializable {
       private String name;
       private String number;
    
       public void write (Json json) {
          json.writeValue(name, number);
       }
    
       public void read (Json json, JsonValue jsonMap) {
          name = jsonMap.child().name();
          number = jsonMap.child().asString();
       }
    }
    
    Json json = new Json();
    json.setElementType(Person.class, "numbers", PhoneNumber.class);
    String text = json.prettyPrint(person);
    System.out.println(text);
    Person person2 = json.fromJson(Person.class, text);
```
```json
    {
    "name": "Nate",
    "age": 31
    "numbers": [
       { 
          "Home": "206-555-1234"
       },
       {
          "Work": "425-555-4321"
       }
    ]
    }
```

In the `Json.Serializable` interface methods, the `Json` instance is given. It has many methods to read and write data to the JSON. When using `Json.Serializable`, the surrounding JSON object is handled automatically in the `write` method. This is why the `read` method always receives a `JsonMap`.

`Json.Serializer` provides more control over what is output, requiring `writeObjectStart` and `writeObjectEnd` to be called to achieve the same effect. A JSON array or a simple value could be output instead of an object. `Json.Serializer` also allows the object creation to be customized.

```java
    Json json = new Json();
    json.setSerializer(PhoneNumber.class, new Json.Serializer<PhoneNumber>() {
       public void write (Json json, PhoneNumber number, Class knownType) {
          json.writeObjectStart();
          json.writeValue(number.name, number.number);
          json.writeObjectEnd();
       }
    
       public PhoneNumber read (Json json, JsonValue jsonData, Class type) {
          PhoneNumber number = new PhoneNumber();
          number.setName(jsonData.child().name());
          number.setNumber(jsonData.child().asString());
          return number;
       }
    });
    json.setElementType(Person.class, "numbers", PhoneNumber.class);
    String text = json.prettyPrint(person);
    System.out.println(text);
    Person person2 = json.fromJson(Person.class, text);
```

## Event based parsing

The `JsonReader` class reads JSON and has protected methods that are called as JSON objects, arrays, strings, numbers, and booleans are encountered. By default, these methods build a DOM out of `JsonValue` objects. These methods can be overridden to do your own event based JSON handling.
