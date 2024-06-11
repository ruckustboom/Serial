package serial

@DslMarker
public annotation class CursorDSL

@CursorDSL
public interface DataCursor {
    public val offset: Long
    public val isEndOfInput: Boolean
    public fun advance()

    public interface Handler {
        public fun eoi()
    }

    public interface Source<T: Handler> {
        public fun fetch(handler: T)
    }
}
