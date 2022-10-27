package ccm;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

// To run this integration use:
// kamel run CcmNotificationService.java --property file:ccmNotificationService.properties --profile openshift
// 

// camel-k: language=java
// camel-k: dependency=mvn:org.apache.camel.quarkus
// camel-k: dependency=mvn:org.apache.camel.camel-quarkus-kafka
// camel-k: dependency=mvn:org.apache.camel.camel-quarkus-jsonpath
// camel-k: dependency=mvn:org.apache.camel.camel-jackson
// camel-k: dependency=mvn:org.apache.camel.camel-splunk-hec
// camel-k: dependency=mvn:org.apache.camel.camel-http
// camel-k: dependency=mvn:org.apache.camel.camel-http-common

//import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import ccm.models.common.*;

public class CcmNotificationService extends RouteBuilder {
  @Override
  public void configure() throws Exception {
    // from("kafka:{{kafka.topic.courtcases.name}}")
    // .routeId("courtcases")
    // .log("Message received from Kafka : ${body}")
    // .log("    on the topic ${headers[kafka.TOPIC]}")
    // .log("    on the partition ${headers[kafka.PARTITION]}")
    // .log("    with the offset ${headers[kafka.OFFSET]}")
    // .log("    with the key ${headers[kafka.KEY]}")
    // .unmarshal().json()
    // .transform(simple("{\"type\": \"courtcase\", \"number\": \"${body[number]}\", \"status\": \"created\", \"public_content\": \"${body[public_content]}\", \"created_datetime\": \"${body[created_datetime]}\"}"))
    // .log("body (after unmarshalling): '${body}'")
    // .to("kafka:{{kafka.topic.kpis.name}}");

    //from("kafka:{{kafka.topic.courtcases.name}}?groupId=ccm-notification-service")
    from("kafka:{{kafka.topic.courtcases.name}}?groupId=ccm-notification-service")
    .routeId("processCourtcaseEvents")
    .log("Event from Kafka {{kafka.topic.courtcases.name}} topic (offset=${headers[kafka.OFFSET]}): ${body}\n" + 
      "    on the topic ${headers[kafka.TOPIC]}\n" +
      "    on the partition ${headers[kafka.PARTITION]}\n" +
      "    with the offset ${headers[kafka.OFFSET]}\n" +
      "    with the key ${headers[kafka.KEY]}")
    .setHeader("event_object_id")
      .jsonpath("$.event_object_id")
    .setHeader("event_status")
      .jsonpath("$.event_status")
    .setHeader("event")
      .simple("${body}")
    .choice()
      .when(header("event_status").isEqualTo(CommonCourtCaseEvent.STATUS.CHANGED))
        .to("direct:processCourtCaseChanged")
      .when(header("event_status").isEqualTo(CommonCourtCaseEvent.STATUS.CREATED))
        .to("direct:processCourtCaseCreated")
      .when(header("event_status").isEqualTo(CommonCourtCaseEvent.STATUS.UPDATED))
        .to("direct:processCourtCaseUpdated")
      .when(header("event_status").isEqualTo(CommonCourtCaseEvent.STATUS.AUTH_LIST_CHANGED))
        .to("direct:processCourtCaseAuthListChanged")
      .otherwise()
        .to("direct:processUnknownStatus");
    ;

    from("kafka:{{kafka.topic.courtcase-metadatas.name}}?groupId=ccm-notification-service")
    .routeId("processCourtcaseMetadataEvents")
    .log("Event from Kafka {{kafka.topic.courtcase-metadatas.name}} topic (offset=${headers[kafka.OFFSET]}): ${body}\n" + 
      "    on the topic ${headers[kafka.TOPIC]}\n" +
      "    on the partition ${headers[kafka.PARTITION]}\n" +
      "    with the offset ${headers[kafka.OFFSET]}\n" +
      "    with the key ${headers[kafka.KEY]}")
    .setHeader("event_object_id")
      .jsonpath("$.event_object_id")
    .setHeader("event_status")
      .jsonpath("$.event_status")
    .setHeader("event")
      .simple("${body}")
    .choice()
      .when(header("event_status").isEqualTo(CommonCourtCaseMetadataEvent.STATUS.CHANGED))
        .to("direct:processCourtCaseMetadataChanged")
      .when(header("event_status").isEqualTo(CommonCourtCaseMetadataEvent.STATUS.APPEARANCE_CHANGED))
        .to("direct:processCourtCaseAppearanceChanged")
      .when(header("event_status").isEqualTo(CommonCourtCaseMetadataEvent.STATUS.CROWN_ASSIGNMENT_CHANGED))
        .to("direct:processCourtCaseCrownAssignmentChanged")
      .otherwise()
        .to("direct:processUnknownStatus");
    ;

    from("direct:processCourtCaseChanged")
    .routeId("processCourtCaseChanged")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("processCourtCaseChanged.  event_object_id = ${header[event_object_id]}")
    .setHeader("number", simple("${header[event_object_id]}"))
    .to("http://ccm-lookup-service/getCourtCaseExists")
    .unmarshal().json()
    .setProperty("caseFound").simple("${body[id]}")
    .process(new Processor() {
      @Override
      public void process(Exchange ex) {
        CommonCourtCaseEvent be = new CommonCourtCaseEvent();

        // hardcoding boolean to false for first implementation
        //boolean court_case_exists = ex.getIn().getBody() != null && ex.getIn().getBody().toString().length() > 0;
        boolean court_case_exists = ex.getProperty("caseFound").toString().length() > 0;

        String event_object_id = ex.getIn().getHeader("event_object_id").toString();

        be.setEvent_source(CommonCourtCaseEvent.SOURCE.JADE_CCM.toString());
        be.setEvent_object_id(event_object_id);
        be.setJustin_rcc_id(event_object_id);

        if (court_case_exists) {
          be.setEvent_status(CommonCourtCaseEvent.STATUS.UPDATED.toString());
        } else {
          be.setEvent_status(CommonCourtCaseEvent.STATUS.CREATED.toString());
        }

        ex.getMessage().setBody(be);
      }
    })
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseEvent.class)
    .log("Generating derived court case event: ${body}")
    .to("kafka:{{kafka.topic.courtcases.name}}")
    .setBody().simple("CCM Notification splunk adapter call: processCourtCaseChanged")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processCourtCaseCreated")
    .routeId("processCourtCaseCreated")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("processCourtCaseCreated.  event_object_id = ${header[event_object_id]}")
    .log("Retrieve latest court case details from JUSTIN.")
    .setHeader(Exchange.HTTP_METHOD, simple("POST"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .setHeader("number").simple("${header.event_object_id}")
    .to("http://ccm-lookup-service/getCourtCaseDetails")
    .log("Create court case in DEMS.  Court case data = ${body}.")
    .setProperty("courtcase_data", simple("${bodyAs(String)}"))
    .to("http://ccm-dems-adapter/createCourtCase")
    .log("Update court case auth list.")
    .to("direct:processCourtCaseAuthListChanged")
    .setBody().simple("CCM Notification splunk adapter call: processCourtCaseCreated")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processCourtCaseUpdated")
    .routeId("processCourtCaseUpdated")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("processCourtCaseCreated.  event_object_id = ${header[event_object_id]}")
    .log("Retrieve latest court case details from JUSTIN.")
    .setHeader(Exchange.HTTP_METHOD, simple("POST"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .setHeader("number").simple("${header.event_object_id}")
    .to("http://ccm-lookup-service/getCourtCaseDetails")
    .log("Update court case in DEMS.  Court case data = ${body}.")
    .setProperty("courtcase_data", simple("${bodyAs(String)}"))
    //.to("http://ccm-dems-adapter/updateCourtCase?httpClient.connectTimeout=1&httpClient.connectionRequestTimeout=1&httpClient.socketTimeout=1")
    .to("http://ccm-dems-adapter/updateCourtCase")
    .log("Update court case auth list.")
    .to("direct:processCourtCaseAuthListChanged")
    .setBody().simple("CCM Notification splunk adapter call: processCourtCaseUpdated")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processCourtCaseAuthListChanged")
    .routeId("processCourtCaseAuthListChanged")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("processCourtCaseAuthListChanged.  event_object_id = ${header[event_object_id]}")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .setHeader("number").simple("${header.event_object_id}")
    .log("Retrieve court case auth list")
    .to("http://ccm-lookup-service/getCourtCaseAuthList")
    .log("Update court case auth list in DEMS.  Court case auth list = ${body}")
    // JADE-1489 work around #1 -- not sure why body doesn't make it into dems-adapter
    .setHeader("temp-body", simple("${body}"))
    .to("http://ccm-dems-adapter/syncCaseUserList")
    .setBody().simple("CCM Notification splunk adapter call: processCourtCaseAuthListChanged")
    .to("direct:logSplunkEvent")
    ;

    // POC code - Experiment with moving a route definition into a dedicated route registration method for ease of code isolation and development
    // naming convention:
    //   * method name is the route id
    processCourtCaseMetadataChanged();

    from("direct:processCourtCaseAppearanceChanged")
    .routeId("processCourtCaseAppearanceChanged")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("processCourtCaseAppearanceChanged.  event_object_id = ${header[event_object_id]}")
    .setHeader("number", simple("${header[event_object_id]}"))
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .to("http://ccm-lookup-service/getCourtCaseAppearanceSummaryList")
    .log("Retrieved Court Case appearance summary list from JUSTIN: ${body}")
    // JADE-1489 workaround #2 -- not sure why in this instance the value of ${body} as-is isn't 
    //   accessible in the split() block through exchange properties unless converted to String first.
    .setProperty("business_data", simple("${bodyAs(String)}"))
    .to("http://ccm-lookup-service/getCourtCaseMetadata")
    .log("Retrieved Court Case Metadata from JUSTIN: ${body}")
    .setProperty("metadata_data", simple("${bodyAs(String)}"))
    .split()
      .jsonpathWriteAsString("$.related_agency_file")
      .setHeader("rcc_id", jsonpath("$.rcc_id"))
      .log("Found related court case. Rcc_id: ${header.rcc_id}")
      .setBody(simple("${exchangeProperty.business_data}"))
      .setHeader(Exchange.HTTP_METHOD, simple("PUT"))
      .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
      .to("http://ccm-dems-adapter/updateCourtCaseWithAppearanceSummary")
    .end()
    .setBody().simple("CCM Notification splunk adapter call: processCourtCaseAppearanceChanged")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processCourtCaseCrownAssignmentChanged")
    .routeId("processCourtCaseCrownAssignmentChanged")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("processCourtCaseCrownAssignmentChanged.  event_object_id = ${header[event_object_id]}")
    .setHeader("number", simple("${header[event_object_id]}"))
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .to("http://ccm-lookup-service/getCourtCaseCrownAssignmentList")
    .log("Retrieved Court Case crown assignment list from JUSTIN: ${body}")
    // JADE-1489 workaround #2 -- not sure why in this instance the value of ${body} as-is isn't 
    //   accessible in the split() block through exchange properties unless converted to String first.
    .setProperty("business_data", simple("${bodyAs(String)}"))
    .to("http://ccm-lookup-service/getCourtCaseMetadata")
    .log("Retrieved Court Case Metadata from JUSTIN: ${body}")
    .setProperty("metadata_data", simple("${bodyAs(String)}"))
    .split()
      .jsonpathWriteAsString("$.related_agency_file")
      .setHeader("rcc_id", jsonpath("$.rcc_id"))
      .log("Found related court case. Rcc_id: ${header.rcc_id}")
      .setBody(simple("${exchangeProperty.business_data}"))
      .setHeader(Exchange.HTTP_METHOD, simple("PUT"))
      .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
      .to("http://ccm-dems-adapter/updateCourtCaseWithCrownAssignmentData")
    .end()
    .setBody().simple("CCM Notification splunk adapter call: processCourtCaseCrownAssignmentChanged")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processUnknownStatus")
    .routeId("processUnknownStatus")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("processUnknownStatus.  event_object_id = ${header[event_object_id]}")
    ;


    from("direct:logSplunkEvent")
    .routeId("logSplunkEvent")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .setProperty("splunk_event", simple("${bodyAs(String)}"))
    .log("Processing Splunk event for message: ${exchangeProperty.splunk_event}")
    .process(new Processor() {
      @Override
      public void process(Exchange ex) {
        CommonSplunkEvent be = new CommonSplunkEvent(ex.getProperty("splunk_event").toString());
        be.setSource("ccm-notification-service");

        ex.getMessage().setBody(be, CommonSplunkEvent.class);
      }
    })
    .marshal().json(JsonLibrary.Jackson, CommonSplunkEvent.class)
    .log("Logging event to splunk body: ${body}")
    //.to("kafka:{{kafka.topic.kpis.name}}")
    ;


  }

  private void processCourtCaseMetadataChanged() {
    // use method name as route id
    String routeId = new Object() {}.getClass().getEnclosingMethod().getName();

    from("direct:" + routeId)
    .routeId(routeId)
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("event_object_id = ${header[event_object_id]}")
    .setHeader("number", simple("${header[event_object_id]}"))
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .to("http://ccm-lookup-service/getCourtCaseMetadata")
    .log("Retrieved Court Case Metadata from JUSTIN: ${body}")
    // JADE-1489 workaround #2 -- not sure why in this instance the value of ${body} as-is isn't 
    //   accessible in the split() block through exchange properties unless converted to String first.
    .setProperty("metadata_data", simple("${bodyAs(String)}"))
    .split()
      .jsonpathWriteAsString("$.related_agency_file")
      .setHeader("rcc_id", jsonpath("$.rcc_id"))
      .log("Found related court case. Rcc_id: ${header.rcc_id}")
      .setBody(simple("${exchangeProperty.metadata_data}"))
      .setHeader(Exchange.HTTP_METHOD, simple("PUT"))
      .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
      .to("http://ccm-dems-adapter/updateCourtCaseWithMetadata")
    .end()
    .setBody().simple("CCM Notification splunk adapter call: processCourtCaseMetadataChanged")
    .to("direct:logSplunkEvent")
    ;
  }
}