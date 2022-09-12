package com.github.pbyrne84.zio2playground.tracing

import com.typesafe.scalalogging.StrictLogging
import io.opentelemetry.context.propagation.TextMapGetter
import zhttp.http.Header

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava
class ZIOHeaderTextMapGetter extends TextMapGetter[List[Header]] with StrictLogging {
  override def keys(carrier: List[Header]): util.List[String] = {
    logger.info(s"getting keys $carrier for tracing")
    carrier.map(_._1.toString).asJava
  }

  // This will be called B3PropagatorExtractorMultipleHeaders using the values passed in
  // the header list in test.spanFrom.
  // At the moment sampled header need to be there which is a bit annoying as you can hack adding the
  // header all the time in the route but that is pants. There should be no faith in any calling entity
  // to be correct.
  // So if the 3 headers are not there or have incorrect values then we get varied results from traced ids that are invalid
  // (00000000000000000000000000000000 and 0000000000000000) or are not set at all.
  override def get(carrier: List[Header], key: String): String = {
    logger.info(s"get key from $carrier -  $key")
    carrier.find(_._1.toString.toLowerCase == key.toLowerCase).map(_._2.toString).orNull
  }
}
