package ecdc.api

import play.api.mvc.{ Controller, Action }

class StatusController extends Controller {

  def getStatus = Action {
    Ok(s"looks good.\n")
  }
}
