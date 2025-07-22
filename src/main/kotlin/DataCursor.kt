package serial

@DslMarker
public annotation class CursorDSL

@CursorDSL
public interface DataCursor {
    public val offset: Long
    public val isEndOfInput: Boolean
    public fun advance()
    public fun crash(message: String, cause: Throwable? = null): Nothing

    public interface Source<S: Sink> {
        public fun fetch(into: S)
    }

    public interface Sink {
        public fun eoi()
        public fun crash(message: String, cause: Throwable? = null): Nothing
    }
}

public inline fun DataCursor.ensure(condition: Boolean, message: () -> String) {
    if (!condition) crash(message())
}

public fun DataCursor.eoi() {
    ensure(isEndOfInput) { "Expected end of input" }
}

public fun DataCursor.advanceBy(count: Int): Unit = repeat(count) { advance() }
