package com.bradmcevoy.http;

import com.bradmcevoy.http.AuthenticationService.AuthStatus;
import com.bradmcevoy.http.Request.Method;
import com.bradmcevoy.http.Response.Status;
import com.bradmcevoy.http.exceptions.BadRequestException;
import com.bradmcevoy.http.exceptions.ConflictException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.bradmcevoy.http.exceptions.NotFoundException;
import com.bradmcevoy.http.http11.Http11ResponseHandler;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author brad
 */
public class ResourceHandlerHelper {

    private final static Logger log = LoggerFactory.getLogger( ResourceHandlerHelper.class );
    /**
     * request attribute name for the parameters map
     */
    public static final String ATT_NAME_PARAMS = "_params";
    /**
     * request attribute name for the files map
     */
    public static final String ATT_NAME_FILES = "_files";

    private final HandlerHelper handlerHelper;
    private final Http11ResponseHandler responseHandler;
    private UrlAdapter urlAdapter = new UrlAdapterImpl();

    public ResourceHandlerHelper( HandlerHelper handlerHelper, Http11ResponseHandler responseHandler ) {
        if( responseHandler == null )
            throw new IllegalArgumentException( "responseHandler may not be null" );
        if( handlerHelper == null )
            throw new IllegalArgumentException( "handlerHelper may not be null" );
        this.handlerHelper = handlerHelper;
        this.responseHandler = responseHandler;
    }

    public void process( HttpManager manager, Request request, Response response, ResourceHandler handler ) throws NotAuthorizedException, ConflictException, BadRequestException {
        // need a linked hash map to preserve ordering of params
        Map<String, String> params = new LinkedHashMap<String, String>();

        Map<String, FileItem> files = new HashMap<String, FileItem>();

        try {
            request.parseRequestParameters( params, files );
        } catch( RequestParseException ex ) {
            log.warn( "exception parsing request. probably interrupted upload", ex );
            return;
        }
        request.getAttributes().put( ATT_NAME_PARAMS, params );
        request.getAttributes().put( ATT_NAME_FILES, files );

        if( !handlerHelper.checkExpects( responseHandler, request, response ) ) {
            return;
        }
        String host = request.getHostHeader();
        String url = urlAdapter.getUrl( request );
        //log.debug( "find resource: path: " + url + " host: " + host );
        Resource r = manager.getResourceFactory().getResource( host, url );
        if( r == null ) {
            responseHandler.respondNotFound( response, request );
            return;
        }
        handler.processResource( manager, request, response, r );
    }

    public void processResource( HttpManager manager, Request request, Response response, Resource resource, ExistingEntityHandler handler ) throws NotAuthorizedException, ConflictException, BadRequestException {
        processResource( manager, request, response, resource, handler, false, null, null );
    }

    public void processResource( HttpManager manager, Request request, Response response, Resource resource, ExistingEntityHandler handler, Map<String, String> params, Map<String, FileItem> files ) throws NotAuthorizedException, ConflictException, BadRequestException {
        processResource( manager, request, response, resource, handler, false, params, files );
    }

    public void processResource( HttpManager manager, Request request, Response response, Resource resource, ExistingEntityHandler handler, boolean allowRedirect, Map<String, String> params, Map<String, FileItem> files ) throws NotAuthorizedException, ConflictException, BadRequestException {
        log.trace( "processResource" );
        long t = System.currentTimeMillis();
        try {

            manager.onProcessResourceStart( request, response, resource );

            if( handlerHelper.isNotCompatible( resource, request.getMethod() ) || !handler.isCompatible( resource ) ) {
                if( log.isInfoEnabled() ) {
                    log.info( "resource not compatible. Resource class: " + resource.getClass() + " handler: " + handler.getClass() );
                }
                responseHandler.respondMethodNotImplemented( resource, response, request );
                return;
            }

            boolean authorised = handlerHelper.checkAuthorisation( manager, resource, request );

            // redirect check must be after authorisation, because the check redirect
            // logic might depend on logged in user
            // but the actual redirection must be before we respond unathorised, because
            // in some cases we might want to pre-empt the unauthorised status and redirect
            // to a login page
            if( allowRedirect ) {
                log.trace( "check redirect" );
                if( handlerHelper.doCheckRedirect( responseHandler, request, response, resource ) ) {
                    return;
                }
            }

            if( !authorised ) {
                if( log.isInfoEnabled() ) {
                    log.info( "authorisation failed. respond with: " + responseHandler.getClass().getCanonicalName() + " resource: " + resource.getClass().getCanonicalName() );
                }
                responseHandler.respondUnauthorised( resource, response, request );
                return;
            }


            // Do not lock on POST requests. It is up to the application to decide whether or not
            // a POST requires a lock
            if( request.getMethod().isWrite && request.getMethod() != Method.POST ) {
                if( handlerHelper.isLockedOut( request, resource ) ) {
                    response.setStatus( Status.SC_LOCKED ); // replace with responsehandler method
                    return;
                }
            }
			try {
				handler.processExistingResource( manager, request, response, resource );
			} catch (NotFoundException ex) {
				log.warn("Not found exception thrown from handler: " + handler.getClass(), ex);
				responseHandler.respondNotFound(response, request);
			}
        } finally {
            t = System.currentTimeMillis() - t;
            manager.onProcessResourceFinish( request, response, resource, t );
        }
    }

    public boolean isNotCompatible( Resource r, Method m ) {
        return handlerHelper.isNotCompatible( r, m );
    }

    public boolean isLockedOut( Request inRequest, Resource inResource ) {
        return handlerHelper.isLockedOut( inRequest, inResource );
    }

    public AuthStatus checkAuthentication( HttpManager manager, Resource resource, Request request ) {
        return handlerHelper.checkAuthentication( manager, resource, request );
    }

    public UrlAdapter getUrlAdapter() {
        return urlAdapter;
    }

    public void setUrlAdapter( UrlAdapter urlAdapter ) {
        this.urlAdapter = urlAdapter;
    }
}
