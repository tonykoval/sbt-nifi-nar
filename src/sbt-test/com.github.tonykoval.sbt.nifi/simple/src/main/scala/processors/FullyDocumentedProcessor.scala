package processors

import org.apache.nifi.annotation.behavior.InputRequirement.Requirement
import org.apache.nifi.annotation.behavior._
import org.apache.nifi.annotation.documentation.{CapabilityDescription, Tags}
import org.apache.nifi.components.state.Scope
import org.apache.nifi.components.{AllowableValue, PropertyDescriptor}
import org.apache.nifi.controller.ControllerService
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.processor.util.StandardValidators
import org.apache.nifi.serialization.RecordReaderFactory
import org.apache.nifi.processor.{AbstractProcessor, ProcessContext, ProcessSession, Relationship}

import scala.collection.JavaConverters._

@Tags(Array("one", "two", "three"))
@CapabilityDescription("This is a processor that is used to test documentation.")
@ReadsAttribute(attribute = "incoming", description = "this specifies the format of the thing")
@DynamicProperty(name = "Relationship Name", expressionLanguageScope = ExpressionLanguageScope.FLOWFILE_ATTRIBUTES,
  value = "some XPath", description = "Routes FlowFiles to relationships based on XPath")
@DynamicRelationship(name = "name from dynamic property", description = "all files that match the properties XPath")
@Stateful(scopes = Array(Scope.CLUSTER, Scope.LOCAL), description = "state management description")
@WritesAttributes(Array(
  new WritesAttribute(attribute = "first", description = "this is the first attribute i write"),
  new WritesAttribute(attribute = "second")
)
)
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@SystemResourceConsideration(resource = SystemResource.CPU)
@SystemResourceConsideration(resource = SystemResource.DISK, description = "Customized disk usage description")
@SystemResourceConsideration(resource = SystemResource.MEMORY, description = "")
class FullyDocumentedProcessor extends AbstractProcessor {

  val DIRECTORY = new PropertyDescriptor.Builder()
    .name("Input Directory")
    .description("The input directory from which to pull files")
    .required(true)
    .addValidator(StandardValidators.createDirectoryExistsValidator(true, false))
    .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
    .build()

  val RECURSE = new PropertyDescriptor.Builder()
    .name("Recurse Subdirectories")
    .description("Indicates whether or not to pull files from subdirectories")
    .required(true)
    .allowableValues(
      new AllowableValue("true", "true", "Should pull from sub directories"),
      new AllowableValue("false", "false", "Should not pull from sub directories")
    )
    .defaultValue("true")
    .build()

  val POLLING_INTERVAL = new PropertyDescriptor.Builder()
    .name("Polling Interval")
    .description("Indicates how long to wait before performing a directory listing")
    .required(true)
    .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
    .defaultValue("0 sec")
    .build()

  @SuppressWarnings(Array("deprecation"))
  val OPTIONAL_PROPERTY = new PropertyDescriptor.Builder()
    .name("Optional Property")
    .description("This is a property you can use or not")
    .required(false)
    .expressionLanguageSupported(true) // test documentation of deprecated method
    .build()

  @SuppressWarnings(Array("deprecation"))
  val TYPE_PROPERTY = new PropertyDescriptor.Builder()
    .name("Type")
    .description("This is the type of something that you can choose.  It has several possible values")
    .allowableValues("yes", "no", "maybe", "possibly", "not likely", "longer option name")
    .required(true)
    .expressionLanguageSupported(false) // test documentation of deprecated method
    .build()

  val SERVICE_PROPERTY = new PropertyDescriptor.Builder()
    .name("Controller Service")
    .description("This is the controller service to use to do things")
    .identifiesControllerService(classOf[RecordReaderFactory])
    .required(true)
    .build()

  val REL_SUCCESS = new Relationship.Builder()
    .name("success")
    .description("Successful files")
    .build()

  val REL_FAILURE = new Relationship.Builder()
    .name("failure")
    .description("Failing files")
    .build()

  var onRemovedNoArgs = 0
  var onRemovedArgs = 0
  var onShutdownNoArgs = 0
  var onShutdownArgs = 0

  override def getSupportedPropertyDescriptors(): java.util.List[PropertyDescriptor] =
    List(DIRECTORY, RECURSE, POLLING_INTERVAL, OPTIONAL_PROPERTY, TYPE_PROPERTY, SERVICE_PROPERTY).asJava

  override def getRelationships(): java.util.Set[Relationship] =
    Set(REL_SUCCESS, REL_FAILURE).asJava

  override def onTrigger(context: ProcessContext, session: ProcessSession) {

  }
}

trait SampleService extends ControllerService {
  def doSomething(): Unit
}


