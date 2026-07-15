package io.worxbend.tui.examples.weather

/** A minimal JSON value model, parsed with an index-threaded recursive-descent reader (no shared mutable state). Just
  * enough to read the flat response shapes returned by the Open-Meteo APIs, without pulling in a JSON library
  * dependency for a single example app.
  */
enum Json:
  case JObject(fields: Map[String, Json])
  case JArray(items: Vector[Json])
  case JString(value: String)
  case JNumber(value: Double)
  case JBool(value: Boolean)
  case JNull

object Json:
  import scala.annotation.tailrec

  def parse(input: String): Either[String, Json] =
    parseValue(input, 0).flatMap { case (value, afterValue) =>
      val trailing = skipWhitespace(input, afterValue)
      if trailing == input.length then Right(value) else Left(s"unexpected trailing content at offset $trailing")
    }

  extension (json: Json)
    def field(name: String): Option[Json] = json match
      case JObject(fields) => fields.get(name)
      case _               => None

    def asString: Option[String] = json match
      case JString(value) => Some(value)
      case _              => None

    def asDouble: Option[Double] = json match
      case JNumber(value) => Some(value)
      case _              => None

    def asInt: Option[Int] = asDouble.map(_.toInt)

    def asBool: Option[Boolean] = json match
      case JBool(value) => Some(value)
      case _            => None

    def asArray: Option[Vector[Json]] = json match
      case JArray(items) => Some(items)
      case _             => None

  private def parseValue(s: String, i: Int): Either[String, (Json, Int)] =
    val p = skipWhitespace(s, i)
    if p >= s.length then Left(s"unexpected end of input at offset $p")
    else
      s.charAt(p) match
        case '{'                   => parseObject(s, p)
        case '['                   => parseArray(s, p)
        case '"'                   => parseString(s, p).map { case (value, next) => (JString(value), next) }
        case 't'                   => parseLiteral(s, p, "true", JBool(true))
        case 'f'                   => parseLiteral(s, p, "false", JBool(false))
        case 'n'                   => parseLiteral(s, p, "null", JNull)
        case c if isNumberStart(c) => parseNumber(s, p)
        case c                     => Left(s"unexpected character '$c' at offset $p")

  private def parseObject(s: String, open: Int): Either[String, (Json, Int)] =
    def loop(pos: Int, acc: Map[String, Json]): Either[String, (Json, Int)] =
      val p = skipWhitespace(s, pos)
      if p < s.length && s.charAt(p) == '}' then Right((JObject(acc), p + 1))
      else
        for
          (key, afterKey) <- parseString(s, p)
          colon = skipWhitespace(s, afterKey)
          _                 <- expect(s, colon, ':')
          (value, afterVal) <- parseValue(s, colon + 1)
          sep  = skipWhitespace(s, afterVal)
          next = acc + (key -> value)
          result <-
            if sep < s.length && s.charAt(sep) == ',' then loop(sep + 1, next)
            else if sep < s.length && s.charAt(sep) == '}' then Right((JObject(next), sep + 1))
            else Left(s"expected ',' or '}' at offset $sep")
        yield result
    loop(open + 1, Map.empty)

  private def parseArray(s: String, open: Int): Either[String, (Json, Int)] =
    def loop(pos: Int, acc: Vector[Json]): Either[String, (Json, Int)] =
      val p = skipWhitespace(s, pos)
      if p < s.length && s.charAt(p) == ']' then Right((JArray(acc), p + 1))
      else
        for
          (value, afterVal) <- parseValue(s, p)
          sep  = skipWhitespace(s, afterVal)
          next = acc :+ value
          result <-
            if sep < s.length && s.charAt(sep) == ',' then loop(sep + 1, next)
            else if sep < s.length && s.charAt(sep) == ']' then Right((JArray(next), sep + 1))
            else Left(s"expected ',' or ']' at offset $sep")
        yield result
    loop(open + 1, Vector.empty)

  private def parseString(s: String, quote: Int): Either[String, (String, Int)] =
    if quote >= s.length || s.charAt(quote) != '"' then Left(s"expected string at offset $quote")
    else
      @tailrec
      def loop(pos: Int, acc: StringBuilder): Either[String, (String, Int)] =
        if pos >= s.length then Left("unterminated string")
        else
          s.charAt(pos) match
            case '"'  => Right((acc.toString, pos + 1))
            case '\\' =>
              if pos + 1 >= s.length then Left("dangling escape at end of string")
              else
                s.charAt(pos + 1) match
                  case '"'   => loop(pos + 2, acc.append('"'))
                  case '\\'  => loop(pos + 2, acc.append('\\'))
                  case '/'   => loop(pos + 2, acc.append('/'))
                  case 'b'   => loop(pos + 2, acc.append('\b'))
                  case 'f'   => loop(pos + 2, acc.append('\f'))
                  case 'n'   => loop(pos + 2, acc.append('\n'))
                  case 'r'   => loop(pos + 2, acc.append('\r'))
                  case 't'   => loop(pos + 2, acc.append('\t'))
                  case 'u'   =>
                    if pos + 6 > s.length then Left("truncated unicode escape")
                    else
                      val codePoint =
                        try Some(Integer.parseInt(s.substring(pos + 2, pos + 6), 16))
                        catch case _: NumberFormatException => None
                      codePoint match
                        case Some(cp) => loop(pos + 6, acc.append(cp.toChar))
                        case None     => Left(s"invalid unicode escape at offset $pos")
                  case other => Left(s"invalid escape '\\$other' at offset $pos")
            case c    => loop(pos + 1, acc.append(c))
      loop(quote + 1, StringBuilder())

  private def parseNumber(s: String, start: Int): Either[String, (Json, Int)] =
    val end = numberEnd(s, start)
    s.substring(start, end).toDoubleOption match
      case Some(value) => Right((JNumber(value), end))
      case None        => Left(s"invalid number at offset $start")

  private def numberEnd(s: String, start: Int): Int =
    var i = start
    if i < s.length && s.charAt(i) == '-' then i += 1
    while i < s.length && (s.charAt(i).isDigit || "+-.eE".contains(s.charAt(i))) do i += 1
    i

  private def parseLiteral(s: String, start: Int, literal: String, value: Json): Either[String, (Json, Int)] =
    val end = start + literal.length
    if end <= s.length && s.substring(start, end) == literal then Right((value, end))
    else Left(s"expected '$literal' at offset $start")

  private def expect(s: String, pos: Int, ch: Char): Either[String, Unit] =
    if pos < s.length && s.charAt(pos) == ch then Right(()) else Left(s"expected '$ch' at offset $pos")

  private def isNumberStart(c: Char): Boolean = c == '-' || c.isDigit

  private def skipWhitespace(s: String, from: Int): Int =
    var i = from
    while i < s.length && s.charAt(i).isWhitespace do i += 1
    i
