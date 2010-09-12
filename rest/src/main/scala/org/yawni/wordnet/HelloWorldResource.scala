package org.yawni.wordnet

import javax.ws.rs._
import javax.ws.rs.core._

@Path("/helloworld")
class HelloWorldResource {
  //@Produces(Array(MediaType.TEXT_PLAIN))
  @GET
  def sayHello() = {
    "Hello, world!";
  }
}
