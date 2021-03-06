package com.andybotting.oystermate.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.andybotting.oystermate.utils.PreferenceHelper;

import android.util.Log;

public class HttpConnection {
	
    private static final String TAG = "OysterMate";
    private static final boolean LOGV = Log.isLoggable(TAG, Log.INFO);
    
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; U; Android 2.3.3; en-gb; Build/GRI40) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1";
	
	private PreferenceHelper mPreferenceHelper;
	
	// 20 seconds timeout
	private static final int HTTP_CONNECTION_TIMEOUT = 20000;
	private static final int HTTP_SOCKET_TIMEOUT = 20000;
	
	
	/**
	 * HttpConnection
	 */
	public HttpConnection() {
		this.mPreferenceHelper = new PreferenceHelper();
	}

	
	/**
	 * HTTP GET a URL
	 */
	public String getURL(String url) throws IOException {
		if (LOGV) Log.i(TAG, "Fetching URL: " + url);
		String output = null;
		
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, HTTP_CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpParameters, HTTP_SOCKET_TIMEOUT);
        
        DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
		
		// Don't handle 302 redirects automatically. We want to test for it
		httpClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
		
	    HttpGet httpGet = new HttpGet(url);
	    httpGet.setHeader("User-Agent", USER_AGENT);
	    httpGet.setHeader("Cookie", "JSESSIONID=" + mPreferenceHelper.getSessionId());

	    HttpResponse httpResponse = httpClient.execute(httpGet);
	    
	    int responseCode = httpResponse.getStatusLine().getStatusCode();
	    if (LOGV) Log.i(TAG, "Return code for " + url + " is: " + responseCode);
	    
	    String location = null;
	    for (Header header : httpResponse.getAllHeaders()) {
	    	if (header.getName().matches("Location"))
	    		location = header.getValue();
	    }
	    
        if (responseCode == 302 && location.matches(OysterProvider.LOGGED_IN_URL)) {
        	// Do a login to get the session cookie
        	performLogin();
        	if (mPreferenceHelper.hasSessionId())
        		return getURL(url);
        }	    

		HttpEntity entity = httpResponse.getEntity();
		output = convertStreamToString(entity.getContent());	
		
		//System.out.println(output);
		return output;
	}
	
	
	/**
	 * HTTP POST a URL with given data
	 */
	public String postURL(String url, List<NameValuePair> nameValuePairs) throws IOException {
		if (LOGV) Log.i(TAG, "Posting URL: " + url);
		String output = null;
        
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, HTTP_CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpParameters, HTTP_SOCKET_TIMEOUT);

        DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
        
	    HttpPost httpPost = new HttpPost(url);
	    httpPost.setHeader("User-Agent", USER_AGENT);
	    httpPost.setHeader("Cookie", "JSESSIONID=" + mPreferenceHelper.getSessionId());
		httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		
		HttpResponse httpResponse = httpClient.execute(httpPost);
			
		// Perform the http request, and forget about the output
		HttpEntity entity = httpResponse.getEntity();
		output = convertStreamToString(entity.getContent());
		
		//System.out.println(output);
		return output;
        
	}
	

	/**
	 * Perform a login using stored credentials
	 */
	public String performLogin() throws IOException {
		String username = mPreferenceHelper.getUsername();
		String password = mPreferenceHelper.getPassword();
		
		return performLogin(username, password);
	}
	
	
	/**
	 * Perform login with given username and password
	 */
	public String performLogin(String username, String password) throws IOException {
		if (LOGV) Log.i(TAG, "Performing Login: " + OysterProvider.LOGIN_POST_URL);
		String output = null;
		
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        nameValuePairs.add(new BasicNameValuePair("j_username", username));
        nameValuePairs.add(new BasicNameValuePair("j_password", password));
        
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, HTTP_CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpParameters, HTTP_SOCKET_TIMEOUT);
        
        DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
        
	    HttpPost httpPost = new HttpPost(OysterProvider.LOGIN_POST_URL);
	    httpPost.setHeader("User-Agent", USER_AGENT);
		httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		HttpResponse response = httpClient.execute(httpPost);
		
	    int responseCode = response.getStatusLine().getStatusCode();
	    if (LOGV) Log.i(TAG, "Return code for LOGIN " + OysterProvider.LOGIN_POST_URL + " is: " + responseCode);
		
		// Perform the http request, and forget about the output
		HttpEntity entity = response.getEntity();
		output = convertStreamToString(entity.getContent());

		List<Cookie> cookies = httpClient.getCookieStore().getCookies();
		
		if (!cookies.isEmpty()) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().matches("JSESSIONID"))
					mPreferenceHelper.setSessionId(cookie.getValue());
			}
		}
		
		//System.out.println(output);
		return output;
        
	}
	

	/**
	 * Build a string from the http output 
	 */
	private static String convertStreamToString(InputStream is) {
		/*
		 * To convert the InputStream to String we use the BufferedReader.readLine()
		 * method. We iterate until the BufferedReader return null which means
		 * there's no more data to read. Each line will appended to a StringBuilder
		 * and returned as String.
		 */
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
				//sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return sb.toString();
	}	

}
