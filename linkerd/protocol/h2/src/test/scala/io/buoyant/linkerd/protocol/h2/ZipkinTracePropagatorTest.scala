package io.buoyant.linkerd.protocol.h2

import org.scalatest.FunSuite
import com.twitter.finagle.buoyant.h2.{Headers, Request}
import com.twitter.finagle.buoyant.h2._
import io.buoyant.router.RoutingFactory._
import io.buoyant.linkerd.protocol.http._
import io.buoyant.router.H2Instances._

class ZipkinTracePropagatorTest extends FunSuite {
  test("get traceid from multi x-b3 headers, 64bit trace id, sampled, UPPER CASE header doesn't matter") {
    val ztp = new ZipkinTracePropagator

    //x-b3 multi headers with lower case
    val req1 = Request("http", Method.Get, "auf", "/", Stream.empty())
    req1.headers.add("x-b3-traceid", "80f198ee56343ba8")
    req1.headers.add("x-b3-spanid", "05e3ac9a4f6e3b90")
    req1.headers.add("x-b3-sampled", "1")
    req1.headers.add("x-b3-parentspanid", "e457b5a2e4d86bd1")

    //X-B3 multi heders with upper case
    val req2 = Request("http", Method.Get, "auf", "/", Stream.empty())
    req2.headers.add("X-B3-TRACEID", "80f198ee56343ba8")
    req2.headers.add("X-B3-SPANID", "05e3ac9a4f6e3b90")
    req2.headers.add("X-B3-SAMPLED", "1")
    req2.headers.add("X-B3-PARENTSPANID", "e457b5a2e4d86bd1")

    val trace1 = ztp.traceId(req1)
    val trace2 = ztp.traceId(req2)

    // traceid is the same, lower/upper case doesn't matter
    assert(trace1 == trace2)

    assert(trace1.isDefined) //expect trace exists
    trace1.foreach { tid => {
      assert(tid.traceId.toString().equals("80f198ee56343ba8"))
      assert(tid.spanId.toString().equals("05e3ac9a4f6e3b90"))
      assert(tid.parentId.toString().equals("e457b5a2e4d86bd1"))
      assert(tid.sampled.contains(true))

      // expect to get the right sampled value which is 0
      val sampler = ZipkinTrace.getSampler(req1.headers)
      assert(sampler.contains(1.0f))
    }}
  }

 }
