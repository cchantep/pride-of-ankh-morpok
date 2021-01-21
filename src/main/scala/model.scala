package cchantep.giveryauth

import play.api.libs.json.{
  Json,
  JsonConfiguration,
  JsonNaming,
  Format,
  OFormat
}

final class UserId(val value: String) extends AnyVal {
  @inline override def toString = value
}

object UserId {
  def apply(id: String): UserId = new UserId(id)

  implicit val format: Format[UserId] = Json.valueFormat
}

// ---

/** Account payload */
case class Account(
    userId: UserId,
    password: String,
    nickname: Option[String],
    comment: Option[String])

object Account {
  implicit val reads: OFormat[Account] = JsonUtils.facade.format
}

// ---

object JsonUtils {
  lazy val facade =
    Json.configured(JsonConfiguration(naming = JsonNaming.SnakeCase))
}
