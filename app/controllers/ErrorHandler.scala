package controllers

import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent._

class ErrorHandler extends HttpErrorHandler {

  def onClientError(request: RequestHeader, status: Int, message: String) = {
    Future.successful {
      Status(status)(if (!message.isEmpty) s"$status: $message\n" else s"$status\n")
    }
  }

  def onServerError(request: RequestHeader, exception: Throwable) = {
    Future.successful(
      InternalServerError(s"A server error occurred: $exception: ${exception.getMessage}\n")
    )
  }
}
