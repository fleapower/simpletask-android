package nl.mpcjanssen.simpletask;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;

public class MyAppWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "MyAppWidgetProvider";

    // Create unique numbers for every widget pendingIntent
    // Otherwise the will overwrite each other
    private final static int FROM_WIDGETS_START = 1;

    private final static int FROM_LIST_VIEW = 0;

    private static void putFilterExtras(Intent target, @NonNull SharedPreferences preferences, int widgetId) {
        // Log.d(tag, "putFilter extras  for appwidget " + widgetId);
        Query filter = new Query(preferences, "widget" + widgetId);
        filter.saveInIntent(target);
    }

    private static RemoteViews updateView(int widgetId, @NonNull Context ctx) {
        SharedPreferences preferences = ctx.getSharedPreferences("" + widgetId, 0);
        RemoteViews view;
        SharedPreferences appPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        ColorDrawable listColor;
        ColorDrawable headerColor;
        String theme = appPreferences.getString("widget_theme", "");

        if (theme.equals("dark")) {
            view = new RemoteViews(ctx.getPackageName(), R.layout.appwidget_dark);
            listColor = new ColorDrawable(ContextCompat.getColor(ctx, R.color.black));
            headerColor = new ColorDrawable(ContextCompat.getColor(ctx, R.color.gray87));
        } else {
            view = new RemoteViews(ctx.getPackageName(), R.layout.appwidget);
            listColor = new ColorDrawable(ContextCompat.getColor(ctx, R.color.white));
            headerColor = new ColorDrawable(ContextCompat.getColor(ctx, R.color.simple_primary));
        }

        int header_transparency = appPreferences.getInt("widget_header_transparency", 0);
        int background_transparency = appPreferences.getInt("widget_background_transparency", 0);

        int header_alpha = ((100 - header_transparency) * 255) / 100;
        int background_alpha = ((100 - background_transparency) * 255) / 100;

        headerColor.setAlpha(header_alpha);
        listColor.setAlpha(background_alpha);

        view.setInt(R.id.widgetlv, "setBackgroundColor", listColor.getColor());
        view.setInt(R.id.header, "setBackgroundColor", headerColor.getColor());

        Intent intent = new Intent(ctx, AppWidgetService.class);
        // Add the app widget ID to the intent extras.
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        // Instantiate the RemoteViews object for the App Widget layout.
        view.setRemoteAdapter(R.id.widgetlv, intent);

        NamedQuery filter = NamedQuery.Companion.initFromPrefs(preferences, "widget" + widgetId, "no name");
        view.setTextViewText(R.id.title, filter.getName());

        // Make sure we use different intents for the different pendingIntents or
        // they will replace each other

        Intent appIntent;

        appIntent = new Intent(ctx, Simpletask.class);
        appIntent.setAction(Constants.INTENT_START_FILTER);
        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, FROM_LIST_VIEW, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        view.setPendingIntentTemplate(R.id.widgetlv, pendingIntent);

        appIntent = new Intent(ctx, Simpletask.class);
        appIntent.setAction(Constants.INTENT_START_FILTER);
        putFilterExtras(appIntent, preferences, widgetId);
        pendingIntent = PendingIntent.getActivity(ctx, FROM_WIDGETS_START + widgetId, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        view.setOnClickPendingIntent(R.id.title, pendingIntent);

        appIntent = new Intent(ctx, AddTask.class);
        putFilterExtras(appIntent, preferences, widgetId);
        pendingIntent = PendingIntent.getActivity(ctx, FROM_WIDGETS_START + widgetId, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        view.setOnClickPendingIntent(R.id.widgetadd, pendingIntent);

        appIntent = new Intent(ctx, FilterActivity.class);
        appIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        appIntent.putExtra(Constants.EXTRA_WIDGET_RECONFIGURE, true);
        appIntent.putExtra(Constants.EXTRA_WIDGET_ID, widgetId);
        filter.getQuery().saveInIntent(appIntent);
        pendingIntent = PendingIntent.getActivity(ctx, FROM_WIDGETS_START + widgetId, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        view.setOnClickPendingIntent(R.id.widgetconfig, pendingIntent);

        return view;
    }

    @Override
    public void onUpdate(@NonNull Context context, @NonNull AppWidgetManager appWidgetManager, @NonNull int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            Log.d(TAG, "onUpdate " + widgetId);
            RemoteViews views = updateView(widgetId, context);
            appWidgetManager.updateAppWidget(widgetId, views);

            // Need to updateCache the listView to redraw the listItems when changing the theme
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widgetlv);
        }
    }

    @Override
    public void onDeleted(@NonNull Context context, @NonNull int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            Log.d(TAG, "cleaning up widget configuration prefName:" + widgetId);

            // At least clear contents of the other_preferences file
            SharedPreferences preferences = context.getSharedPreferences("" + widgetId, 0);
            preferences.edit().clear().apply();

            File prefs_path = new File(context.getFilesDir(), "../shared_prefs");
            File prefs_xml = new File(prefs_path, widgetId + ".xml");

            // Remove the XML file
            if (!prefs_xml.delete()) {
                Log.w(TAG, "File not deleted: " + prefs_xml);
            }
        }
    }

    public static void updateAppWidget(@NonNull Context context, @NonNull AppWidgetManager appWidgetManager, int appWidgetId, String name) {
        Log.d(TAG, "updateAppWidget appWidgetId=" + appWidgetId + " title=" + name);

        // Construct the RemoteViews object.  It takes the package name (in our case, it's our
        // package, but it needs this because on the other side it's the widget host inflating
        // the layout from our package).
        RemoteViews views = updateView(appWidgetId, context);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}