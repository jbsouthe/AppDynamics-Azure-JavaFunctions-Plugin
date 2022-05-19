package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ServletContext;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Plugin to support BT creation from Azure Java Functions, this is troublesome in configuration because of some utility methods needed to name the function
Author: John Southerland josouthe@cisco.com 03/05/2021 initial version
Customers: MGM Resorts
 */
public class InvocationRequestHandlerInterceptor extends MyBaseInterceptor {

    IReflector getFunctionId, getInvocationId; //InvocationRequest
    IReflector brokerAttribute; //InvocationRequestHandler
    IReflector getMethodName; //JavaFunctionBroker
    IReflector getHeaders, getHttpMethod, getQueryParameters, getUri; //InvocationRequest - in a round about way, HttpRequestMessage Interface

    public InvocationRequestHandlerInterceptor() {
        super();

        getFunctionId = makeInvokeInstanceMethodReflector("getFunctionId"); //String
        getInvocationId = makeInvokeInstanceMethodReflector( "getInvocationId" ); //String
        brokerAttribute = makeAccessFieldValueReflector( "broker" ); //Object
        getMethodName = makeInvokeInstanceMethodReflector( "getMethodName", String.class.getCanonicalName()); //String

        getHeaders = makeInvokeInstanceMethodReflector( "getHeaders" ); // Map<String,String>
        getHttpMethod = makeInvokeInstanceMethodReflector( "getHttpMethod" ); //Object HttpMethod Enum
        getQueryParameters = makeInvokeInstanceMethodReflector( "getQueryParameters" ); // Map<String,String>
        getUri = makeInvokeInstanceMethodReflector( "getUri" ); //java.net.URI

    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        /* Reference https://github.com/Azure/azure-functions-java-worker/blob/dev/src/main/java/com/microsoft/azure/functions/worker/handler/InvocationRequestHandler.java
    String execute(InvocationRequest request, InvocationResponse.Builder response) throws Exception {
        final String functionId = request.getFunctionId();
        final String invocationId = request.getInvocationId();

        this.invocationLogger = WorkerLogManager.getInvocationLogger(invocationId);
        response.setInvocationId(invocationId);

        List<ParameterBinding> outputBindings = new ArrayList<>();
        this.broker.invokeMethod(functionId, request, outputBindings).ifPresent(response::setReturnValue);
        response.addAllOutputData(outputBindings);

        return String.format("Function \"%s\" (Id: %s) invoked by Java Worker",
                this.broker.getMethodName(functionId).orElse("UNKNOWN"), invocationId);
    }

         */
        rules.add( new Rule.Builder(
                "com.microsoft.azure.functions.worker.handler.InvocationRequestHandler")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("execute")
                .build()
        );

        rules.add( new Rule.Builder(
                "com.microsoft.azure.functions.worker.handler.InvocationRequestHandler")
                .classMatchType(SDKClassMatchType.INHERITS_FROM_CLASS)
                .methodMatchString("execute")
                .build()
        );

        return rules;
    }

    @Override
    public Object onMethodBegin(Object object, String className, String methodName, Object[] params) {
        this.getLogger().debug("InvocationRequestHandlerInterceptor.onMethodBegin() start: "+ className +"."+ methodName +"()");
        Transaction transaction = null;
        Object invocationRequestHandler = object;
        Object request = params[0];

        String functionId = getReflectiveString( request, getFunctionId , "UNKNOWN-FUNCTIONID");
        String invocationId = getReflectiveString( request, getInvocationId, "UNKNOWN-INVOCATIONID" );
        Object broker = getReflectiveObject( invocationRequestHandler, brokerAttribute );
        String azureMethodName = "UNKNOWN-METHODNAME";
        String methodNameReturned = (String) getReflectiveObject( broker, getMethodName, functionId);
        if( methodNameReturned != null )
            azureMethodName = methodNameReturned;
        URL url = null;
        URI uri = null;
        try {
            uri = (URI) getReflectiveObject( request, getUri );
            if( uri != null ) {
                url = uri.toURL();
                this.getLogger().debug("InvocationRequestHandlerInterceptor.onMethodBegin() succeeded in converting URI: \""+ uri +"\" to URL: \""+ url +"\"");
            } else {
                this.getLogger().debug("InvocationRequestHandlerInterceptor.onMethodBegin() URI in request was null?!");
            }
        } catch (MalformedURLException e) {
            this.getLogger().debug("InvocationRequestHandlerInterceptor.onMethodBegin() failed to convert URI to URL: \""+ uri +"\" Exception: "+ e.getMessage() );
        }
        if( url == null ) {
            transaction = AppdynamicsAgent.startTransaction(azureMethodName, getCorrelationHeader(request), EntryTypes.POJO, false);
        } else {
            transaction = AppdynamicsAgent.startServletTransaction( buildServletContext( url, request), EntryTypes.HTTP, getCorrelationHeader(request),  false);
        }
        collectData( transaction, "Azure-FunctionId", functionId);
        collectData( transaction, "Azure-InvocationId", invocationId);
        collectData( transaction, "Azure-FunctionName", azureMethodName);
        this.getLogger().debug("InvocationRequestHandlerInterceptor.onMethodBegin() end: "+ className +"."+ methodName +"()");
        return transaction;
    }

    private ServletContext buildServletContext(URL url, Object request) {
        this.getLogger().debug("InvocationRequestHandlerInterceptor.buildServletContext() start for URL: "+ url);
        ServletContext.ServletContextBuilder builder = new ServletContext.ServletContextBuilder();
        builder.withURL(url);
        builder.withRequestMethod( getReflectiveString(request, getHttpMethod, "POST" ));
        builder.withHeaders( (Map<String,String>) getReflectiveObject(request, getHeaders));
        Map<String,String[]> appdParameters = new HashMap<>();
        Map<String,String> parameters = (Map<String, String>) getReflectiveObject( request, getQueryParameters);
        for( String key : parameters.keySet() ) appdParameters.put( key, new String[]{ parameters.get(key) });
        builder.withParameters( appdParameters );
        builder.withHostValue( url.getHost() );
        this.getLogger().debug("InvocationRequestHandlerInterceptor.buildServletContext() end for URL: "+ url);
        return builder.build();
    }

    private String getCorrelationHeader(Object request) {
        String correlationHeader = null;
        Map<String,String> headers = (Map<String,String>) getReflectiveObject(request, getHeaders);
        if( headers != null ) correlationHeader = headers.get(CORRELATION_HEADER_KEY);
        this.getLogger().debug("InvocationRequestHandlerInterceptor.getCorrelationHeader() correlation header: "+correlationHeader);
        return correlationHeader;
    }

    @Override
    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        this.getLogger().debug("InvocationRequestHandlerInterceptor.onMethodEnd() start: "+ className +"."+ methodName +"()");
        Transaction transaction = (Transaction) state;
        if( exception != null )
            transaction.markAsError( exception.getMessage() );
        collectData( transaction, "Azure-FunctionResponse", returnVal.toString() );
        transaction.end();
        this.getLogger().debug("InvocationRequestHandlerInterceptor.onMethodEnd() end: "+ className +"."+ methodName +"()");
    }


}
