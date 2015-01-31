# Clojure vs Scala for High Performance Web API
A comparison of Clojure and Scala for implementing a web API.

## Table of Content

* [TL;DL](#TL;DR)
* [Intro](#Intro)
* [Goals](#Goals)
* [Building Web API](#Building-Web-Api)
    * [Scala](#Scala-Web-Api)

## TL;DR

Scala & Clojure run on the JVM and compete for a replacement for Java. Clojure wins because:

**Scala**

* functional + object oriented + actors + familiar syntax
* this seems great until you realize how complex it makes things.

**Clojure**

* functional + STM + LISP syntax
* seems strange until you do it a bit then you find solving problems with it is easy!

---------------

## Intro

I have a problem, and you may have the same one. My problem is that I have a legacy web service written in Java and I want a better way to develop while still being able to use my existing components. Currently the production level languages written specifically for the JVM are:

* Java
* Clojure
* Scala
* Groovy

Groovy was designed mainly for Grails which is a web MVC framework heavily inspired by Rails. As such we won't be examining it since our goal is not to emulate a full MVC framework since they often carry much more heft than necessary (yes Ruby developers, we know how long it takes your integration tests to run). Thus the two remaining langauges are Clojure and Scala.

## Goals

Our goals for this are to:

1. implement a basic API for a web application (ToDo List).
2. do this in a standard way that a production system would.
3. benchmark each.
4. compare code and performance of each.

## Building Web API

A few quick notes, all development is done on Fedora 21 on Rackspace Cloud.

First up, Scala!

### Scala

Scala was conceived, and still largely built to this day, by Martin Odersky. It is a langauge that lets you have it all in a sense. It is advertised, and accurately, that you can write both OO and Functional style code with it.

#### Getting Started

The best way to get started with Scala isn't the most obvious. We want to use [SBT](http://www.scala-sbt.org) (which I assume is short for Scala Build Tool but I'm couldn't actually find it documented any where). To install it we'll just add the rpm repo and do a yum install (note this will also intall Java):

```bash
$ curl https://bintray.com/sbt/rpm/rpm > bintray-sbt-rpm.repo
$ sudo mv bintray-sbt-rpm.repo /etc/yum.repos.d/
$ yum install sbt
```

for a more detailed explanation of installation look [here](http://www.scala-sbt.org/0.13/tutorial/index.html).

Now for project directory creation!

```bash
$ cd my_programming_dir
$ mkdir scala-api
$ cd scala-api
$ git init
$ echo "*target/" > .gitignore
$ mkdir {project,src}
$ mkdir -p src/main/scala/com/API
```

So now you should have:
```bash
$ tree
.
├── project
└── src
    └── main
        └── scala
            └── com
                └── API
```

This is the required setup for a Scala application using SBT (you can see the Java influence). Some things that have been omitted because we won't be dealing with them are `src/test` and `src/main/{java,resources}` but I think you get the idea.

After digging around some I have found that the two of the main frameworks for doing web routing in Scala are [Scalatra](http://www.scalatra.org/) and [Spray](http://spray.io/). Note that [Play](https://www.playframework.com/) is much more of a web framework like Rails and as such we're excluding it.

Scalatra is a Scala built version of Sinatra, a minimal easy to use web framework:
> Scalatra is a simple, accessible and free web micro-framework. It combines the power of the JVM with the beauty and brevity of Scala, helping you quickly build high-performance web sites and APIs.


Mean while Spray seems to sit on the opposite end of the spectrum being a library for constructing web frameworks:
> *spray* is an open-source toolkit for building REST/HTTP-based integration layers on top of Scala and Akka. Being asynchronous, actor-based, fast, lightweight, modular and testable it's a great way to connect your Scala applications to the world.

You'll notice the mention of [Akka](http://akka.io/). To continue my pattern of lazily ripping product descriptions from their home pages:
> Akka is a toolkit and runtime for building highly concurrent, distributed, and resilient message-driven applications on the JVM.

In essence you can think of Akka as the defacto library for concurrency via actors in Scala. It's a library that's so often used with Scala that it has a similar relation with concurrency as Rails for Ruby has for web frameworks.

For this project I've decided to go with Spray since it appears to be a much better performing solution (see [benchmark reference](http://spray.io/blog/2013-05-24-benchmarking-spray/)) and more flexible.

Now lets include Spray. We'll start with adding the file `built.sbt` to the `scala-api` project directory. It should read:

```scala
name := "Scala-Api"

version := "0.0.0"

scalaVersion := "2.10.4"

libraryDependencies ++= {
        val akkaV = "2.3.9"
        val sprayV = "1.3.2"
        Seq(
        "io.spray" %% "spray-can" % sprayV,
        "io.spray" %% "spray-routing" % sprayV,
        "com.typesafe.akka" %% "akka-actor" % akkaV,
        )
}
```

As you can tell there are some unusual symbols in here. That's because this file is straight Scala code. But you can see we include Spray and Akka from their respective repositories.

Now onto building the basics of our server. We'll start with a simple "hello world" example. I've taken this initial one from the [Spray Template Repo](https://github.com/spray/spray-template). Go to `scala-api/src/main/scala/com/api` and `$ touch boot.scala route.scala`. For `boot.scala` we'll add:

```scala
package com.API

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

object Boot extends App {

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("spray-actors")

  // create and start our service actor
  val service = system.actorOf(Props[MyServiceActor], "api")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 8080)
}
```

And for `route.scala` we'll add:

```scala
package com.API

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}


// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

  val myRoute =
    path("") {
      get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            "Welcome to <b>Spray</b>!"
          }
        }
      }
    }
}
```

Now at this point you'll notice that we have all of our code but we still haven't installed Scala! Not to fear, let SBT take handle it. Go to the main level of your dir (`$ cd scala-api`) and run `sbt run` (note, if you get a memory warning try adding the `-mem` flag followed by however many MB you can / want to give the JVM).

And if we navigate to the IP of our development machine on port 8080 we see "Welcome to **Spray**!". So now we just need to implement a basic todo list! It'll have the following interface:

* to get todo's: GET /api/todo/get_all
* to get a todo by id: GET /api/todo/get_id/[id]
* to add todo: POST /api/todo/create
* to delete todo: DELETE /api/todo/remove/[id]
