package nerd.tuxmobil.fahrplan.congress;

import nerd.tuxmobil.fahrplan.congress.CustomHttpClient.HTTP_STATUS;
import nerd.tuxmobil.fahrplan.congress.MyApp.TASKS;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.format.Time;

public class UpdateService extends IntentService implements OnDownloadCompleteListener, OnParseCompleteListener {

	public UpdateService() {
		super("UpdateService");
	}

	final String LOG_TAG = "UpdateService";
	private FetchFahrplan fetcher;
	final String PREFS_NAME = "settings";
	private FahrplanParser parser;

	@Override
	public void onParseDone(Boolean result, String version) {
		MyApp.LogDebug(LOG_TAG, "parseDone: " + result + " , numdays="+MyApp.numdays);
		MyApp.task_running = TASKS.NONE;
		MyApp.fahrplan_xml = null;

		// update complete, show notification
		MyApp.LogDebug(LOG_TAG, "background update complete");

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notify = new Notification();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        notify = builder.setAutoCancel(true).setContentText(getString(R.string.aktualisiert_auf, version)).setContentTitle(getString(R.string.app_name))
               .setDefaults(Notification.DEFAULT_LIGHTS).setSmallIcon(R.drawable.ic_notification)
               .setSound(Uri.parse(prefs.getString("reminder_tone", "")))
               .setContentIntent(contentIntent).build();

        nm.notify(2, notify);

		stopSelf();
	}

	public void parseFahrplan() {
		MyApp.task_running = TASKS.PARSE;
		if (MyApp.parser == null) {
			parser = new FahrplanParser(getApplicationContext());
		} else {
			parser = MyApp.parser;
		}
		parser.setListener(this);
		parser.parse(MyApp.fahrplan_xml, MyApp.eTag);
	}

	public void onGotResponse(HTTP_STATUS status, String response, String eTagStr) {
		MyApp.LogDebug(LOG_TAG, "Response... " + status);
		MyApp.task_running = TASKS.NONE;
		if ((status == HTTP_STATUS.HTTP_OK) || (status == HTTP_STATUS.HTTP_NOT_MODIFIED)) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			Time now = new Time();
			now.setToNow();
			long millis = now.toMillis(true);
			Editor edit = prefs.edit();
			edit.putLong("last_fetch", millis);
			edit.commit();
		}
		if (status != HTTP_STATUS.HTTP_OK) {
			MyApp.LogDebug(LOG_TAG, "background update failed with " + status);
			stopSelf();
			return;
		}

		MyApp.fahrplan_xml = response;
		MyApp.eTag = eTagStr;
		parseFahrplan();
	}

	private void fetchFahrplan(OnDownloadCompleteListener completeListener) {
		if (MyApp.task_running == TASKS.NONE) {
			MyApp.task_running = TASKS.FETCH;
			fetcher.setListener(completeListener);
			fetcher.fetch(MyApp.schedulePath, MyApp.eTag);
		} else {
			MyApp.LogDebug(LOG_TAG, "fetch already in progress");
		}
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		MyApp.LogDebug(LOG_TAG, "onHandleIntent");

		ConnectivityManager connectivityMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo nwInfo = connectivityMgr.getActiveNetworkInfo();
		if ((nwInfo == null) || (nwInfo.isConnected() == false)) {
			MyApp.LogDebug(LOG_TAG, "not connected");
			ConnectivityStateReceiver.enableReceiver(this);
			stopSelf();
			return;
		}

		if (MyApp.fetcher == null) {
			fetcher = new FetchFahrplan();
		} else {
			fetcher = MyApp.fetcher;
		}
		MyApp.LogDebug(LOG_TAG, "going to fetch schedule");
		FahrplanMisc.setUpdateAlarm(this, false);
		fetchFahrplan(this);
	}

}
