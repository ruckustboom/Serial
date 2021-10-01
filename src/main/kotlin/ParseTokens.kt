package serial

public fun <T> TextParseState.tokenize(
    parseToken: TextParseState.() -> T
): ParseState<T> = object : Iterator<T> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()

public fun <T> BinaryParseState.tokenize(
    parseToken: BinaryParseState.() -> T
): ParseState<T> = object : Iterator<T> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()

public fun <T, R> ParseState<T>.tokenize(
    parseToken: ParseState<T>.() -> R
): ParseState<R> = object : Iterator<R> {
    override fun hasNext() = !isEndOfInput
    override fun next() = parseToken()
}.initParse()
