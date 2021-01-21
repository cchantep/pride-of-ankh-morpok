package cchantep.giveryauth

import scala.concurrent.Future

import play.api.libs.json.Json

import akka.http.scaladsl.server.Route

final class Router(context: AppContext) {

  import akka.http.scaladsl.server.Directives._

  // See https://github.com/lomigmegard/akka-http-cors#quick-start
  import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  val instance: Route =
    logRequest(s"${context.name}.router") {
      // See https://doc.akka.io/docs/akka-http/current/routing-dsl/overview.html

      cors() {
        {
          (post & path("signup")) {
            complete(Future.successful(Json.obj("signup" -> 1)))
          }
        } ~ {
          (get & path("users" / Segment)) { userId =>
            complete(Future.successful(Json.obj("fetch user" -> userId)))
          }
        } ~ {
          (patch & path("users" / Segment)) { userId =>
            complete(Future.successful(Json.obj("patch user" -> userId)))
          }
        } ~ {
          (post & path("close")) {
            complete(Future.successful(Json.obj("close" -> 1)))
          }
        }
      }
    }
}
