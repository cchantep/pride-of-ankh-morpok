package cchantep.auth

import scala.util.{ Failure, Success }

import scala.concurrent.Future

import play.api.libs.json.{ Json, JsObject, JsResult }

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.model.{
  HttpEntity,
  ContentTypes,
  HttpResponse,
  StatusCodes
}

final class Router(context: AppContext) {

  import akka.http.scaladsl.server.Directives._

  // See https://github.com/lomigmegard/akka-http-cors#quick-start
  import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  import context.executor // ExecutionContext

  val instance: Route =
    logRequest(s"${context.name}.router") {
      // See https://doc.akka.io/docs/akka-http/current/routing-dsl/overview.html

      val userIdSegment = Segment.map(UserId(_))

      cors() {
        {
          (post & path("signup") & entity(as[JsObject])) {
            signupRoute
          }
        } ~ {
          (get & path("users" / userIdSegment)) {
            getUserRoute
          }
        } ~ {
          (patch & path("users" / userIdSegment)) {
            patchUserRoute
          }
        } ~ {
          ((delete & path("users")) | (post & path("close"))) {
            deleteUser
          }
        }
      }
    }

  // ---

  private val signupRoute: JsObject => Route = { payload: JsObject =>
    JsResult.toTry(payload.validate[Account]) match {
      case Failure(cause) =>
        complete(
          badRequest(
            Json.obj(
              "message" -> "Account creation failed",
              "cause" -> cause.getMessage
            )
          )
        )

      case Success(account) =>
        complete(Future(mockDb.synchronized {
          mockDb.get(account.userId) match {
            case Some(_) =>
              badRequest(
                Json.obj(
                  "message" -> "Account creation failed",
                  "cause" -> "this user_id is already taken"
                )
              )

            case _ => {
              mockDb.put(account.userId, account)

              ok(
                Json.obj(
                  "message" -> "Account successfully created",
                  "user" -> account
                )
              )
            }
          }
        }))

    }
  }

  private val getUserRoute: UserId => Route = { userId =>
    if (!mockDb.contains(userId)) {
      // Not secure as allow to probe user DB
      complete(StatusCodes.NotFound -> Json.obj("message" -> "No user found"))
    } else {
      authenticateBasic(realm = "auth", Authenticator.of(userId)) { account =>
        val user = account.copy(nickname =
          account.nickname.orElse(Some(account.userId.value))
        )

        complete(
          Future.successful(
            Json.obj(
              "message" -> s"User details for ${userId.value}",
              "user" -> user
            )
          )
        )
      }
    }
  }

  private val patchUserRoute: UserId => Route = { userId =>
    authenticateBasic(realm = "auth", Authenticator.of(userId)) { account =>
      entity(as[AccountPatch]) { patch =>
        val updated = account.copy(
          password = patch.password.getOrElse(account.password),
          nickname = patch.nickname.orElse(account.nickname),
          comment = patch.comment.orElse(account.comment)
        )

        complete(Future(mockDb.synchronized {
          mockDb.put(userId, updated)

          Json
            .obj("message" -> "Account successfully patched", "user" -> updated)
        }))
      }
    }
  }

  private val deleteUser: Route =
    authenticateBasic(realm = "auth", Authenticator.strict) { account =>
      complete(Future(mockDb.synchronized {
        mockDb.remove(account.userId)

        Json.obj("message" -> "Account successfully closed", "user" -> account)
      }))
    }

  // ---

  private object Authenticator {
    def of(
        userId: UserId
      )(credentials: Credentials
      ): Option[Account] = {
      val expectedId = userId.value

      credentials match {
        case p @ Credentials.Provided(`expectedId`) =>
          mockDb.get(userId).filter { p verify _.password }

        case _ =>
          None
      }
    }

    def strict(credentials: Credentials): Option[Account] =
      credentials match {
        case p @ Credentials.Provided(id) =>
          mockDb.get(UserId(id)).filter { p verify _.password }

        case _ =>
          None
      }
  }

  private def badRequest(details: JsObject): HttpResponse =
    HttpResponse(
      StatusCodes.BadRequest,
      entity =
        HttpEntity(ContentTypes.`application/json`, Json.stringify(details))
    )

  private def ok(result: JsObject): HttpResponse =
    HttpResponse(
      StatusCodes.OK,
      entity =
        HttpEntity(ContentTypes.`application/json`, Json.stringify(result))
    )

  private lazy val mockDb = scala.collection.mutable.Map.empty[UserId, Account]
}
