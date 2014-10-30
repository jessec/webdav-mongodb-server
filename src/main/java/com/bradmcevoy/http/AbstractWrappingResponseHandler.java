package com.bradmcevoy.http;

import com.bradmcevoy.http.Response.Status;
import com.bradmcevoy.http.exceptions.BadRequestException;
import com.bradmcevoy.http.exceptions.NotFoundException;
import com.bradmcevoy.http.quota.StorageChecker.StorageErrorReason;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.bradmcevoy.http.webdav.PropFindResponse;
import com.bradmcevoy.http.webdav.WebDavResponseHandler;

/**
 * Response Handler which wraps another
 *
 * @author brad
 */
public abstract class AbstractWrappingResponseHandler implements WebDavResponseHandler {

    private static final Logger log = LoggerFactory.getLogger( AbstractWrappingResponseHandler.class );
    /**
     * The underlying respond handler which takes care of actually generating
     * content
     */
    protected WebDavResponseHandler wrapped;

    public AbstractWrappingResponseHandler() {
    }

    public AbstractWrappingResponseHandler( WebDavResponseHandler wrapped ) {
        this.wrapped = wrapped;
    }

	@Override
    public String generateEtag( Resource r ) {
        return wrapped.generateEtag( r );
    }

	@Override
    public void respondContent( Resource resource, Response response, Request request, Map<String, String> params ) throws NotAuthorizedException, BadRequestException, NotFoundException {
        wrapped.respondContent( resource, response, request, params );
    }

    public void setWrapped( WebDavResponseHandler wrapped ) {
        this.wrapped = wrapped;
    }

    public WebDavResponseHandler getWrapped() {
        return wrapped;
    }

	@Override
    public void respondNoContent( Resource resource, Response response, Request request ) {
        wrapped.respondNoContent( resource, response, request );
    }

	@Override
    public void respondPartialContent( GetableResource resource, Response response, Request request, Map<String, String> params, Range range ) throws NotAuthorizedException, BadRequestException, NotFoundException {
        wrapped.respondPartialContent( resource, response, request, params, range );
    }

	@Override
    public void respondCreated( Resource resource, Response response, Request request ) {
        wrapped.respondCreated( resource, response, request );
    }

    public void respondUnauthorised( Resource resource, Response response, Request request ) {
        wrapped.respondUnauthorised( resource, response, request );
    }

    public void respondMethodNotImplemented( Resource resource, Response response, Request request ) {
        wrapped.respondMethodNotImplemented( resource, response, request );
    }

    public void respondMethodNotAllowed( Resource res, Response response, Request request ) {
        wrapped.respondMethodNotAllowed( res, response, request );
    }

    public void respondConflict( Resource resource, Response response, Request request, String message ) {
        wrapped.respondConflict( resource, response, request, message );
    }

    public void respondRedirect( Response response, Request request, String redirectUrl ) {
        wrapped.respondRedirect( response, request, redirectUrl );
    }

    public void responseMultiStatus( Resource resource, Response response, Request request, List<HrefStatus> statii ) {
        wrapped.responseMultiStatus( resource, response, request, statii );
    }

    public void respondNotModified( GetableResource resource, Response response, Request request ) {
        log.trace( "respondNotModified" );
        wrapped.respondNotModified( resource, response, request );
    }

    public void respondNotFound( Response response, Request request ) {
        wrapped.respondNotFound( response, request );
    }

    public void respondWithOptions( Resource resource, Response response, Request request, List<String> methodsAllowed ) {
        wrapped.respondWithOptions( resource, response, request, methodsAllowed );
    }

    public void respondHead( Resource resource, Response response, Request request ) {
        wrapped.respondHead( resource, response, request );
    }

    public void respondExpectationFailed( Response response, Request request ) {
        wrapped.respondExpectationFailed( response, request );
    }

    public void respondBadRequest( Resource resource, Response response, Request request ) {
        wrapped.respondBadRequest( resource, response, request );
    }

    public void respondForbidden( Resource resource, Response response, Request request ) {
        wrapped.respondForbidden( resource, response, request );
    }

    public void respondDeleteFailed( Request request, Response response, Resource resource, Status status ) {
        wrapped.respondDeleteFailed( request, response, resource, status );
    }

    public void respondPropFind( List<PropFindResponse> propFindResponses, Response response, Request request, PropFindableResource pfr ) {
        wrapped.respondPropFind( propFindResponses, response, request, pfr );
    }

    public void respondPropFind( List<PropFindResponse> propFindResponses, Response response, Request request, Resource r ) {
        wrapped.respondPropFind( propFindResponses, response, request, r );
    }

    public void respondServerError( Request request, Response response, String reason ) {
        wrapped.respondServerError( request, response, reason );
    }

    public void respondInsufficientStorage( Request request, Response response, StorageErrorReason storageErrorReason ) {
        wrapped.respondInsufficientStorage( request, response, storageErrorReason );
    }

    public void respondLocked( Request request, Response response, Resource existingResource ) {
        wrapped.respondLocked( request, response, existingResource );
    }

    public void respondPreconditionFailed( Request request, Response response, Resource resource ) {
        wrapped.respondPreconditionFailed( request, response, resource );
    }
}
