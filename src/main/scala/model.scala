package cchantep.auth

import play.api.libs.json.{
  Json,
  JsonConfiguration,
  JsonNaming,
  Format,
  OFormat,
  Reads
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
  implicit val firlat: OFormat[Account] = JsonUtils.facade.format
}

// ---

case class AccountPatch(
    password: Option[String],
    nickname: Option[String],
    comment: Option[String])

object AccountPatch {
  implicit val reads: Reads[AccountPatch] = JsonUtils.facade.reads
}

// ---

object JsonUtils {
  lazy val facade =
    Json.configured(JsonConfiguration(naming = JsonNaming.SnakeCase))
}
