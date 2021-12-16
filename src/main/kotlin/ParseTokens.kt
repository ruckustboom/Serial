package serial

import java.io.InputStream
import java.io.Reader

// Generic

public fun <T, R> ParseState<T>.tokenize(
    parseToken: ParseState<T>.() -> R,
): ParseState<R> = object : Iterator<R> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()

public fun <T> TextParseState.tokenize(
    parseToken: TextParseState.() -> T,
): ParseState<T> = object : Iterator<T> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()

public fun <T> BinaryParseState.tokenize(
    parseToken: BinaryParseState.() -> T,
): ParseState<T> = object : Iterator<T> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()

// Text

public fun <T> ParseState<T>.textTokenize(
    parseToken: ParseState<T>.() -> Char,
): TextParseState = object : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var i = 0
        while (i < len) {
            try {
                cbuf[off + i] = parseToken()
            } catch (e: Throwable) {
                break
            }
            i++
        }
        return i
    }

    override fun close() {}
}.initParse()

public fun TextParseState.textTokenize(
    parseToken: TextParseState.() -> Char,
): TextParseState = object : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var i = 0
        while (i < len) {
            try {
                cbuf[off + i] = parseToken()
            } catch (e: Throwable) {
                break
            }
            i++
        }
        return i
    }

    override fun close() {}
}.initParse()

public fun BinaryParseState.textTokenize(
    parseToken: BinaryParseState.() -> Char,
): TextParseState = object : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var i = 0
        while (i < len) {
            try {
                cbuf[off + i] = parseToken()
            } catch (e: Throwable) {
                break
            }
            i++
        }
        return i
    }

    override fun close() {}
}.initParse()

// Binary

public fun <T> ParseState<T>.binaryTokenize(
    parseToken: ParseState<T>.() -> Byte,
): BinaryParseState = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.initParse()

public fun TextParseState.binaryTokenize(
    parseToken: TextParseState.() -> Byte,
): BinaryParseState = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.initParse()

public fun BinaryParseState.binaryTokenize(
    parseToken: BinaryParseState.() -> Byte,
): BinaryParseState = object : InputStream() {
    override fun read() = try {
        parseToken().toInt() and 0xFF
    } catch (e: Throwable) {
        -1
    }
}.initParse()
