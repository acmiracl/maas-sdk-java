package com.miracl.maas_sdk;

import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.SerializeException;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.UserInfoErrorResponse;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.UserInfoSuccessResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class MiraclClient
{
	private static final String KEY_STATE = "miracl_state";
	private static final String KEY_NONCE = "miracl_nonce";
	private static final String KEY_TOKEN = "miracl_token";
	private static final String KEY_USERINFO = "miracl_userinfo";

	private final ClientID clientId;
	private final Secret clientSecret;
	private final URI redirectUrl;
	private final OIDCProviderMetadata providerMetadata;

	public MiraclClient(String clientId, String clientSecret, String redirectUrl) throws MiraclException
	{
		try
		{
			this.clientId = new ClientID(clientId);
			this.clientSecret = new Secret(clientSecret);
			this.redirectUrl = new URI(redirectUrl);
			providerMetadata = OIDCProviderMetadata.parse(
					"{" +
					"\"request_parameter_supported\":false," +
					"\"display_values_supported\":[\"page\",\"popup\"]," +
					"\"response_types_supported\":[" +
					"\"code\",\"id_token\",\"token id_token\",\"code id_token\"" +
					"]," +
					"\"token_endpoint_auth_signing_alg_values_supported\":[" +
					"\"HS256\",\"HS512\",\"HS384\"" +
					"]," +
					"\"registration_endpoint\":\"https:\\/\\/m-pin.my.id\\/c2id\\/client-reg\"," +
					"\"ui_locales_supported\":[\"en\"]," +
					"\"userinfo_signing_alg_values_supported\":[" +
					"\"PS256\",\"HS256\",\"RS384\",\"HS512\",\"RS512\",\"PS384\",\"RS256\",\"HS384\",\"PS512\"" +
					"]," +
					"\"token_endpoint\":\"https:\\/\\/m-pin.my.id\\/c2id\\/token\"," +
					"\"claim_types_supported\":[\"normal\"]," +
					"\"grant_types_supported\":[\"implicit\",\"authorization_code\"]," +
					"\"scopes_supported\":[\"openid\",\"profile\",\"email\",\"address\",\"phone\",\"offline_access\"]," +
					"\"request_uri_parameter_supported\":false," +
					"\"acr_values_supported\":[\"0\"]," +
					"\"userinfo_endpoint\":\"https:\\/\\/m-pin.my.id\\/c2id\\/userinfo\"," +
					"\"token_endpoint_auth_methods_supported\":[" +
					"\"client_secret_post\",\"client_secret_jwt\",\"client_secret_basic\"" +
					"]," +
					"\"subject_types_supported\":[\"public\"]," +
					"\"response_modes_supported\":[\"query\",\"fragment\"]," +
					"\"issuer\":\"https:\\/\\/m-pin.my.id\\/c2id\"," +
					"\"claims_parameter_supported\":true," +
					"\"jwks_uri\":\"https:\\/\\/m-pin.my.id\\/c2id\\/jwks.json\"," +
					"\"claims_supported\":[" +
					"\"sub\",\"iss\",\"auth_time\",\"acr\",\"name\",\"given_name\",\"family_name\",\"nickname\",\"email\",\"email_verified\"" +
					"]," +
					"\"id_token_signing_alg_values_supported\":[" +
					"\"RS256\",\"RS384\",\"RS512\",\"PS256\",\"PS384\",\"PS512\",\"HS256\",\"HS384\",\"HS512\"" +
					"]," +
					"\"authorization_endpoint\":\"https:\\/\\/m-pin.my.id\\/abstractlogin\"," +
					"\"require_request_uri_registration\":false}"
			                                             );
		}
		catch (URISyntaxException | ParseException e)
		{
			throw new MiraclSystemException(e);
		}
	}

	public URI getAuthorizationRequestUrl(MiraclStatePreserver preserver)
	{
		State state = new State();
		Nonce nonce = new Nonce();
		preserver.put(KEY_STATE, state.getValue());
		preserver.put(KEY_NONCE, nonce.getValue());

		Scope scope = new Scope();
		scope.add("openid");

		AuthenticationRequest authenticationRequest = new AuthenticationRequest(
				providerMetadata.getAuthorizationEndpointURI(),
				new ResponseType(ResponseType.Value.CODE),
				scope, clientId, redirectUrl, state, nonce);

		return authenticationRequest.toURI();
	}

	public String validateAuthorization(MiraclStatePreserver preserver, String queryString) throws MiraclException
	{
		try
		{
			final AuthenticationResponse response;
			try
			{
				response = AuthenticationResponseParser.parse(URI.create("/?" + queryString));
			}
			catch (ParseException e)
			{
				throw new MiraclClientException(e);
			}
			if (response instanceof AuthenticationErrorResponse)
			{
				ErrorObject error = ((AuthenticationErrorResponse) response).getErrorObject();
				throw new Error(error.getDescription());
			}

			AuthenticationSuccessResponse successResponse = (AuthenticationSuccessResponse) response;

			final boolean stateOk = successResponse.getState().toString().equals(preserver.get(KEY_STATE));
			if (stateOk)
			{
				TokenRequest tokenRequest = new TokenRequest(
						providerMetadata.getTokenEndpointURI(),
						new ClientSecretBasic(clientId, clientSecret),
						new AuthorizationCodeGrant(((AuthenticationSuccessResponse) response).getAuthorizationCode(), redirectUrl)
				);
				final TokenResponse tokenResponse = OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());
				if (tokenResponse instanceof TokenErrorResponse)
				{
					ErrorObject error = ((TokenErrorResponse) tokenResponse).getErrorObject();
					throw new Error(error.getDescription());
				}

				OIDCTokenResponse accessTokenResponse = (OIDCTokenResponse) tokenResponse;
				final AccessToken accessToken = accessTokenResponse.getOIDCTokens().getAccessToken();

				preserver.put(KEY_TOKEN, accessToken.getValue());
				return accessToken.getValue();
			}
		}
		catch (ParseException | IOException e)
		{
			e.printStackTrace();
			throw new MiraclSystemException(e);
		}

		return null;
	}


	public void clearUserInfo(MiraclStatePreserver preserver)
	{
		preserver.remove(KEY_USERINFO);
	}

	public void clearUserInfoAndSession(MiraclStatePreserver preserver)
	{
		clearUserInfo(preserver);
		preserver.remove(KEY_TOKEN);
	}

	private UserInfo requestUserInfo(MiraclStatePreserver preserver) throws MiraclException
	{
		if (preserver.get(KEY_TOKEN) == null)
		{
			throw new MiraclClientException("User is not authorized");
		}

		if (preserver.get(KEY_USERINFO) != null)
		{
			final UserInfo userInfo;
			try
			{
				userInfo = UserInfo.parse(preserver.get(KEY_USERINFO));
				return userInfo;
			}
			catch (ParseException e)
			{
				//If problems with userinfo parsing, remove it and continue with obtaining new userinfo
				preserver.remove(KEY_USERINFO);
			}
		}

		final BearerAccessToken accessToken = new BearerAccessToken(preserver.get(KEY_TOKEN));
		UserInfoRequest userInfoReq = new UserInfoRequest(
				providerMetadata.getUserInfoEndpointURI(),
				accessToken);

		try
		{
			HTTPResponse userInfoHTTPResp = userInfoReq.toHTTPRequest().send();
			UserInfoResponse userInfoResponse = UserInfoResponse.parse(userInfoHTTPResp);

			if (userInfoResponse instanceof UserInfoErrorResponse)
			{
				ErrorObject error = ((UserInfoErrorResponse) userInfoResponse).getErrorObject();
				throw new MiraclClientException(error);
			}

			UserInfoSuccessResponse successResponse = (UserInfoSuccessResponse) userInfoResponse;
			final UserInfo userInfo = successResponse.getUserInfo();
			preserver.put(KEY_USERINFO, userInfo.toJSONObject().toJSONString());
			return userInfo;

		}
		catch (SerializeException | IOException | ParseException e)
		{
			throw new MiraclSystemException(e);
		}
	}

	public boolean isAuthorized(MiraclStatePreserver preserver)
	{
		try
		{
			return requestUserInfo(preserver) != null;
		}
		catch (MiraclClientException e)
		{
			return false;
		}
	}

	public String getEmail(MiraclStatePreserver preserver) throws MiraclException
	{
		final UserInfo userInfo = requestUserInfo(preserver);
		if (userInfo != null)
		{
			return userInfo.getStringClaim("sub");
		}
		return null;
	}


	public String getUserId(MiraclStatePreserver preserver) throws MiraclException
	{
		final UserInfo userInfo = requestUserInfo(preserver);
		if (userInfo != null)
		{
			return userInfo.getStringClaim("user_id");
		}
		return null;
	}
}
