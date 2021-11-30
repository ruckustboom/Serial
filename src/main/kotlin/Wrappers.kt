package serial

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

public fun InputStream.readOrThrow(): Int {
    val x = read()
    check(x in 0x00..0xFF) { "Reached end of stream" }
    return x
}

public inline fun <T : Any> InputStream.readNullable(readValue: InputStream.() -> T): T? =
    if (readBoolean()) readValue() else null

public inline fun <T : Any> OutputStream.writeNullable(value: T?, writeValue: OutputStream.(T) -> Unit) {
    writeBoolean(value != null)
    if (value != null) writeValue(value)
}

public inline fun InputStream.repeatRead(action: InputStream.(index: Int) -> Unit) {
    repeat(readInt()) { action(it) }
}

public inline fun OutputStream.repeatWrite(count: Int, action: OutputStream.(index: Int) -> Unit) {
    writeInt(count)
    repeat(count) { action(it) }
}

public inline fun <T> InputStream.readValues(readValue: InputStream.() -> T, action: (value: T) -> Unit) {
    repeatRead { action(readValue()) }
}

public inline fun <T, C : MutableCollection<T>> InputStream.readValuesTo(values: C, readValue: InputStream.() -> T): C {
    readValues(readValue) { values += it }
    return values
}

public inline fun <T> InputStream.readValues(readValue: InputStream.() -> T): List<T> = List(readInt()) { readValue() }
public inline fun <T> OutputStream.writeValues(list: Collection<T>, writeValue: OutputStream.(T) -> Unit) {
    writeInt(list.size)
    for (value in list) writeValue(value)
}

public inline fun <K, V> InputStream.readMap(
    readKey: InputStream.() -> K,
    readValue: InputStream.(K) -> V,
    action: (key: K, value: V) -> Unit,
): Unit = repeatRead {
    val key = readKey()
    val value = readValue(key)
    action(key, value)
}

public inline fun <K, V> InputStream.readMap(
    readKey: InputStream.() -> K,
    readValue: InputStream.(K) -> V,
    into: MutableMap<K, V> = mutableMapOf(),
): Map<K, V> {
    readMap(readKey, readValue) { key, value -> into[key] = value }
    return into
}

public inline fun <K, V> OutputStream.writeMap(
    map: Map<K, V>,
    writeEntry: OutputStream.(key: K, value: V) -> Unit,
) {
    writeInt(map.size)
    for ((key, value) in map) writeEntry(key, value)
}

public inline fun <K, V> OutputStream.writeMap(
    map: Map<K, V>,
    writeKey: OutputStream.(key: K) -> Unit,
    writeValue: OutputStream.(value: V) -> Unit,
): Unit = writeMap(map) { key, value ->
    writeKey(key)
    writeValue(value)
}

// Testing

public inline fun makeByteArray(action: OutputStream.() -> Unit): ByteArray = with(ByteArrayOutputStream()) {
    use(action)
    toByteArray()
}

public inline fun <T> ByteArray.asInputStream(action: InputStream.() -> T): T =
    ByteArrayInputStream(this).use(action)

public inline fun makeInputStream(action: OutputStream.() -> Unit): InputStream =
    ByteArrayInputStream(makeByteArray(action))

public inline fun <T> fullWriteAndRead(write: OutputStream.() -> Unit, read: InputStream.() -> T): T =
    makeByteArray(write).asInputStream(read)
