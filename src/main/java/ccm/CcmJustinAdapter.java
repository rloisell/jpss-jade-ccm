package ccm;
// To run this integration use:
// kamel run CcmJustinAdapter.java --property file:application.properties --profile openshift
// 
// recover the service location. If you're running on minikube, minikube service platform-http-server --url=true
// curl -d '{}' http://ccm-justin-adapter/courtFileCreated
//

// camel-k: language=java
// camel-k: dependency=mvn:org.apache.camel.quarkus
// camel-k: dependency=mvn:org.apache.camel.component.kafka
// camel-k: dependency=mvn:org.apache.camel.camel-quarkus-kafka
// camel-k: dependency=mvn:org.apache.camel.camel-quarkus-jsonpath
// camel-k: dependency=mvn:org.apache.camel.camel-jackson
// camel-k: dependency=mvn:org.apache.camel.camel-splunk-hec
// camel-k: dependency=mvn:org.apache.camel.camel-splunk
// camel-k: dependency=mvn:org.apache.camel.camel-http
// camel-k: dependency=mvn:org.apache.camel.camel-http-common

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
//import org.apache.camel.component.kafka.KafkaConstants;
//import org.apache.camel.model.;

import ccm.models.common.*;
import ccm.models.system.justin.*;

class JustinEventBatchProcessor implements Processor {

  // example: https://github.com/apache/camel-examples/tree/main/examples/transformer-demo/src/main/java/org/apache/camel/example/transformer/demo
  // example: https://www.baeldung.com/java-camel-jackson-json-array
  //   Unmarshalling a JSON Array using camel-jackson

  // example: https://www.programcreek.com/java-api-examples/?api=org.apache.camel.component.jackson.JacksonDataFormat
  //   Marshalling and unmarshalling Json and Pojo

  // example: https://developers.redhat.com/articles/2021/11/24/normalize-web-services-camel-k-and-atlasmap-part-1#camel_k_implementation_overview
  //   Normalize web services with Camel K and AtlasMap, Part 1
  // example: https://developers.redhat.com/articles/2021/11/26/normalize-web-services-camel-k-and-atlasmap-part-2
  //   Normalize web services with Camel K and AtlasMap, Part 2

  @Override
  public void process(Exchange exchange) throws Exception {
    // Insert code that gets executed *before* delegating
    // to the next processor in the chain.

    String exchangeId = exchange.getExchangeId();
    String messageId = exchange.getIn().getMessageId();

    JustinEventBatch jeb = exchange.getIn().getBody(JustinEventBatch.class);

    int batchSize = jeb.getEvents().size();
    System.out.println("Retrieved " + batchSize + (batchSize == 1 ? " record " : " records ") + "from JUSTIN Interface.  JADE-CCM Exchange Id = " + exchangeId + "; JADE-CCM Message Id = " + messageId);
    System.out.println("Total number of JUSTIN events retrieved: " + jeb.getEvents().size());

    if (jeb.getEvents().size() > 0) {
      for (JustinEvent e: jeb.getEvents()) {
        System.out.print("Processing JUSTIN event " + e.getEvent_message_id() + " (" + e.getMessage_event_type_cd() + ").");

        if (e.isAgenFileEvent()) {
          // court case changed.  generate new business event.
          CommonCourtCaseEvent bce = new CommonCourtCaseEvent(e);
          System.out.println(" Generating 'Court Case Changed' event (RCC_ID = '" + bce.getJustin_rcc_id() + "')..");
        } else if (e.isAuthListEvent()) {
          // auth list changed.  Generate new business event.
          CommonCourtCaseEvent bce = new CommonCourtCaseEvent(e);
          System.out.println(" Generating 'Court Case Auth List Changed' event (RCC_ID = '" + bce.getJustin_rcc_id() + "')..");
        } else if (e.isCourtFileEvent()) {
          // court file changed.  Generate new business event.
          CommonCourtCaseMetadataEvent bcme = new CommonCourtCaseMetadataEvent(e);
          System.out.println(" Generating 'Court Case Metadata Changed' event (MDOC_NO = '" + bcme.getJustin_mdoc_no() + "')..");
        } else {
          System.out.println(" Unknown JUSTIN event type; Do nothing.");
        }
      }
    }

    exchange.getMessage().setBody("OK");
  }
}

public class CcmJustinAdapter extends RouteBuilder {
  @Override
  public void configure() throws Exception {
    from("platform-http:/courtFileCreated?httpMethodRestrict=POST")
    .routeId("courtFileCreated")
    .removeHeader("CamelHttpUri")
    .removeHeader("CamelHttpBaseUri")
    .removeHeaders("CamelHttp*")
    .log("body (before unmarshalling): '${body}'")
    .unmarshal().json()
    .transform(simple("{\"number\": \"${body[number]}\", \"status\": \"created\", \"sensitive_content\": \"${body[sensitive_content]}\", \"public_content\": \"${body[public_content]}\", \"created_datetime\": \"${body[created_datetime]}\"}"))
    .log("body (after unmarshalling): '${body}'")
    .to("kafka:{{kafka.topic.courtcases.name}}");

    from("platform-http:/v1/health?httpMethodRestrict=GET")
    .routeId("healthCheck")
    .removeHeaders("CamelHttp*")
    .log("/v1/health request received")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .to("https://dev.jag.gov.bc.ca/ords/devj/justinords/dems/v1/health");

    from("file:/tmp/?fileName=eventBatch-oneRCC.json&exchangePattern=InOnly")
    .routeId("requeueEvents")
    //.log("Processing file with content: ${body}")
    //.to("direct:processJustinEventBatch")
    .log("Re-queueing event(s)...")
    //.removeHeaders("*")
    .setHeader(Exchange.HTTP_METHOD, simple("PUT"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    //.to("{{justin.host}}/requeueEventById?id=2045")
    //.to("{{justin.host}}/requeueEventById?id=2060")
    //.to("{{justin.host}}/requeueEventById?id=2307") // AGEN_FILE 50431.0734
    //.to("{{justin.host}}/requeueEventById?id=2309") // AUTH_LIST 50431.0734
    //.to("{{justin.host}}/requeueEventById?id=2367") // AGEN_FILE 50433.0734
    //.to("{{justin.host}}/requeueEventById?id=2368") // AUTH_LIST 50433.0734
    //.to("{{justin.host}}/requeueEventById?id=2451") // COURT_FILE 39857

    // JSIT Sep 8
    //.to("{{justin.host}}/requeueEventById?id=2581") // AGEN_FILE 49408.0734 (case name: YOYO, Yammy; SOSO, Yolando ...)
    //.to("{{justin.host}}/requeueEventById?id=2590") // AGEN_FILE 50448.0734 (case name: VADER, Darth)
    //.to("{{justin.host}}/requeueEventById?id=2592") // COURT_FILE 39861 (court file for Vader agency file)

    //.to("{{justin.host}}/requeueEventById?id=2362") // AGEN_FILE 50431.0734
    .to("{{justin.host}}/requeueEventById?id=2320") // COURT_FILE 39849 (RCC_ID 50431.0734)
    //.to("{{justin.host}}/requeueEventById?id=2327") // APPR (mdoc no 39849; RCC_ID = 50431.0734)
    //.to("{{justin.host}}/requeueEventById?id=2321") // CRN_ASSIGN (mdoc no 39849; RCC_ID 50431.0734)

    // JSIT Sep 29
    .to("{{justin.host}}/requeueEventById?id=2753") // AGEN_FILE (RCC_ID = 50454.0734)
    .to("{{justin.host}}/requeueEventById?id=2759") // APPR (mdoc no 39869; RCC_ID = 50444.0734)
    ;

    from("timer://simpleTimer?period={{notification.check.frequency}}")
    .routeId("processTimer")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .setHeader(Exchange.HTTP_METHOD, simple("PUT"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .to("https://dev.jag.gov.bc.ca/ords/devj/justinords/dems/v1/newEventsBatch") // mark all new events as "in progres"
    //.log("Marking all new events in JUSTIN as 'in progress': ${body}")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .to("https://dev.jag.gov.bc.ca/ords/devj/justinords/dems/v1/inProgressEvents") // retrieve all "in progress" events
    //.log("Processing in progress events from JUSTIN: ${body}")
    .to("direct:processJustinEventBatch")
    ;

    /* 
     * To kick off processing, execute the following on the 'service/ccm-justin-adapter' pod:
     *    cp /etc/camel/resources/eventBatch-oneRCC.json /tmp
     */
    //from("timer://simpleTimer?period={{notification.check.frequency}}")
    from("direct:processJustinEventBatch")
    .routeId("processJustinEventBatch")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    //from("file:/etc/camel/resources/?fileName=eventBatch-oneRCC.json&noop=true&exchangePattern=InOnly&readLock=none")
    //from("file:/etc/camel/resources/?fileName=eventBatch-empty.json&noop=true&exchangePattern=InOnly&readLock=none")
    //from("file:/etc/camel/resources/?fileName=eventBatch.json&noop=true&exchangePattern=InOnly&readLock=none")
    //.to("splunk-hec://hec.monitoring.ag.gov.bc.ca:8088/services/collector/f38b6861-1947-474b-bf6c-a743f2c6a413?")
    // .to("https://dev.jag.gov.bc.ca/ords/devj/justinords/dems/v1/inProgressEvents")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    //.to("direct:processNewJUSTINEvents");
    //.log("Processing new JUSTIN events: ${body}")
    //.unmarshal().json(JsonLibrary.Jackson, JustinEventBatch.class)
    .setProperty("numOfEvents")
      .jsonpath("$.events.length()")
    .choice()
      .when(simple("${exchangeProperty.numOfEvents} > 0"))
        .log("Event batch count: ${exchangeProperty.numOfEvents}")
        .endChoice()
      .end()
    .setProperty("justin_events")
      .jsonpath("$.events")
    .split()
      .jsonpathWriteAsString("$.events")  // https://stackoverflow.com/questions/51124978/splitting-a-json-array-with-camel
      .setProperty("message_event_type_cd")
        .jsonpath("$.message_event_type_cd")
      .setProperty("event_message_id")
        .jsonpath("$.event_message_id")
      .log("Event batch record: (id=${exchangeProperty.event_message_id}, type=${exchangeProperty.message_event_type_cd})")
      .choice()
        .when(header("message_event_type_cd").isEqualTo(JustinEvent.STATUS.AGEN_FILE))
          .to("direct:processAgenFileEvent")
          .endChoice()
        .when(header("message_event_type_cd").isEqualTo(JustinEvent.STATUS.AUTH_LIST))
          .to("direct:processAuthListEvent")
          .endChoice()
        .when(header("message_event_type_cd").isEqualTo(JustinEvent.STATUS.COURT_FILE))
          .to("direct:processCourtFileEvent")
          .endChoice()
        .when(header("message_event_type_cd").isEqualTo(JustinEvent.STATUS.APPR))
          .to("direct:processApprEvent")
          .endChoice()
        .when(header("message_event_type_cd").isEqualTo(JustinEvent.STATUS.CRN_ASSIGN))
          .to("direct:processCrnAssignEvent")
          .endChoice()
        .otherwise()
          .log("message_event_type_cd = ${exchangeProperty.message_event_type_cd}")
          .to("direct:processUnknownEvent")
          .endChoice()
        .end()
    ;

      //.to("direct:processOneJUSTINEvent");
    
    // https://github.com/json-path/JsonPath

    //JustinEventBatchProcessor jp = new JustinEventBatchProcessor();

    from("direct:processNewJUSTINEvents")
    .routeId("processNewJUSTINEvents")
    .log("Processing new JUSTIN events: ${body}")
    .unmarshal().json(JsonLibrary.Jackson, JustinEventBatch.class)
    .process(new JustinEventBatchProcessor())
    .log("Getting ready to send to Kafka: ${body}")
    ;

    from("direct:processAgenFileEvent")
    .routeId("processAgenFileEvent")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .setProperty("justin_event").body()
    .log("Processing AGEN_FILE event: ${exchangeProperty.justin_event}")
    .unmarshal().json(JsonLibrary.Jackson, JustinEvent.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) throws Exception {
        // Insert code that gets executed *before* delegating
        // to the next processor in the chain.
    
        JustinEvent je = exchange.getIn().getBody(JustinEvent.class);
    
        CommonCourtCaseEvent be = new CommonCourtCaseEvent(je);
    
        exchange.getMessage().setBody(be, CommonCourtCaseEvent.class);
        exchange.getMessage().setHeader("kafka.KEY", be.getEvent_object_id());
      }})
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseEvent.class)
    .setProperty("business_event").body()
    .log("Generate converted business event: ${body}")
    .to("kafka:{{kafka.topic.courtcases.name}}")
    .setBody(simple("${exchangeProperty.justin_event}"))
    .setProperty("event_message_id")
      .jsonpath("$.event_message_id")
    .to("direct:confirmEventProcessed")
    .setBody().simple("${routeId}")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processAuthListEvent")
    .routeId("processAuthListEvent")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .setProperty("justin_event").body()
    .log("Processing AUTH_LIST event: ${exchangeProperty.justin_event}")
    .unmarshal().json(JsonLibrary.Jackson, JustinEvent.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) throws Exception {
        // Insert code that gets executed *before* delegating
        // to the next processor in the chain.
    
        JustinEvent je = exchange.getIn().getBody(JustinEvent.class);
    
        CommonCourtCaseEvent be = new CommonCourtCaseEvent(je);
    
        exchange.getMessage().setBody(be, CommonCourtCaseEvent.class);
        exchange.getMessage().setHeader("kafka.KEY", be.getEvent_object_id());
      }})
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseEvent.class)
    .setProperty("business_event").body()
    .log("Generate converted business event: ${body}")
    .to("kafka:{{kafka.topic.courtcases.name}}")
    .setBody(simple("${exchangeProperty.justin_event}"))
    .setProperty("event_message_id")
      .jsonpath("$.event_message_id")
    .to("direct:confirmEventProcessed")
    .setBody().simple("${routeId}")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processCourtFileEvent")
    .routeId("processCourtFileEvent")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .setProperty("justin_event").body()
    .log("Processing COURT_FILE event: ${exchangeProperty.justin_event}")
    .unmarshal().json(JsonLibrary.Jackson, JustinEvent.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) throws Exception {
        // Insert code that gets executed *before* delegating
        // to the next processor in the chain.
    
        JustinEvent je = exchange.getIn().getBody(JustinEvent.class);
    
        CommonCourtCaseMetadataEvent be = new CommonCourtCaseMetadataEvent(je);
    
        exchange.getMessage().setBody(be, CommonCourtCaseMetadataEvent.class);
        exchange.getMessage().setHeader("kafka.KEY", be.getEvent_object_id());
      }})
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseMetadataEvent.class)
    .setProperty("business_event").body()
    .log("Generate converted business event: ${body}")
    .to("kafka:{{kafka.topic.courtcase-metadatas.name}}")
    .setBody(simple("${exchangeProperty.justin_event}"))
    .setProperty("event_message_id")
      .jsonpath("$.event_message_id")
    .to("direct:confirmEventProcessed")
    .setBody().simple("${routeId}")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processApprEvent")
    .routeId("processApprEvent")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .setProperty("justin_event").body()
    .log("Processing APPR event: ${body}")
    .unmarshal().json(JsonLibrary.Jackson, JustinEvent.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) throws Exception {
        // Insert code that gets executed *before* delegating
        // to the next processor in the chain.
    
        JustinEvent je = exchange.getIn().getBody(JustinEvent.class);
    
        CommonCourtCaseMetadataEvent be = new CommonCourtCaseMetadataEvent(je);
    
        exchange.getMessage().setBody(be, CommonCourtCaseMetadataEvent.class);
        exchange.getMessage().setHeader("kafka.KEY", be.getEvent_object_id());
      }})
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseMetadataEvent.class)
    .log("Generate converted business event: ${body}")
    .to("kafka:{{kafka.topic.courtcase-metadatas.name}}")
    .setBody(simple("${exchangeProperty.justin_event}"))
    .setProperty("event_message_id")
      .jsonpath("$.event_message_id")
    .to("direct:confirmEventProcessed")
    .setBody().simple("${routeId}")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processCrnAssignEvent")
    .routeId("processCrnAssignEvent")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .setProperty("justin_event").body()
    .log("Processing CRN_ASSIGN event: ${body}")
    .unmarshal().json(JsonLibrary.Jackson, JustinEvent.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) throws Exception {
        // Insert code that gets executed *before* delegating
        // to the next processor in the chain.
    
        JustinEvent je = exchange.getIn().getBody(JustinEvent.class);
    
        CommonCourtCaseMetadataEvent be = new CommonCourtCaseMetadataEvent(je);
    
        exchange.getMessage().setBody(be, CommonCourtCaseMetadataEvent.class);
        exchange.getMessage().setHeader("kafka.KEY", be.getEvent_object_id());
      }})
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseMetadataEvent.class)
    .log("Generate converted business event: ${body}")
    .to("kafka:{{kafka.topic.courtcase-metadatas.name}}")
    .setBody(simple("${exchangeProperty.justin_event}"))
    .setProperty("event_message_id")
      .jsonpath("$.event_message_id")
    .to("direct:confirmEventProcessed")
    .setBody().simple("${routeId}")
    .to("direct:logSplunkEvent")
    ;

    from("direct:processUnknownEvent")
    .routeId("processUnknownEvent")
    .log("Ignoring unknown event: ${body}")
    .to("direct:confirmEventProcessed")
    ;

    from("direct:confirmEventProcessed")
    .routeId("confirmEventProcessed")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .removeHeaders("*")
    .setProperty("event_message_id")
      .jsonpath("$.event_message_id")
    .setProperty("message_event_type_cd")
      .jsonpath("$.message_event_type_cd")
    .log("Marking event ${exchangeProperty.event_message_id} (${exchangeProperty.message_event_type_cd}) as processed.")
    //.removeHeader("message_event_type_cd")
    //.removeHeader("event_message_id")
    //.removeHeader("is_success")
    .setHeader(Exchange.HTTP_METHOD, simple("PUT"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    //.toD("{{justin.host}}/eventStatus?event_message_id=${header.custom_event_message_id}&is_success=T")
    //.to("{{justin.host}}/eventStatus")
    .doTry()
      //.to("{{justin.host}}/eventStatus")
      .toD("{{justin.host}}/eventStatus?event_message_id=${exchangeProperty.event_message_id}&is_success=T")
    .doCatch(Exception.class)
      .log("Exception: ${exception}")
      .log("Exchange Context: ${exchange.context}")
      .choice()
        //.when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo("404"))
        .when().simple("${exception.statusCode} == 400")
          .log(LoggingLevel.INFO,"Bad request.  HTTP response code = ${exception.statusCode}")
          .log("Exception: '${exception}'")
          .log("Headers: '${headers}'")
        .endChoice()
        .otherwise()
          .log(LoggingLevel.ERROR,"Unknown error.  HTTP response code = ${exception.statusCode}")
          .log("Headers: '${headers}'")
        .endChoice()
      .end()
    .end()
    ;

    from("platform-http:/getCourtCaseDetails?httpMethodRestrict=GET")
    .routeId("getCourtCaseDetails")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("getCourtCaseDetails request received. number = ${header[number]}")
    .removeHeader("CamelHttpUri")
    .removeHeader("CamelHttpBaseUri")
    .removeHeaders("CamelHttp*")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .removeHeader("rcc_id")
    .toD("{{justin.host}}/agencyFile?rcc_id=${header[number]}")
    .log("Received response from JUSTIN: '${body}'")
    .unmarshal().json(JsonLibrary.Jackson, JustinAgencyFile.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) {
        JustinAgencyFile j = exchange.getIn().getBody(JustinAgencyFile.class);
        CommonCourtCaseData b = new CommonCourtCaseData(j);
        exchange.getMessage().setBody(b, CommonCourtCaseData.class);
      }
    })
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseData.class)
    .log("Converted response (from JUSTIN to Business model): '${body}'")
    ;

    from("platform-http:/getCourtCaseAuthList?httpMethodRestrict=GET")
    .routeId("getCourtCaseAuthList")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("getCourtCaseAuthList request received. rcc_id = ${header.number}")
    .removeHeader("CamelHttpUri")
    .removeHeader("CamelHttpBaseUri")
    .removeHeaders("CamelHttp*")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .toD("{{justin.host}}/authUsers?rcc_id=${header.number}")
    .log("Received response from JUSTIN: '${body}'")
    .unmarshal().json(JsonLibrary.Jackson, JustinAuthUsersList.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) {
        JustinAuthUsersList j = exchange.getIn().getBody(JustinAuthUsersList.class);
        CommonAuthUsersList b = new CommonAuthUsersList(j);
        exchange.getMessage().setBody(b, CommonAuthUsersList.class);
      }
    })
    .marshal().json(JsonLibrary.Jackson, CommonAuthUsersList.class)
    .log("Converted response (from JUSTIN to Business model): '${body}'")
    ;

    from("platform-http:/getCourtCaseMetadata")
    .routeId("getCourtCaseMetadata")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("getCourtCaseMetadata request received. mdoc_no = ${header.number}")
    .removeHeader("CamelHttpUri")
    .removeHeader("CamelHttpBaseUri")
    .removeHeaders("CamelHttp*")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .toD("{{justin.host}}/courtFile?mdoc_justin_no=${header.number}")
    .log("Received response from JUSTIN: '${body}'")
    .unmarshal().json(JsonLibrary.Jackson, JustinCourtFile.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) {
        JustinCourtFile j = exchange.getIn().getBody(JustinCourtFile.class);
        CommonCourtCaseMetadataData b = new CommonCourtCaseMetadataData(j);
        exchange.getMessage().setBody(b, CommonCourtCaseMetadataData.class);
      }
    })
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseMetadataData.class)
    .log("Converted response (from JUSTIN to Business model): '${body}'")
    ;

    from("platform-http:/getCourtCaseAppearanceSummaryList")
    .routeId("getCourtCaseAppearanceSummaryList")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("getCourtCaseAppearanceSummaryList request received. mdoc_no = ${header.number}")
    .removeHeader("CamelHttpUri")
    .removeHeader("CamelHttpBaseUri")
    .removeHeaders("CamelHttp*")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .toD("{{justin.host}}/apprSummary?mdoc_justin_no=${header.number}")
    .log("Received response from JUSTIN: '${body}'")
    .unmarshal().json(JsonLibrary.Jackson, JustinCourtAppearanceSummaryList.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) {
        JustinCourtAppearanceSummaryList j = exchange.getIn().getBody(JustinCourtAppearanceSummaryList.class);
        CommonCourtCaseAppearanceSummaryList b = new CommonCourtCaseAppearanceSummaryList(j);
        exchange.getMessage().setBody(b, CommonCourtCaseAppearanceSummaryList.class);
      }
    })
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseAppearanceSummaryList.class)
    .log("Converted response (from JUSTIN to Business model): '${body}'")
    ;

    from("platform-http:/getCourtCaseCrownAssignmentList")
    .routeId("getCourtCaseCrownAssignmentList")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .log("getCourtCaseCrownAssignmentList request received. mdoc_no = ${header.number}")
    .removeHeader("CamelHttpUri")
    .removeHeader("CamelHttpBaseUri")
    .removeHeaders("CamelHttp*")
    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .toD("{{justin.host}}/crownAssignments?mdoc_justin_no=${header.number}")
    .log("Received response from JUSTIN: '${body}'")
    .unmarshal().json(JsonLibrary.Jackson, JustinCrownAssignmentList.class)
    .process(new Processor() {
      @Override
      public void process(Exchange exchange) {
        JustinCrownAssignmentList j = exchange.getIn().getBody(JustinCrownAssignmentList.class);
        CommonCourtCaseCrownAssignmentList b = new CommonCourtCaseCrownAssignmentList(j);
        exchange.getMessage().setBody(b, CommonCourtCaseCrownAssignmentList.class);
      }
    })
    .marshal().json(JsonLibrary.Jackson, CommonCourtCaseCrownAssignmentList.class)
    .log("Converted response (from JUSTIN to Business model): '${body}'")
    ;


    from("direct:logSplunkEvent")
    .routeId("logSplunkEvent")
    .streamCaching() // https://camel.apache.org/manual/faq/why-is-my-message-body-empty.html
    .setProperty("splunk_event", simple("${bodyAs(String)}"))
    .log("Processing Splunk event for message: ${exchangeProperty.splunk_event}")
    .log("Message event ${exchangeProperty.event_message_id}")
    .process(new Processor() {
      @Override
      public void process(Exchange ex) {
        CommonSplunkEvent be = new CommonSplunkEvent(ex.getProperty("splunk_event").toString());
        be.setSource("ccm-justin-adapter");
        be.setEvent_object_id(ex.getProperty("event_message_id").toString());

        ex.getMessage().setBody(be, CommonSplunkEvent.class);
      }
    })
    .marshal().json(JsonLibrary.Jackson, CommonSplunkEvent.class)
    .log("Logging event to splunk body: ${body}")
    .to("kafka:{{kafka.topic.kpis.name}}")
    ;




  }
}