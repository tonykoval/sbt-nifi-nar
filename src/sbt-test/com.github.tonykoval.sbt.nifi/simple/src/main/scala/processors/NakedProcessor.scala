package processors

import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.processor.exception.ProcessException
import org.apache.nifi.processor.AbstractProcessor

class NakedProcessor extends AbstractProcessor {
  override def onTrigger(processContext: ProcessContext, processSession: ProcessSession): Unit = {
  }
}
