package com.backendless.push;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.RemoteViews;
import com.backendless.Backendless;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.messaging.AndroidPushTemplate;
import com.backendless.messaging.PublishOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BackendlessPushService extends IntentService implements PushReceiverCallback
{
  private static final String TAG = "BackendlessPushService";
  private static final Random random = new Random();

  private static final int MAX_BACKOFF_MS = (int) TimeUnit.SECONDS.toMillis( 3600 );
  private static final String TOKEN = Long.toBinaryString( random.nextLong() );
  private static final String EXTRA_TOKEN = "token";
  private static Map<String, AndroidPushTemplate> pushNotificationTemplateDTOs = Collections.emptyMap();
  private static final AtomicInteger ints = new AtomicInteger(  );

  private PushReceiverCallback callback;

  public BackendlessPushService()
  {
    this( "BackendlessPushService" );
  }

  public BackendlessPushService( String name )
  {
    super( name );
    this.callback = this;
  }

  public BackendlessPushService( PushReceiverCallback callback )
  {
    super(null);
    this.callback = callback;
  }

  /**
   * At this point {@link com.backendless.push.BackendlessBroadcastReceiver}
   * is still holding a wake lock
   * for us.  We can do whatever we need to here and then tell it that
   * it can release the wakelock.  This sample just does some slow work,
   * but more complicated implementations could take their own wake
   * lock here before releasing the receiver's.
   * <p/>
   * Note that when using this approach you should be aware that if your
   * service gets killed and restarted while in the middle of such work
   * (so the Intent gets re-delivered to perform the work again), it will
   * at that point no longer be holding a wake lock since we are depending
   * on SimpleWakefulReceiver to that for us.  If this is a concern, you can
   * acquire a separate wake lock here.
   */
  @Override
  protected void onHandleIntent( Intent intent )
  {
    try
    {
      handleIntent( this, intent );
    }
    finally
    {
      BackendlessBroadcastReceiver.completeWakefulIntent( intent );
    }
  }

  public void onRegistered( Context context, String registrationId )
  {
  }

  public void onUnregistered( Context context, Boolean unregistered )
  {
  }

  public boolean onMessage( Context context, Intent intent )
  {
    return true;
  }

  public void onError( Context context, String message )
  {
    throw new RuntimeException( message );
  }

  void handleIntent( Context context, Intent intent )
  {
    String action = intent.getAction();

    switch ( action )
    {
      case GCMConstants.INTENT_FROM_GCM_REGISTRATION_CALLBACK:
        handleRegistration( context, intent );
        break;
      case GCMConstants.INTENT_FROM_GCM_MESSAGE:
        handleMessage( context, intent );
        break;
      case GCMConstants.INTENT_FROM_GCM_LIBRARY_RETRY:
        handleRetry( context, intent );
        break;
    }
  }

  private void handleRetry( Context context, Intent intent )
  {
    String token = intent.getStringExtra( EXTRA_TOKEN );
    if( !TOKEN.equals( token ) )
      return;
    // retry last call
    if( GCMRegistrar.isRegistered( context ) )
      GCMRegistrar.internalUnregister( context );
    else
      GCMRegistrar.internalRegister( context, GCMRegistrar.getSenderId( context ) );
  }

  private void handleMessage( final Context context, Intent intent )
  {
    String contentText = intent.getStringExtra( PublishOptions.ANDROID_CONTENT_TEXT_TAG );

    try
    {
      final String templateName = intent.getStringExtra( PublishOptions.TEMPLATE_NAME );
      if( templateName != null )
      {
        AndroidPushTemplate androidPushTemplateDTO = pushNotificationTemplateDTOs.get( templateName );
        Notification notification = convertFromTemplate( androidPushTemplateDTO, contentText );
        showNotification( notification, androidPushTemplateDTO.getName() );
        return;
      }

      String immediatePush = intent.getStringExtra( PublishOptions.ANDROID_IMMEDIATE_PUSH );
      if( immediatePush != null )
      {
        AndroidPushTemplate androidPushTemplateDTO = (AndroidPushTemplate) weborb.util.io.Serializer.fromBytes( immediatePush.getBytes(), weborb.util.io.Serializer.JSON, false );
        Notification notification = convertFromTemplate( androidPushTemplateDTO, contentText );
        showNotification( notification, androidPushTemplateDTO.getName() );
        return;
      }

      boolean showPushNotification = callback.onMessage( context, intent );

      // TODO: perfrom actions for push templates

      if( showPushNotification )
      {
        CharSequence tickerText = intent.getStringExtra( PublishOptions.ANDROID_TICKER_TEXT_TAG );
        CharSequence contentTitle = intent.getStringExtra( PublishOptions.ANDROID_CONTENT_TITLE_TAG );

        if( tickerText != null && tickerText.length() > 0 )
        {
          int appIcon = context.getApplicationInfo().icon;
          if( appIcon == 0 )
            appIcon = android.R.drawable.sym_def_app_icon;

          Intent notificationIntent = context.getPackageManager().getLaunchIntentForPackage( context.getApplicationInfo().packageName );
          PendingIntent contentIntent = PendingIntent.getActivity( context, 0, notificationIntent, 0 );
          Notification notification = new Notification.Builder( context )
              .setSmallIcon( appIcon )
              .setTicker( tickerText )
              .setContentTitle( contentTitle )
              .setContentText( contentText )
              .setContentIntent( contentIntent )
              .setWhen( System.currentTimeMillis() )
              .build();
          notification.flags |= Notification.FLAG_AUTO_CANCEL;

          int customLayout = context.getResources().getIdentifier( "notification", "layout", context.getPackageName() );
          int customLayoutTitle = context.getResources().getIdentifier( "title", "id", context.getPackageName() );
          int customLayoutDescription = context.getResources().getIdentifier( "text", "id", context.getPackageName() );
          int customLayoutImageContainer = context.getResources().getIdentifier( "image", "id", context.getPackageName() );
          int customLayoutImage = context.getResources().getIdentifier( "push_icon", "drawable", context.getPackageName() );

          if( customLayout > 0 && customLayoutTitle > 0 && customLayoutDescription > 0 && customLayoutImageContainer > 0 )
          {
            NotificationLookAndFeel lookAndFeel = new NotificationLookAndFeel();
            lookAndFeel.extractColors( context );
            RemoteViews contentView = new RemoteViews( context.getPackageName(), customLayout );
            contentView.setTextViewText( customLayoutTitle, contentTitle );
            contentView.setTextViewText( customLayoutDescription, contentText );
            contentView.setTextColor( customLayoutTitle, lookAndFeel.getTextColor() );
            contentView.setFloat( customLayoutTitle, "setTextSize", lookAndFeel.getTextSize() );
            contentView.setTextColor( customLayoutDescription, lookAndFeel.getTextColor() );
            contentView.setFloat( customLayoutDescription, "setTextSize", lookAndFeel.getTextSize() );
            contentView.setImageViewResource( customLayoutImageContainer, customLayoutImage );
            notification.contentView = contentView;
          }

          NotificationManager notificationManager = (NotificationManager) context.getSystemService( Context.NOTIFICATION_SERVICE );
          notificationManager.notify( intent.getIntExtra( BackendlessBroadcastReceiver.EXTRA_MESSAGE_ID, 0 ), notification );
        }
      }
    }
    catch ( Throwable throwable )
    {
      Log.e( TAG, "Error processing push notification", throwable );
    }
  }

  private Notification convertFromTemplate( AndroidPushTemplate templateDTO, String messageText )
  {
    String channelId = Backendless.getApplicationId() + ":" + templateDTO.getName();

    // Notification channel ID is ignored for Android 7.1.1 (API level 25) and lower.
    // NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder( getApplicationContext(), channelId)

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder( getApplicationContext(), channelId );

    long[] vibrate = null;
    if( templateDTO.getButtonTemplate().getVibrate() != null )
    {
      vibrate = new long[ templateDTO.getButtonTemplate().getVibrate().length ];
      int index = 0;
      for( long l : templateDTO.getButtonTemplate().getVibrate() )
        vibrate[ index++ ] = l;
    }

    Uri sound = null;
    if( templateDTO.getButtonTemplate().getSound() != null )
      sound = Uri.parse( templateDTO.getButtonTemplate().getSound() );

    notificationBuilder
            .setAutoCancel( true )
            .setDefaults( Notification.DEFAULT_ALL )
            .setWhen( System.currentTimeMillis() )
            .setSmallIcon( Integer.parseInt( templateDTO.getIcon() ) )
            .setTicker( templateDTO.getTickerText() )
            .setPriority( templateDTO.getPriority() )
            .setColor( templateDTO.getColorCode() )
            .setColorized( templateDTO.getColorized() )
            .setLights( templateDTO.getLightsColor(), templateDTO.getLightsOnMs(), templateDTO.getLightsOffMs() )
            .setContentTitle( templateDTO.getFirstRowTitle() )
            .setBadgeIconType( templateDTO.getBadge() )

            .setVisibility( templateDTO.getButtonTemplate().getVisibility() )
            .setVibrate( vibrate )
            .setSound( sound )

            .setContentText( messageText );

    return notificationBuilder.build();
  }

  private void showNotification( final Notification notification, final String tag )
  {
    final NotificationManagerCompat notificationManager = NotificationManagerCompat.from( getApplicationContext() );
    Handler handler = new Handler( Looper.getMainLooper() );
    handler.post( new Runnable()
    {
      @Override
      public void run()
      {
        notificationManager.notify( tag, ints.getAndIncrement(), notification );
      }
    } );
  }

  private void handleRegistration( final Context context, Intent intent )
  {
    String registrationIds = intent.getStringExtra( GCMConstants.EXTRA_REGISTRATION_IDS );
    String error = intent.getStringExtra( GCMConstants.EXTRA_ERROR );
    String unregistered = intent.getStringExtra( GCMConstants.EXTRA_UNREGISTERED );
    boolean isInternal = intent.getBooleanExtra( GCMConstants.EXTRA_IS_INTERNAL, false );

    // registration succeeded
    if( registrationIds != null )
    {
      if( isInternal )
      {
        callback.onRegistered( context, registrationIds );
      }

      GCMRegistrar.resetBackoff( context );
      GCMRegistrar.setGCMdeviceToken( context, registrationIds );
      registerFurther( context, registrationIds );
      return;
    }

    // unregistration succeeded
    if( unregistered != null )
    {
      // Remember we are unregistered
      GCMRegistrar.resetBackoff( context );
      GCMRegistrar.setGCMdeviceToken( context, "" );
      GCMRegistrar.setChannels( context, Collections.<String>emptyList() );
      GCMRegistrar.setRegistrationExpiration( context, -1 );
      unregisterFurther( context );
      return;
    }

    // Registration failed
    if( error.equals( GCMConstants.ERROR_SERVICE_NOT_AVAILABLE ) )
    {
      int backoffTimeMs = GCMRegistrar.getBackoff( context );
      int nextAttempt = backoffTimeMs / 2 + random.nextInt( backoffTimeMs );
      Intent retryIntent = new Intent( GCMConstants.INTENT_FROM_GCM_LIBRARY_RETRY );
      retryIntent.putExtra( EXTRA_TOKEN, TOKEN );
      PendingIntent retryPendingIntent = PendingIntent.getBroadcast( context, 0, retryIntent, 0 );
      AlarmManager am = (AlarmManager) context.getSystemService( Context.ALARM_SERVICE );
      am.set( AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + nextAttempt, retryPendingIntent );
      // Next retry should wait longer.
      if( backoffTimeMs < MAX_BACKOFF_MS )
        GCMRegistrar.setBackoff( context, backoffTimeMs * 2 );
    }
    else
    {
      callback.onError( context, error );
    }
  }

  private void registerFurther( final Context context, String GCMregistrationId )
  {
    final long registrationExpiration = GCMRegistrar.getRegistrationExpiration( context );
    Backendless.Messaging.registerDeviceOnServer( GCMregistrationId, new ArrayList<>( GCMRegistrar.getChannels( context ) ), registrationExpiration, new AsyncCallback<String>()
    {
      @Override
      public void handleResponse( String registrationInfo )
      {
        String ids;
        try
        {
          Object[] obj = (Object[]) weborb.util.io.Serializer.fromBytes( registrationInfo.getBytes(), weborb.util.io.Serializer.JSON, false );
          ids = (String) obj[0];
          createNotificationChannels( (Map<String, AndroidPushTemplate>) obj[1] );
        }
        catch( IOException e )
        {
          callback.onError( context, "Could not deserialize server response: " + e.getMessage() );
          return;
        }
        GCMRegistrar.setRegistrationIds( context, ids, registrationExpiration );
        callback.onRegistered( context, registrationInfo );
      }

      @Override
      public void handleFault( BackendlessFault fault )
      {
        callback.onError( context, "Could not register device on Backendless server: " + fault.getMessage() );
      }
    } );
  }

  private void createNotificationChannels( Map<String, AndroidPushTemplate> pushNotificationTemplateDTOs)
  {
    BackendlessPushService.pushNotificationTemplateDTOs = Collections.unmodifiableMap( pushNotificationTemplateDTOs );

    //TODO
    for (Map.Entry<String, AndroidPushTemplate> template : pushNotificationTemplateDTOs.entrySet())
    {

    }
  }

  private void unregisterFurther( final Context context )
  {
    Backendless.Messaging.unregisterDeviceOnServer( new AsyncCallback<Boolean>()
    {
      @Override
      public void handleResponse( Boolean unregistered )
      {
        GCMRegistrar.setRegistrationIds( context, "", 0 );
        callback.onUnregistered( context, unregistered );
      }

      @Override
      public void handleFault( BackendlessFault fault )
      {
        callback.onError( context, "Could not unregister device on Backendless server: " + fault.getMessage() );
      }
    } );
  }
}
