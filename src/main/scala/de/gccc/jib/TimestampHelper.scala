package de.gccc.jib

import java.time.Instant

object TimestampHelper {

  def useCurrentTimestamp(useCurrentTimestamp: Boolean) =
    if (useCurrentTimestamp) Instant.now() else Instant.EPOCH

}
