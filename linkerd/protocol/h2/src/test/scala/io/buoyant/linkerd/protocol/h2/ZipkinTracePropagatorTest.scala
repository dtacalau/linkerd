package io.buoyant.linkerd.protocol.h2

import com.twitter.finagle.buoyant.Sampler
import com.twitter.util.{Await, Future}
import org.scalatest.FunSuite
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Service
import com.twitter.finagle.tracing.Trace
import com.twitter.finagle.buoyant.h2.{Headers, Request, Status}
import com.twitter.finagle.buoyant.h2._
import io.buoyant.router.RoutingFactory._
import io.buoyant.linkerd.protocol.http._
import io.buoyant.router.H2Instances._

class ZipkinTracePropagatorTest extends FunSuite {

  test("get traceid from multi x-b3 headers, 64bit trace id, sampled, lower/UPPER/mIxEd case header doesn't matter") {
    val ztp = new ZipkinTracePropagator

    //x-b3 multi headers with lower case
    val req1 = Request("http", Method.Get, "auf", "/", Stream.empty())
    req1.headers.add("x-b3-traceid", "80f198ee56343ba8")
    req1.headers.add("x-b3-Spanid", "05e3ac9a4f6e3b90")
    req1.headers.add("x-b3-SAmpled", "1")
    req1.headers.add("x-b3-PARentspanid", "e457b5a2e4d86bd1")
    val trace1 = ztp.traceId(req1)
    // test multi x-b3 headers have been removed after traceId call, lower case, mixed case
    assert(req1.headers.toSeq == Seq((":scheme","http"), (":method","GET"), (":authority","auf"), (":path","/")))

    //X-B3 multi heders with upper case
    val req2 = Request("http", Method.Get, "auf", "/", Stream.empty())
    req2.headers.add("X-B3-TRACEID", "80f198ee56343ba8")
    req2.headers.add("X-B3-sPANID", "05e3ac9a4f6e3b90")
    req2.headers.add("X-B3-saMPLED", "1")
    req2.headers.add("X-B3-parENTSPANID", "e457b5a2e4d86bd1")
    val trace2 = ztp.traceId(req2)
    // test multi x-b3 headers have been removed after traceId call, upper case, mixed case
    assert(req2.headers.toSeq == Seq((":scheme","http"), (":method","GET"), (":authority","auf"), (":path","/")))

    // traceid is the same, lower/upper case headers don't matter
    assert(trace1 == trace2)

    assert(trace1.isDefined) //expect trace exists and has the expected values
    trace1.foreach { tid => {
      assert(tid.traceId.toString().equals("80f198ee56343ba8"))
      assert(tid.spanId.toString().equals("05e3ac9a4f6e3b90"))
      assert(tid.parentId.toString().equals("e457b5a2e4d86bd1"))
      assert(tid.sampled.contains(true))
    }
    }

    val svc = new Service[Request, Response] {
      def apply(request: Request): Future[Response] = {
        // expect traceid to have been set on thread local context
        assert(Trace.idOption == trace1)

        // expect to get the right sampled value which is 1.0
        val sampler = ztp.sampler(req1)
        assert(sampler.contains(Sampler(1.0f)))

        Future.value(Response(Status.Ok, Stream()))
      }
    }

    val res = Trace.letIdOption(trace1) {
      svc(req1)
    }

    assert(Status.Ok == Await.result(res, 5.seconds).status)

    ztp.setContext(req1, trace1.get)
    // check "x-b3-traceid" and "x-b3-spanid" and "x-b3-sampled" and "x-b3-parentspanid" have been added to request
    assert(req1.headers.contains("x-b3-traceid"))
    assert(req1.headers.contains("x-b3-spanid"))
    assert(req1.headers.contains("x-b3-parentspanid"))
    assert(req1.headers.contains("x-b3-sampled"))

  }

  test("get traceid from multi x-b3 headers, 64bit trace id, do not sample decision set") {
    val ztp = new ZipkinTracePropagator

    //x-b3 multi headers with lower case
    val req1 = Request("http", Method.Get, "auf", "/", Stream.empty())
    req1.headers.add("x-b3-traceid", "80f198ee56343ba8")
    req1.headers.add("x-b3-spanid", "05e3ac9a4f6e3b90")
    req1.headers.add("x-b3-sampled", "0")
    req1.headers.add("x-b3-parentspanid", "e457b5a2e4d86bd1")
    val trace1 = ztp.traceId(req1)
    // test multi x-b3 headers have been removed after traceId call
    assert(req1.headers.toSeq == Seq((":scheme","http"), (":method","GET"), (":authority","auf"), (":path","/")))

    assert(trace1.isDefined) //expect trace exists and has the expected values
    trace1.foreach { tid => {
      assert(tid.traceId.toString().equals("80f198ee56343ba8"))
      assert(tid.spanId.toString().equals("05e3ac9a4f6e3b90"))
      assert(tid.parentId.toString().equals("e457b5a2e4d86bd1"))
      assert(tid.sampled.contains(false))
    }
    }

    val svc = new Service[Request, Response] {
      def apply(request: Request): Future[Response] = {
        // expect traceid to have been set on thread local context
        assert(Trace.idOption == trace1)

        // expect to get the right sampled value which is 1.0
        val sampler = ztp.sampler(req1)
        assert(sampler.contains(Sampler(0.0f)))

        Future.value(Response(Status.Ok, Stream()))
      }
    }

    val res = Trace.letIdOption(trace1) {
      svc(req1)
    }

    assert(Status.Ok == Await.result(res, 5.seconds).status)

    ztp.setContext(req1, trace1.get)
    // check "x-b3-traceid" and "x-b3-spanid" and "x-b3-sampled" and "x-b3-parentspanid" have been added to request
    assert(req1.headers.contains("x-b3-traceid"))
    assert(req1.headers.contains("x-b3-spanid"))
    assert(req1.headers.contains("x-b3-parentspanid"))
    assert(req1.headers.contains("x-b3-sampled"))
  }

  test("get traceid from multi x-b3 headers, 64bit trace id, no sampling decision present") {
    val ztp = new ZipkinTracePropagator

    //x-b3 multi headers with lower case
    val req1 = Request("http", Method.Get, "auf", "/", Stream.empty())
    req1.headers.add("x-b3-traceid", "80f198ee56343ba8")
    req1.headers.add("x-b3-spanid", "05e3ac9a4f6e3b90")
    req1.headers.add("x-b3-parentspanid", "e457b5a2e4d86bd1")
    val trace1 = ztp.traceId(req1)
    // test multi x-b3 headers have been removed after traceId call
    assert(req1.headers.toSeq == Seq((":scheme","http"), (":method","GET"), (":authority","auf"), (":path","/")))

    assert(trace1.isDefined) //expect trace exists and has the expected values
    trace1.foreach { tid => {
      assert(tid.traceId.toString().equals("80f198ee56343ba8"))
      assert(tid.spanId.toString().equals("05e3ac9a4f6e3b90"))
      assert(tid.parentId.toString().equals("e457b5a2e4d86bd1"))
      assert(tid.sampled.isEmpty)
    }
    }

    val svc = new Service[Request, Response] {
      def apply(request: Request): Future[Response] = {
        // expect traceid to have been set on thread local context
        assert(Trace.idOption == trace1)

        // expect to get the right sampled value which is 1.0
        val sampler = ztp.sampler(req1)
        assert(sampler.isEmpty)

        Future.value(Response(Status.Ok, Stream()))
      }
    }

    val res = Trace.letIdOption(trace1) {
      svc(req1)
    }

    assert(Status.Ok == Await.result(res, 5.seconds).status)

    ztp.setContext(req1, trace1.get)
    // check "x-b3-traceid" and "x-b3-spanid" and "x-b3-parentspanid" have been added to request
    assert(req1.headers.contains("x-b3-traceid"))
    assert(req1.headers.contains("x-b3-spanid"))
    assert(req1.headers.contains("x-b3-parentspanid"))
  }

  test("get traceid from multi x-b3 headers, 64bit trace id, debug flags set") {
    val ztp = new ZipkinTracePropagator

    //x-b3 multi headers with lower case
    val req1 = Request("http", Method.Get, "auf", "/", Stream.empty())
    req1.headers.add("x-b3-traceid", "80f198ee56343ba8")
    req1.headers.add("x-b3-spanid", "05e3ac9a4f6e3b90")
    req1.headers.add("x-b3-flags", "1")
    req1.headers.add("x-b3-parentspanid", "e457b5a2e4d86bd1")
    val trace1 = ztp.traceId(req1)
    // test multi x-b3 headers have been removed after traceId call
    assert(req1.headers.toSeq == Seq((":scheme","http"), (":method","GET"), (":authority","auf"), (":path","/")))

    assert(trace1.isDefined) //expect trace exists and has the expected values
    trace1.foreach { tid => {
      assert(tid.traceId.toString().equals("80f198ee56343ba8"))
      assert(tid.spanId.toString().equals("05e3ac9a4f6e3b90"))
      assert(tid.parentId.toString().equals("e457b5a2e4d86bd1"))
      assert(tid.sampled.contains(true))
    }
    }

    val svc = new Service[Request, Response] {
      def apply(request: Request): Future[Response] = {
        // expect traceid to have been set on thread local context
        assert(Trace.idOption == trace1)

        // expect to get the right sampled value which is 1.0
        val sampler = ztp.sampler(req1)
        assert(sampler.contains(Sampler(1.0f)))

        Future.value(Response(Status.Ok, Stream()))
      }
    }

    val res = Trace.letIdOption(trace1) {
      svc(req1)
    }

    assert(Status.Ok == Await.result(res, 5.seconds).status)

    ztp.setContext(req1, trace1.get)
    // check "x-b3-traceid" and "x-b3-spanid" and "x-b3-flags" and "x-b3-parentspanid" have been added to request
    assert(req1.headers.contains("x-b3-traceid"))
    assert(req1.headers.contains("x-b3-spanid"))
    assert(req1.headers.contains("x-b3-parentspanid"))
    assert(req1.headers.contains("x-b3-flags"))
  }

}