package com.bradmcevoy.http.http11.auth;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author brad
 */
public class DigestGenerator {

    private static final Logger log = LoggerFactory.getLogger( DigestGenerator.class );

    /**
     * Computes the <code>response</code> portion of a Digest authentication header. Both the server and user
     * agent should compute the <code>response</code> independently. Provided as a static method to simplify the
     * coding of user agents.
     *
     * @param dr - the auth request from the client
     * @param password - plain text unencoded password
     * @return the MD5 of the digest authentication response, encoded in hex
     * @throws IllegalArgumentException if the supplied qop value is unsupported.
     */
    public String generateDigest( DigestResponse dr, String password ) throws IllegalArgumentException {
        log.debug( "user:" + dr.getUser() + ":realm:" + dr.getRealm() + ":" + password );
        String p = password == null ? "" : password;
        String a1Md5 = encodePasswordInA1Format( dr.getUser(), dr.getRealm(), p );
        return generateDigestWithEncryptedPassword( dr, a1Md5 );
    }

    /**
     * Use this method if you are persisting a one way hash of the user name, password
     * and realm (referred to as a1md5 in the spec)
     *
     * @param dr
     * @param a1Md5
     * @return
     * @throws IllegalArgumentException
     */
    public String generateDigestWithEncryptedPassword( DigestResponse dr, String a1Md5 ) throws IllegalArgumentException {
        String httpMethod = dr.getMethod().code;
        String a2Md5 = encodeMethodAndUri( httpMethod, dr.getUri() );

        String qop = dr.getQop();
        String nonce = dr.getNonce();

        //String digest;
        if( qop == null ) {
            // as per RFC 2069 compliant clients (also reaffirmed by RFC 2617)
            //digest = a1Md5 + ":" + dr.getNonce() + ":" + a2Md5;
            return md5( a1Md5, dr.getNonce(), a2Md5 );
        } else if( "auth".equals( qop ) ) {
            // As per RFC 2617 compliant clients
            return md5( a1Md5, nonce, dr.getNc(), dr.getCnonce(), dr.getQop(), a2Md5 );
            //digest = a1Md5 + ":" + nonce + ":" + dr.getNc() + ":" + dr.getCnonce() + ":" + qop + ":" + a2Md5;
        } else {
            throw new IllegalArgumentException( "This method does not support a qop '" + qop + "'" );
        }
    }

    public String encodePasswordInA1Format( String username, String realm, String password ) {
        String a1 = username + ":" + realm + ":" + password;
        String a1Md5 = new String( DigestUtils.md5Hex( a1 ) );

        return a1Md5;
    }

    String encodeMethodAndUri( String httpMethod, String uri ) {
        String a2 = httpMethod + ":" + uri;
        String a2Md5 = new String( DigestUtils.md5Hex( a2 ) );
        return a2Md5;
    }

    String md5( String... ss ) {
        String d = "";
        for( int i = 0; i < ss.length; i++ ) {
            if( i > 0 ) d += ":";
            d = d + ss[i];
        }
        return new String( DigestUtils.md5Hex( d ) );
    }
}
