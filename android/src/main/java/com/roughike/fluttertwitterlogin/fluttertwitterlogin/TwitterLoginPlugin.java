package com.roughike.fluttertwitterlogin.fluttertwitterlogin;

import android.app.Activity;
import android.content.Intent;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;

import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;

import java.util.HashMap;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry;

public class TwitterLoginPlugin extends Callback<TwitterSession> implements MethodCallHandler, PluginRegistry.ActivityResultListener, FlutterPlugin, ActivityAware {
    private static final String CHANNEL_NAME = "com.roughike/flutter_twitter_login";
    private static final String METHOD_GET_CURRENT_SESSION = "getCurrentSession";
    private static final String METHOD_AUTHORIZE = "authorize";
    private static final String METHOD_LOG_OUT = "logOut";

    private ActivityPluginBinding activityPluginBinding;
    private Activity pluginActivity;
    private MethodChannel channel;

    private TwitterAuthClient authClientInstance;
    private Result pendingResult;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL_NAME);
        this.channel = channel;
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onMethodCall(MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case METHOD_GET_CURRENT_SESSION:
                getCurrentSession(result, call);
                break;
            case METHOD_AUTHORIZE:
                authorize(result, call);
                break;
            case METHOD_LOG_OUT:
                logOut(result, call);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void setPendingResult(String methodName, MethodChannel.Result result) {
        if (pendingResult != null) {
            result.error(
                    "TWITTER_LOGIN_IN_PROGRESS",
                    methodName + " called while another Twitter " +
                            "login operation was in progress.",
                    null
            );
        }

        pendingResult = result;
    }

    private void getCurrentSession(Result result, MethodCall call) {
        initializeAuthClient(call);
        TwitterSession session = TwitterCore.getInstance().getSessionManager().getActiveSession();
        HashMap<String, Object> sessionMap = sessionToMap(session);

        result.success(sessionMap);
    }

    private void authorize(Result result, MethodCall call) {
        setPendingResult("authorize", result);
        initializeAuthClient(call).authorize(pluginActivity, this);
    }

    private TwitterAuthClient initializeAuthClient(MethodCall call) {
        if (authClientInstance == null) {
            String consumerKey = call.argument("consumerKey");
            String consumerSecret = call.argument("consumerSecret");

            authClientInstance = configureClient(consumerKey, consumerSecret);
        }

        return authClientInstance;
    }

    private TwitterAuthClient configureClient(String consumerKey, String consumerSecret) {
        TwitterAuthConfig authConfig = new TwitterAuthConfig(consumerKey, consumerSecret);
        TwitterConfig config = new TwitterConfig.Builder(pluginActivity)
                .twitterAuthConfig(authConfig)
                .build();
        Twitter.initialize(config);

        return new TwitterAuthClient();
    }

    private void logOut(Result result, MethodCall call) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeSessionCookies(
                new ValueCallback<Boolean>() {
                    @Override
                    public void onReceiveValue(Boolean aBoolean) {
                        // Do nothing
                    }
                }
        );

        initializeAuthClient(call);
        TwitterCore.getInstance().getSessionManager().clearActiveSession();
        result.success(null);
    }

    private HashMap<String, Object> sessionToMap(final TwitterSession session) {
        if (session == null) {
            return null;
        }

        return new HashMap<String, Object>() {{
            put("secret", session.getAuthToken().secret);
            put("token", session.getAuthToken().token);
            put("userId", String.valueOf(session.getUserId()));
            put("username", session.getUserName());
        }};
    }

    @Override
    public void success(final com.twitter.sdk.android.core.Result<TwitterSession> result) {
        if (pendingResult != null) {
            final HashMap<String, Object> sessionMap = sessionToMap(result.data);
            final HashMap<String, Object> resultMap = new HashMap<String, Object>() {{
                put("status", "loggedIn");
                put("session", sessionMap);
            }};

            pendingResult.success(resultMap);
            pendingResult = null;
        }
    }

    @Override
    public void failure(final TwitterException exception) {
        if (pendingResult != null) {
            final HashMap<String, Object> resultMap = new HashMap<String, Object>() {{
                put("status", "error");
                put("errorMessage", exception.getMessage());
            }};

            pendingResult.success(resultMap);
            pendingResult = null;
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (authClientInstance != null) {
            authClientInstance.onActivityResult(requestCode, resultCode, data);
        }

        return false;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        handleAttachToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        handleDetachFromActivity();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        handleDetachFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        handleAttachToActivity(binding);
    }

    private void handleAttachToActivity(@NonNull ActivityPluginBinding binding) {
        activityPluginBinding = binding;
        pluginActivity = binding.getActivity();
        activityPluginBinding.addActivityResultListener(this);
    }

    private void handleDetachFromActivity() {
        if (activityPluginBinding != null) {
            activityPluginBinding.removeActivityResultListener(this);
        }
        activityPluginBinding = null;
        pluginActivity = null;
    }
}
