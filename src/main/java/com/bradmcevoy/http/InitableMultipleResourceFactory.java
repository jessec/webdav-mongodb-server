package com.bradmcevoy.http;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author brad
 */
public class InitableMultipleResourceFactory extends MultipleResourceFactory {

    private Logger log = LoggerFactory.getLogger(InitableMultipleResourceFactory.class);

    public InitableMultipleResourceFactory() {
        super();
    }

    public InitableMultipleResourceFactory( List<ResourceFactory> factories ) {
        super( factories );
    }

    public void init(ApplicationConfig config, HttpManager manager) {
        String sFactories = config.getInitParameter("resource.factory.multiple");
        init(sFactories, config, manager);
    }


    protected void init(String sFactories,ApplicationConfig config, HttpManager manager) {
        log.debug("init: " + sFactories );
        String[] arr = sFactories.split(",");
        for(String s : arr ) {
            createFactory(s,config,manager);
        }
    }

    private void createFactory(String s,ApplicationConfig config, HttpManager manager) {
        log.debug("createFactory: " + s);
        Class c;
        try {
            c = Class.forName(s);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(s,ex);
        }
        Object o;
        try {
            o = c.newInstance();
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(s,ex);
        } catch (InstantiationException ex) {
            throw new RuntimeException(s,ex);
        }
        ResourceFactory rf = (ResourceFactory) o;
        if( rf instanceof Initable ) {
            Initable i = (Initable)rf;
            i.init(config,manager);
        }
        factories.add(rf);
    }
    

    public void destroy(HttpManager manager) {
        if( factories == null ) {
            log.warn("factories is null");
            return ;
        }
        for( ResourceFactory f : factories ) {
            if( f instanceof Initable ) {
                ((Initable)f).destroy(manager);
            }
        }
    }
}
