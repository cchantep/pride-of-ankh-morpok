package cchantep.giveryauth

import scala.util.{ Failure, Success, Try }

import scala.concurrent.Future

import play.api.libs.json.{ Json, JsObject, JsResult }

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }

final class Router(context: AppContext) {

  import akka.http.scaladsl.server.Directives._

  // See https://github.com/lomigmegard/akka-http-cors#quick-start
  import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

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
          (get & path("users" / userIdSegment)) { userId =>
            complete(Future.successful(Json.obj("fetch user" -> userId)))
          }
        } ~ {
          (patch & path("users" / userIdSegment)) { userId =>
            complete(Future.successful(Json.obj("patch user" -> userId)))
          }
        } ~ {
          (post & path("close")) {
            complete(Future.successful(Json.obj("close" -> 1)))
          }
        }
      }
    }

  // ---

  private val signupRoute: JsObject => Route = { payload: JsObject =>
    JsResult.toTry(payload.validate[Account]) match {
      case Failure(cause) =>
        complete(
          HttpResponse(
            StatusCodes.BadRequest,
            entity = Json.stringify(
              Json.obj(
                "message" -> "Account creation failed",
                "cause" -> cause.getMessage
              )
            )
          )
        )

      case Success(account) =>
        complete(Future.fromTry(Try(mockDb.synchronized {
          mockDb.get(account.userId) match {
            case Some(_) =>
              HttpResponse(
                StatusCodes.BadRequest,
                entity = Json.stringify(
                  Json.obj(
                    "message" -> "Account creation failed",
                    "cause" -> "this user_id is already taken"
                  )
                )
              )

            case _ => {
              mockDb.put(account.userId, account)

              HttpResponse(
                StatusCodes.OK,
                entity = Json.stringify(
                  Json.obj(
                    "message" -> "Account successfully created",
                    "user" -> account
                  )
                )
              )
            }
          }
        })))

    }
  }

  private lazy val mockDb = scala.collection.mutable.Map.empty[UserId, Account]
}
