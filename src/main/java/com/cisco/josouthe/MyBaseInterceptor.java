package com.cisco.josouthe;

import com.appdynamics.agent.api.Transaction;
import com.appdynamics.apm.appagent.api.DataScope;
import com.appdynamics.instrumentation.sdk.template.AGenericInterceptor;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.util.HashSet;
import java.util.Set;

public abstract class MyBaseInterceptor extends AGenericInterceptor {
    protected static final Object CORRELATION_HEADER_KEY = "singularityheader";
    protected Set<DataScope> dataScopes = null;
    protected static final String DISABLE_ANALYTICS_COLLECTION_PROPERTY = "disablePluginAnalytics";

    protected IReflector makeAccessFieldValueReflector(String field ) {
        return getNewReflectionBuilder().accessFieldValue( field, true).build();
    }

    protected IReflector makeInvokeInstanceMethodReflector(String method, String...args ) {
        if( args.length > 0 ) return getNewReflectionBuilder().invokeInstanceMethod( method, true, args).build();
        return getNewReflectionBuilder().invokeInstanceMethod( method, true).build();
    }

    protected IReflector makeCreateObjectReflector( String className, String... args ) {
        return getNewReflectionBuilder().createObject(className, args).build();
    }

    protected IReflector makeLoadClassReflector( String className ) {
        return getNewReflectionBuilder().loadClass(className).build();
    }

    protected IReflector makeInvokeStaticMethodReflector( String method, String... args ) {
        return getNewReflectionBuilder().invokeStaticMethod( method, true, args ).build();
    }

    protected String getReflectiveString(Object object, IReflector method, String defaultString) {
        String value = defaultString;
        if( object == null || method == null ) return defaultString;
        try{
            value = (String) method.execute(object.getClass().getClassLoader(), object);
        } catch (ReflectorException e) {
            this.getLogger().debug("Error in reflection call, exception: "+ e.getMessage(),e);
        }
        return value;
    }

    protected Integer getReflectiveInteger(Object object, IReflector method, Integer defaultInteger) {
        Integer value = defaultInteger;
        if( object == null || method == null ) return defaultInteger;
        try{
            value = (Integer) method.execute(object.getClass().getClassLoader(), object);
        } catch (ReflectorException e) {
            this.getLogger().debug("Error in reflection call, exception: "+ e.getMessage(),e);
        }
        return value;
    }

    protected Object getReflectiveObject(Object object, IReflector method, Object... args) {
        Object value = null;
        if( object == null || method == null ) return value;
        try{
            if( args.length > 0 ) {
                value = method.execute(object.getClass().getClassLoader(), object, args);
            } else {
                value = method.execute(object.getClass().getClassLoader(), object);
            }
        } catch (ReflectorException e) {
            this.getLogger().debug("Error in reflection call, method: "+ method.getClass().getCanonicalName() +" object: "+ object.getClass().getCanonicalName() +" exception: "+ e.getMessage(),e);
        }
        return value;
    }

    protected void collectData(Transaction transaction, String name, String value ) {
        if( dataScopes == null ) {
            dataScopes = new HashSet<DataScope>();

            dataScopes.add(DataScope.SNAPSHOTS);
            if( System.getProperty(DISABLE_ANALYTICS_COLLECTION_PROPERTY,"false").equalsIgnoreCase("false") ) {
                dataScopes.add(DataScope.ANALYTICS);
                this.getLogger().info("Enabling Analytics Collection of Plugin Custom Data, to disable add JVM property -D"+ DISABLE_ANALYTICS_COLLECTION_PROPERTY +"=true");
            }
        }
        transaction.collectData( name, value, this.dataScopes );
    }
}
