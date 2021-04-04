
package com.esotericsoftware.jsonbeans;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/** An {@link ObjectMap} that also stores keys in an {@link ArrayList} using the insertion order. Null keys are not allowed. No
 * allocation is done except when growing the table size.
 * <p>
 * Iteration over the {@link #entries()}, {@link #keys()}, and {@link #values()} is ordered and faster than an unordered map. Keys
 * can also be accessed and the order changed using {@link #orderedKeys()}. There is some additional overhead for put and remove.
 * <p>
 * This class performs fast contains (typically O(1), worst case O(n) but that is rare in practice). Remove is somewhat slower due
 * to {@link #orderedKeys()}. Add may be slightly slower, depending on hash collisions. Hashcodes are rehashed to reduce
 * collisions and the need to resize. Load factors greater than 0.91 greatly increase the chances to resize to the next higher POT
 * size.
 * <p>
 * Unordered sets and maps are not designed to provide especially fast iteration. Iteration is faster with OrderedSet and
 * OrderedMap.
 * <p>
 * This implementation uses linear probing with the backward shift algorithm for removal. Hashcodes are rehashed using Fibonacci
 * hashing, instead of the more common power-of-two mask, to better distribute poor hashCodes (see <a href=
 * "https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/">Malte
 * Skarupke's blog post</a>). Linear probing continues to work even when all hashCodes collide, just more slowly.
 * @author Nathan Sweet
 * @author Tommy Ettinger */
public class OrderedMap<K, V> extends ObjectMap<K, V> {
	final ArrayList<K> keys;

	public OrderedMap () {
		keys = new ArrayList();
	}

	public OrderedMap (int initialCapacity) {
		super(initialCapacity);
		keys = new ArrayList(initialCapacity);
	}

	public OrderedMap (int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor);
		keys = new ArrayList(initialCapacity);
	}

	public OrderedMap (OrderedMap<? extends K, ? extends V> map) {
		super(map);
		keys = new ArrayList(map.keys);
	}

	public V put (K key, V value) {
		int i = locateKey(key);
		if (i >= 0) { // Existing key was found.
			V oldValue = valueTable[i];
			valueTable[i] = value;
			return oldValue;
		}
		i = -(i + 1); // Empty space was found.
		keyTable[i] = key;
		valueTable[i] = value;
		keys.add(key);
		if (++size >= threshold) resize(keyTable.length << 1);
		return null;
	}

	public <T extends K> void putAll (OrderedMap<T, ? extends V> map) {
		ensureCapacity(map.size);
		ArrayList<T> keys = map.keys;
		for (int i = 0, n = map.keys.size(); i < n; i++) {
			K key = keys.get(i);
			put(key, map.get((T)key));
		}
	}

	public V remove (K key) {
		keys.remove(key);
		return super.remove(key);
	}

	public V removeIndex (int index) {
		return super.remove(keys.remove(index));
	}

	/** Changes the key {@code before} to {@code after} without changing its position in the order or its value. Returns true if
	 * {@code after} has been added to the OrderedMap and {@code before} has been removed; returns false if {@code after} is
	 * already present or {@code before} is not present. If you are iterating over an OrderedMap and have an index, you should
	 * prefer {@link #alterIndex(int, Object)}, which doesn't need to search for an index like this does and so can be faster.
	 * @param before a key that must be present for this to succeed
	 * @param after a key that must not be in this map for this to succeed
	 * @return true if {@code before} was removed and {@code after} was added, false otherwise */
	public boolean alter (K before, K after) {
		if (containsKey(after)) return false;
		int index = keys.indexOf(before);
		if (index == -1) return false;
		super.put(after, super.remove(before));
		keys.set(index, after);
		return true;
	}

	/** Changes the key at the given {@code index} in the order to {@code after}, without changing the ordering of other entries or
	 * any values. If {@code after} is already present, this returns false; it will also return false if {@code index} is invalid
	 * for the size of this map. Otherwise, it returns true. Unlike {@link #alter(Object, Object)}, this operates in constant time.
	 * @param index the index in the order of the key to change; must be non-negative and less than {@link #size}
	 * @param after the key that will replace the contents at {@code index}; this key must not be present for this to succeed
	 * @return true if {@code after} successfully replaced the key at {@code index}, false otherwise */
	public boolean alterIndex (int index, K after) {
		if (index < 0 || index >= size || containsKey(after)) return false;
		super.put(after, super.remove(keys.get(index)));
		keys.set(index, after);
		return true;
	}

	public void clear (int maximumCapacity) {
		keys.clear();
		super.clear(maximumCapacity);
	}

	public void clear () {
		keys.clear();
		super.clear();
	}

	public ArrayList<K> orderedKeys () {
		return keys;
	}

	public Entries<K, V> iterator () {
		return entries();
	}

	/** Returns an iterator for the entries in the map. Remove is supported. */
	public Entries<K, V> entries () {
		return new OrderedMapEntries(this);
	}

	/** Returns an iterator for the values in the map. Remove is supported. */
	public Values<V> values () {
		return new OrderedMapValues(this);
	}

	/** Returns an iterator for the keys in the map. Remove is supported. */
	public Keys<K> keys () {
		return new OrderedMapKeys(this);
	}

	public String toString () {
		if (size == 0) return "{}";
		java.lang.StringBuilder buffer = new java.lang.StringBuilder(32);
		buffer.append('{');
		ArrayList<K> keys = this.keys;
		for (int i = 0, n = keys.size(); i < n; i++) {
			K key = keys.get(i);
			if (i > 0) buffer.append(", ");
			buffer.append(key);
			buffer.append('=');
			buffer.append(get(key));
		}
		buffer.append('}');
		return buffer.toString();
	}

	static public class OrderedMapEntries<K, V> extends Entries<K, V> {
		private ArrayList<K> keys;

		public OrderedMapEntries (OrderedMap<K, V> map) {
			super(map);
			keys = map.keys;
		}

		public void reset () {
			currentIndex = -1;
			nextIndex = 0;
			hasNext = map.size > 0;
		}

		public Entry next () {
			if (!hasNext) throw new NoSuchElementException();
			currentIndex = nextIndex;
			entry.key = keys.get(nextIndex);
			entry.value = map.get(entry.key);
			nextIndex++;
			hasNext = nextIndex < map.size;
			return entry;
		}

		public void remove () {
			if (currentIndex < 0) throw new IllegalStateException("next must be called before remove.");
			map.remove(entry.key);
			nextIndex--;
			currentIndex = -1;
		}
	}

	static public class OrderedMapKeys<K> extends Keys<K> {
		private ArrayList<K> keys;

		public OrderedMapKeys (OrderedMap<K, ?> map) {
			super(map);
			keys = map.keys;
		}

		public void reset () {
			currentIndex = -1;
			nextIndex = 0;
			hasNext = map.size > 0;
		}

		public K next () {
			if (!hasNext) throw new NoSuchElementException();
			K key = keys.get(nextIndex);
			currentIndex = nextIndex;
			nextIndex++;
			hasNext = nextIndex < map.size;
			return key;
		}

		public void remove () {
			if (currentIndex < 0) throw new IllegalStateException("next must be called before remove.");
			((OrderedMap)map).removeIndex(currentIndex);
			nextIndex = currentIndex;
			currentIndex = -1;
		}
	}

	static public class OrderedMapValues<V> extends Values<V> {
		private ArrayList keys;

		public OrderedMapValues (OrderedMap<?, V> map) {
			super(map);
			keys = map.keys;
		}

		public void reset () {
			currentIndex = -1;
			nextIndex = 0;
			hasNext = map.size > 0;
		}

		public V next () {
			if (!hasNext) throw new NoSuchElementException();
			V value = map.get(keys.get(nextIndex));
			currentIndex = nextIndex;
			nextIndex++;
			hasNext = nextIndex < map.size;
			return value;
		}

		public void remove () {
			if (currentIndex < 0) throw new IllegalStateException("next must be called before remove.");
			((OrderedMap)map).removeIndex(currentIndex);
			nextIndex = currentIndex;
			currentIndex = -1;
		}

		public <T extends List<V>> T toList (T array) {
			int n = keys.size();
			for (int i = nextIndex; i < n; i++)
				array.add(map.get(keys.get(i)));
			currentIndex = n - 1;
			nextIndex = n;
			hasNext = false;
			return array;
		}

		public ArrayList<V> toList () {
			return toList(new ArrayList(keys.size() - nextIndex));
		}
	}
}
