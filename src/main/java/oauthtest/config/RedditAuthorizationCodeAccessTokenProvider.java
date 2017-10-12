package oauthtest.config;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.client.filter.state.DefaultStateKeyGenerator;
import org.springframework.security.oauth2.client.filter.state.StateKeyGenerator;
import org.springframework.security.oauth2.client.resource.OAuth2AccessDeniedException;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.resource.UserApprovalRequiredException;
import org.springframework.security.oauth2.client.resource.UserRedirectRequiredException;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.util.Base64Utils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class RedditAuthorizationCodeAccessTokenProvider extends AuthorizationCodeAccessTokenProvider implements Serializable {

    final static Logger log = LoggerFactory.getLogger(RedditAuthorizationCodeAccessTokenProvider.class);

    private static final long serialVersionUID = 3822611002661972274L;

    private final StateKeyGenerator stateKeyGenerator = new DefaultStateKeyGenerator();

    private OAuth2ProtectedResourceDetails details;

    @Override
    public OAuth2AccessToken obtainAccessToken(final OAuth2ProtectedResourceDetails details, final AccessTokenRequest request) throws UserRedirectRequiredException, UserApprovalRequiredException, AccessDeniedException, OAuth2AccessDeniedException {
        final AuthorizationCodeResourceDetails resource = (AuthorizationCodeResourceDetails) details;
        this.details = details;

        if (request.getAuthorizationCode() == null) {
            if (request.getStateKey() == null) {
                throw getRedirectForAuthorization(resource, request);
            }
            obtainAuthorizationCode(resource, request);
        }
        try {
            OAuth2AccessToken accessToken = retrieveToken(request, resource, getParametersForTokenRequest(resource, request), getHeadersForTokenRequest(request));
            log.debug("ACCESS TOKEN OBTAINED " + accessToken.getValue());
            return accessToken;
        }
        catch (OAuth2AccessDeniedException e) {
            log.debug("ACCESS TOKEN ERROR ", e);
            throw e;
        }
    }

    private HttpHeaders getHeadersForTokenRequest(final AccessTokenRequest request) {
        final HttpHeaders headers = new HttpHeaders();
//        String authHeader = "Basic " +
//                Base64Utils.encodeToString((details.getClientId() + ":" + details.getClientSecret()).getBytes());
//        headers.add("Authorization", authHeader);
//        log.debug(authHeader);
//        headers.add("Content-Type", "application/x-www-form-urlencoded");

        headers.set("User-Agent", "web:oauthtest:v0.0.1 (by /u/PigExterminator)");

        return headers;
    }

    private MultiValueMap<String, String> getParametersForTokenRequest(final AuthorizationCodeResourceDetails resource, final AccessTokenRequest request) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.set("grant_type", "authorization_code");
        form.set("code", request.getAuthorizationCode());

        final Object preservedState = request.getPreservedState();
        if (request.getStateKey() != null) {
            if (preservedState == null) {
                throw new InvalidRequestException("Possible CSRF detected - state parameter was present but no state could be found");
            }
        }

        String redirectUri = null;
        if (preservedState instanceof String) {
            redirectUri = String.valueOf(preservedState);
        } else {
            redirectUri = resource.getRedirectUri(request);
        }

        if ((redirectUri != null) && !"NONE".equals(redirectUri)) {
            form.set("redirect_uri", redirectUri);
        }

        return form;
    }

    private UserRedirectRequiredException getRedirectForAuthorization(final AuthorizationCodeResourceDetails resource, final AccessTokenRequest request) {
        final TreeMap<String, String> requestParameters = new TreeMap<String, String>();
        requestParameters.put("response_type", "code");
        requestParameters.put("client_id", resource.getClientId());
        requestParameters.put("duration", "permanent");
        requestParameters.put("scope", "identity");

        final String redirectUri = resource.getRedirectUri(request);
        if (redirectUri != null) {
            requestParameters.put("redirect_uri", redirectUri);
        }

        if (resource.isScoped()) {

            final StringBuilder builder = new StringBuilder();
            final List<String> scope = resource.getScope();

            if (scope != null) {
                final Iterator<String> scopeIt = scope.iterator();
                while (scopeIt.hasNext()) {
                    builder.append(scopeIt.next());
                    if (scopeIt.hasNext()) {
                        builder.append(',');
                    }
                }
            }

            requestParameters.put("scope", builder.toString());
        }

        final UserRedirectRequiredException redirectException = new UserRedirectRequiredException(resource.getUserAuthorizationUri(), requestParameters);

        final String stateKey = stateKeyGenerator.generateKey(resource);
        redirectException.setStateKey(stateKey);
        request.setStateKey(stateKey);
        redirectException.setStateToPreserve(redirectUri);
        request.setPreservedState(redirectUri);

        return redirectException;
    }

}