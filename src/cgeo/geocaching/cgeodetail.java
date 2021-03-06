package cgeo.geocaching;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map.Entry;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.StrikethroughSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.apps.cache.GeneralAppsFactory;
import cgeo.geocaching.apps.cache.navi.NavigationAppFactory;
import cgeo.geocaching.compatibility.Compatibility;

/**
 * Activity to display all details of a cache like owner, difficulty, description etc.
 *
 */
public class cgeodetail extends AbstractActivity {

	public cgeodetail() {
		super("c:geo-cache-details");
	}

	public Long searchId = null;
	public cgCache cache = null;
	public String geocode = null;
	public String name = null;
	public String guid = null;
	private LayoutInflater inflater = null;
	private cgGeo geo = null;
	private cgUpdateLoc geoUpdate = new update();
	private float pixelRatio = 1;
	private TextView cacheDistance = null;
	private String contextMenuUser = null;
	private ProgressDialog waitDialog = null;
	private ProgressDialog descDialog = null;
	private Spanned longDesc = null;
	private Boolean longDescDisplayed = false;
	private loadCache threadCache = null;
	private loadLongDesc threadLongDesc = null;
	private Thread storeThread = null;
	private Thread refreshThread = null;
	private ProgressDialog storeDialog = null;
	private ProgressDialog refreshDialog = null;
	private ProgressDialog dropDialog = null;
	private ProgressDialog watchlistDialog = null; // progress dialog for watchlist add/remove
	private Thread watchlistThread = null; // thread for watchlist add/remove
	private HashMap<Integer, String> calendars = new HashMap<Integer, String>();

	private ViewGroup attributeIconsLayout; // layout for attribute icons
	private ViewGroup attributeDescriptionsLayout; // layout for attribute descriptions
	private boolean attributesShowAsIcons = true; // default: show icons
	/** <code>noAttributeImagesFound</code>
	 * This will be the case if the cache was imported with an older version of c:geo.
	 * These older versions parsed the attribute description from the tooltip in the web
	 * page and put them into the DB. No icons can be matched for these. */
	private boolean noAttributeIconsFound = false;
	private int attributeBoxMaxWidth;
	/**
	 * differentiate between whether we are starting the activity for a cache or if we return to the activity from
	 * another activity that we started in front
	 */
	private boolean disableResumeSetView = false;

	private Handler storeCacheHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			storeThread = null;

			try {
				cache = app.getCache(searchId); // reload cache details
			} catch (Exception e) {
				showToast(res.getString(R.string.err_store_failed));

				Log.e(cgSettings.tag, "cgeodetail.storeCacheHandler: " + e.toString());
			}

			setView();
		}
	};

	private Handler refreshCacheHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			refreshThread = null;

			try {
				cache = app.getCache(searchId); // reload cache details
			} catch (Exception e) {
				showToast(res.getString(R.string.err_refresh_failed));

				Log.e(cgSettings.tag, "cgeodetail.refreshCacheHandler: " + e.toString());
			}

			setView();
		}
	};

	private Handler dropCacheHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			try {
				cache = app.getCache(searchId); // reload cache details
			} catch (Exception e) {
				showToast(res.getString(R.string.err_drop_failed));

				Log.e(cgSettings.tag, "cgeodetail.dropCacheHandler: " + e.toString());
			}

			setView();
		}
	};

	private Handler loadCacheHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (searchId == null || searchId <= 0) {
				showToast(res.getString(R.string.err_dwld_details_failed));

				finish();
				return;
			}

			if (app.getError(searchId) != null) {
				showToast(res.getString(R.string.err_dwld_details_failed_reason) + " " + app.getError(searchId) + ".");

				finish();
				return;
			}

			setView();

			if (settings.autoLoadDesc == 1) {
				try {
					loadLongDesc();
				} catch (Exception e) {
					// activity is not visible
				}
			}

			(new loadMapPreview(loadMapPreviewHandler)).start();
		}
	};

	final Handler loadMapPreviewHandler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			BitmapDrawable image = (BitmapDrawable) message.obj;
			if (image == null) {
				return;
			}
			ScrollView scroll = (ScrollView) findViewById(R.id.details_list_box);
			final ImageView view = (ImageView) findViewById(R.id.map_preview);
			if (view == null) {
				return;
			}
			Bitmap bitmap = image.getBitmap();
			if (bitmap == null || bitmap.getWidth() <= 10) {
				return;
			}
			view.setImageDrawable(image);

			if (scroll.getScrollY() == 0) {
				scroll.scrollTo(0, (int) (80 * pixelRatio));
			}
			view.setVisibility(View.VISIBLE);
			view.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					try {
						registerForContextMenu(view);
						openContextMenu(view);
					} catch (Exception e) {
						// nothing
					}
				}
			});
		}
	};

	private Handler loadDescriptionHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			parseLongDescription();

			if (longDesc != null) {
				((LinearLayout) findViewById(R.id.desc_box)).setVisibility(View.VISIBLE);
				TextView descView = (TextView) findViewById(R.id.description);
				if (cache.description.length() > 0) {
					descView.setVisibility(View.VISIBLE);
					descView.setText(longDesc, TextView.BufferType.SPANNABLE);
					descView.setMovementMethod(LinkMovementMethod.getInstance());
				}
				else {
					descView.setVisibility(View.GONE);
				}

				Button showDesc = (Button) findViewById(R.id.show_description);
				showDesc.setVisibility(View.GONE);
				showDesc.setOnTouchListener(null);
				showDesc.setOnClickListener(null);
			} else {
				showToast(res.getString(R.string.err_load_descr_failed));
			}

			if (descDialog != null && descDialog.isShowing()) {
				descDialog.dismiss();
			}

			longDescDisplayed = true;
		}
	};

	/**
	 * shows/hides buttons, sets text in watchlist box
	 */
	private void updateWatchlistBox() {
		LinearLayout layout = (LinearLayout)findViewById(R.id.watchlist_box);
		boolean supportsWatchList = cache.supportsWatchList();
		layout.setVisibility(supportsWatchList ? View.VISIBLE : View.GONE);
		if (!supportsWatchList) {
			return;
		}
		Button buttonAdd = (Button) findViewById(R.id.add_to_watchlist);
		Button buttonRemove = (Button) findViewById(R.id.remove_from_watchlist);
		TextView text = (TextView) findViewById(R.id.watchlist_text);

		if (cache.onWatchlist) {
			buttonAdd.setVisibility(View.GONE);
			buttonRemove.setVisibility(View.VISIBLE);
			text.setText(R.string.cache_watchlist_on);
		} else {
			buttonAdd.setVisibility(View.VISIBLE);
			buttonRemove.setVisibility(View.GONE);
			text.setText(R.string.cache_watchlist_not_on);
		}
	}

	/**
	 * Handler, called when watchlist add or remove is done
	 */
	private Handler WatchlistHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			watchlistThread = null;
			if (watchlistDialog != null)
				watchlistDialog.dismiss();
			if (msg.what == -1) {
				showToast(res.getString(R.string.err_watchlist_failed));
			} else {
				updateWatchlistBox();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setTheme();
		setContentView(R.layout.detail);
		setTitle(res.getString(R.string.cache));

		init();

		// get parameters
		final Bundle extras = getIntent().getExtras();
		final Uri uri = getIntent().getData();

		// try to get data from extras
		if (geocode == null && extras != null) {
			geocode = extras.getString("geocode");
			name = extras.getString("name");
			guid = extras.getString("guid");
		}

		// try to get data from URI
		if (geocode == null && guid == null && uri != null) {
			String uriHost = uri.getHost().toLowerCase();
			String uriPath = uri.getPath().toLowerCase();
			String uriQuery = uri.getQuery();

			if (uriQuery != null) {
				Log.i(cgSettings.tag, "Opening URI: " + uriHost + uriPath + "?" + uriQuery);
			} else {
				Log.i(cgSettings.tag, "Opening URI: " + uriHost + uriPath);
			}

			if (uriHost.contains("geocaching.com")) {
				geocode = uri.getQueryParameter("wp");
				guid = uri.getQueryParameter("guid");

				if (geocode != null && geocode.length() > 0) {
					geocode = geocode.toUpperCase();
					guid = null;
				} else if (guid != null && guid.length() > 0) {
					geocode = null;
					guid = guid.toLowerCase();
				} else {
					showToast(res.getString(R.string.err_detail_open));
					finish();
					return;
				}
			} else if (uriHost.contains("coord.info")) {
				if (uriPath != null && uriPath.startsWith("/gc")) {
					geocode = uriPath.substring(1).toUpperCase();
				} else {
					showToast(res.getString(R.string.err_detail_open));
					finish();
					return;
				}
			}
		}

		// no given data
		if (geocode == null && guid == null) {
			showToast(res.getString(R.string.err_detail_cache));
			finish();
			return;
		}

		app.setAction(geocode);

		try {
			if (name != null && name.length() > 0) {
				waitDialog = ProgressDialog.show(this, name, res.getString(R.string.cache_dialog_loading_details), true);
			} else if (geocode != null && geocode.length() > 0) {
				waitDialog = ProgressDialog.show(this, geocode.toUpperCase(), res.getString(R.string.cache_dialog_loading_details), true);
			} else {
				waitDialog = ProgressDialog.show(this, res.getString(R.string.cache), res.getString(R.string.cache_dialog_loading_details), true);
			}
			waitDialog.setCancelable(true);
		} catch (Exception e) {
			// nothing, we lost the window
		}

		disableResumeSetView = true;
		threadCache = new loadCache(loadCacheHandler);
		threadCache.start();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		setView();
	}

	@Override
	public void onResume() {
		super.onResume();

		settings.load();

		if (geo == null) {
			geo = app.startGeo(this, geoUpdate, base, settings, 0, 0);
		}
		if (!disableResumeSetView) {
			setView();
		}
		disableResumeSetView = false;
	}

	@Override
	public void onDestroy() {
		if (geo != null) {
			geo = app.removeGeo();
		}

		super.onDestroy();
	}

	@Override
	public void onStop() {
		if (geo != null) {
			geo = app.removeGeo();
		}

		super.onStop();
	}

	@Override
	public void onPause() {
		if (geo != null) {
			geo = app.removeGeo();
		}

		super.onPause();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
		super.onCreateContextMenu(menu, view, info);
		final int viewId = view.getId();

		if (viewId == R.id.author || viewId == R.id.value) {
			if (viewId == R.id.author) { // Author of a log entry
				contextMenuUser = ((TextView)view).getText().toString();
			} else if (viewId == R.id.value) { // The owner of the cache
				if (cache.ownerReal != null && cache.ownerReal.length() > 0) {
					contextMenuUser = cache.ownerReal;
				} else {
					contextMenuUser = cache.owner;
				}
			}

			menu.setHeaderTitle(res.getString(R.string.user_menu_title) + " " + contextMenuUser);
			menu.add(viewId, 1, 0, res.getString(R.string.user_menu_view_hidden));
			menu.add(viewId, 2, 0, res.getString(R.string.user_menu_view_found));
			menu.add(viewId, 3, 0, res.getString(R.string.user_menu_open_browser));
		}
		if (viewId == R.id.map_preview) {
			menu.setHeaderTitle(res.getString(R.string.cache_menu_navigate));
			addNavigationMenuItems(menu);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final int group = item.getGroupId();

		if (group == R.id.author || group == R.id.value) {
			final int id = item.getItemId();

			if (id == 1) {
				cgeocaches.startActivityCacheOwner(this, contextMenuUser);
				return true;
			} else if (id == 2) {
				cgeocaches.startActivityCacheUser(this, contextMenuUser);
				return true;
			} else if (id == 3) {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.geocaching.com/profile/?u=" + URLEncoder.encode(contextMenuUser))));

				return true;
			}
		}
		else {
			return onOptionsItemSelected(item);
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (cache != null && cache.latitude != null && cache.longitude != null) {
			menu.add(0, 2, 0, res.getString(R.string.cache_menu_compass)).setIcon(android.R.drawable.ic_menu_compass); // compass
			SubMenu subMenu = menu.addSubMenu(1, 0, 0, res.getString(R.string.cache_menu_navigate)).setIcon(android.R.drawable.ic_menu_more);
			addNavigationMenuItems(subMenu);
		}

		if (cache != null && cache.canBeAddedToCalendar()) {
			menu.add(1, 11, 0, res.getString(R.string.cache_menu_event)).setIcon(android.R.drawable.ic_menu_agenda); // add event to calendar
		}
		addVisitMenu(menu, cache);

		if (cache != null && cache.spoilers != null && cache.spoilers.size() > 0) {
			menu.add(1, 5, 0, res.getString(R.string.cache_menu_spoilers)).setIcon(android.R.drawable.ic_menu_gallery); // spoiler images
		}

		if (cache != null && cache.latitude != null && cache.longitude != null) {
			menu.add(0, 10, 0, res.getString(R.string.cache_menu_around)).setIcon(android.R.drawable.ic_menu_rotate); // caches around
		}

		if (cache != null && cache.canOpenInBrowser()) {
			menu.add(1, 7, 0, res.getString(R.string.cache_menu_browser)).setIcon(android.R.drawable.ic_menu_info_details); // browser
		}
		menu.add(0, 12, 0, res.getString(R.string.cache_menu_share)).setIcon(android.R.drawable.ic_menu_share); // share cache

		return true;
	}

	private void addNavigationMenuItems(final Menu menu) {
		NavigationAppFactory.addMenuItems(menu, this, res);
		GeneralAppsFactory.addMenuItems(menu, this, res, cache);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int menuItem = item.getItemId();

		if (menuItem == 2) {
			navigateTo();
			return true;
		} else if (menuItem == MENU_LOG_VISIT) {
			logVisit();
			return true;
		} else if (menuItem == 5) {
			showSpoilers();
			return true;
		} else if (menuItem == 7) {
			cache.openInBrowser(this);
			return true;
		} else if (menuItem == 10) {
			cachesAround();
			return true;
		} else if (menuItem == 11) {
			addToCalendar();
			return true;
		} else if (menuItem == 12) {
			shareCache();
			return true;
		}
		if (NavigationAppFactory.onMenuItemSelected(item, geo, this, res, cache, searchId, null, null)) {
			return true;
		}
		if (GeneralAppsFactory.onMenuItemSelected(item, this, cache)) {
			return true;
		}

		int logType = menuItem - MENU_LOG_VISIT_OFFLINE;
		cache.logOffline(this, logType, settings, base);
		return true;
	}

	private void init() {
		final DisplayMetrics dm = getResources().getDisplayMetrics();
		pixelRatio = dm.density;

		if (inflater == null) {
			inflater = getLayoutInflater();
		}
		if (geo == null) {
			geo = app.startGeo(this, geoUpdate, base, settings, 0, 0);
		}

		if (searchId != null && searchId > 0) {
			cache = app.getCache(searchId);
			if (cache != null && cache.geocode != null) {
				geocode = cache.geocode;
			}
		}

		if (geocode != null && geocode.length() > 0) {
			app.setAction(geocode);
		}
	}

	private void setView() {
		RelativeLayout itemLayout;
		TextView itemName;
		TextView itemValue;

		if (searchId == null) {
			return;
		}

		cache = app.getCache(searchId);

		if (cache == null) {
			if (waitDialog != null && waitDialog.isShowing()) waitDialog.dismiss();

			if (geocode != null && geocode.length() > 0) {
				showToast(res.getString(R.string.err_detail_cache_find) + " " + geocode + ".");
			} else {
				geocode = null;
				showToast(res.getString(R.string.err_detail_cache_find_some));
			}

			finish();
			return;
		}

		try {

			if (null == geocode && cache.geocode.length() > 0)
            {
			    geocode = cache.geocode;
            }

			if (null == guid && cache.guid.length() > 0)
            {
                guid = cache.guid;
            }

			setTitle(cache.geocode.toUpperCase());

			inflater = getLayoutInflater();

			ScrollView scroll = (ScrollView) findViewById(R.id.details_list_box);
			scroll.setVisibility(View.VISIBLE);

			LinearLayout detailsList = (LinearLayout) findViewById(R.id.details_list);
			detailsList.removeAllViews();

			// actionbar icon, default myster<
			((TextView) findViewById(R.id.actionbar_title)).setCompoundDrawablesWithIntrinsicBounds((Drawable) getResources().getDrawable(cgBase.getCacheIcon(cache.type)), null, null, null);

			// cache name (full name)
			itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
			itemName = (TextView) itemLayout.findViewById(R.id.name);
			itemValue = (TextView) itemLayout.findViewById(R.id.value);

			itemName.setText(res.getString(R.string.cache_name));
			Spannable span = (new Spannable.Factory()).newSpannable(Html.fromHtml(cache.name).toString());
			if (cache.disabled || cache.archived) { // strike
				span.setSpan(new StrikethroughSpan(), 0, span.toString().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			itemValue.setText(span);
			detailsList.addView(itemLayout);

			// cache type
			itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
			itemName = (TextView) itemLayout.findViewById(R.id.name);
			itemValue = (TextView) itemLayout.findViewById(R.id.value);

			itemName.setText(res.getString(R.string.cache_type));

			String size = "";
			if (cache.size != null && cache.size.length() > 0) {
				// don't show "not chosen" for events, that should be the normal case
				if (!(cache.isEventCache() && cache.size.equals("not chosen"))) {
					size = " (" + cache.size + ")";
				}
			}

			if (cgBase.cacheTypesInv.containsKey(cache.type)) { // cache icon
				itemValue.setText(cgBase.cacheTypesInv.get(cache.type) + size);
			} else {
				itemValue.setText(cgBase.cacheTypesInv.get("mystery") + size);
			}
			detailsList.addView(itemLayout);

			// gc-code
			itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
			itemName = (TextView) itemLayout.findViewById(R.id.name);
			itemValue = (TextView) itemLayout.findViewById(R.id.value);

			itemName.setText(res.getString(R.string.cache_geocode));
			itemValue.setText(cache.geocode.toUpperCase());
			detailsList.addView(itemLayout);

			// cache state
			if (cache.logOffline || cache.archived || cache.disabled || cache.members || cache.found) {
				itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
				itemName = (TextView) itemLayout.findViewById(R.id.name);
				itemValue = (TextView) itemLayout.findViewById(R.id.value);

				itemName.setText(res.getString(R.string.cache_status));

				StringBuilder state = new StringBuilder();
				if (cache.logOffline) {
					if (state.length() > 0) {
						state.append(", ");
					}
					state.append(res.getString(R.string.cache_status_offline_log));
				}
				if (cache.found) {
					if (state.length() > 0) {
						state.append(", ");
					}
					state.append(res.getString(R.string.cache_status_found));
				}
				if (cache.archived) {
					if (state.length() > 0) {
						state.append(", ");
					}
					state.append(res.getString(R.string.cache_status_archived));
				}
				if (cache.disabled) {
					if (state.length() > 0) {
						state.append(", ");
					}
					state.append(res.getString(R.string.cache_status_disabled));
				}
				if (cache.members) {
					if (state.length() > 0) {
						state.append(", ");
					}
					state.append(res.getString(R.string.cache_status_premium));
				}

				itemValue.setText(state.toString());
				detailsList.addView(itemLayout);

				state = null;
			}

			// distance
			itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
			itemName = (TextView) itemLayout.findViewById(R.id.name);
			itemValue = (TextView) itemLayout.findViewById(R.id.value);

			itemName.setText(res.getString(R.string.cache_distance));
			if (cache.distance != null) {
				itemValue.setText("~" + base.getHumanDistance(cache.distance));
			} else {
				itemValue.setText("--");
			}
			detailsList.addView(itemLayout);
			cacheDistance = itemValue;

			// difficulty
			if (cache.difficulty != null && cache.difficulty > 0) {
				addStarRating(detailsList, res.getString(R.string.cache_difficulty), cache.difficulty);
			}

			// terrain
			if (cache.terrain != null && cache.terrain > 0) {
				addStarRating(detailsList, res.getString(R.string.cache_terrain), cache.terrain);
			}

			// rating
			if (cache.rating != null && cache.rating > 0) {
				itemLayout = addStarRating(detailsList, res.getString(R.string.cache_rating), cache.rating);
				if (cache.votes != null) {
					final TextView itemAddition = (TextView)itemLayout.findViewById(R.id.addition);
					itemAddition.setText("(" + cache.votes + ")");
					itemAddition.setVisibility(View.VISIBLE);
				}
			}

			// favourite count
			if (cache.favouriteCnt != null) {
				itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
				itemName = (TextView) itemLayout.findViewById(R.id.name);
				itemValue = (TextView) itemLayout.findViewById(R.id.value);

				itemName.setText(res.getString(R.string.cache_favourite));
				itemValue.setText(String.format("%d", cache.favouriteCnt) + "×");
				detailsList.addView(itemLayout);
			}

			// cache author
			if ((cache.owner != null && cache.owner.length() > 0) || (cache.ownerReal != null && cache.ownerReal.length() > 0)) {
				itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
				itemName = (TextView) itemLayout.findViewById(R.id.name);
				itemValue = (TextView) itemLayout.findViewById(R.id.value);

				itemName.setText(res.getString(R.string.cache_owner));
				if (cache.owner != null && cache.owner.length() > 0) {
					itemValue.setText(Html.fromHtml(cache.owner), TextView.BufferType.SPANNABLE);
				} else if (cache.ownerReal != null && cache.ownerReal.length() > 0) {
					itemValue.setText(Html.fromHtml(cache.ownerReal), TextView.BufferType.SPANNABLE);
				}
				itemValue.setOnClickListener(new userActions());
				detailsList.addView(itemLayout);
			}

			// cache hidden
			if (cache.hidden != null && cache.hidden.getTime() > 0) {
				itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
				itemName = (TextView) itemLayout.findViewById(R.id.name);
				itemValue = (TextView) itemLayout.findViewById(R.id.value);

				if (cache.type != null && (cache.type.equalsIgnoreCase("event") || cache.type.equalsIgnoreCase("mega") || cache.type.equalsIgnoreCase("cito"))) {
					itemName.setText(res.getString(R.string.cache_event));
				} else {
					itemName.setText(res.getString(R.string.cache_hidden));
				}
				itemValue.setText(base.formatFullDate(cache.hidden.getTime()));
				detailsList.addView(itemLayout);
			}

			// cache location
			if (cache.location != null && cache.location.length() > 0) {
				itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
				itemName = (TextView) itemLayout.findViewById(R.id.name);
				itemValue = (TextView) itemLayout.findViewById(R.id.value);

				itemName.setText(res.getString(R.string.cache_location));
				itemValue.setText(cache.location);
				detailsList.addView(itemLayout);
			}

			// cache coordinates
			if (cache.latitude != null && cache.longitude != null) {
				itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_item, null);
				itemName = (TextView) itemLayout.findViewById(R.id.name);
				itemValue = (TextView) itemLayout.findViewById(R.id.value);

				itemName.setText(res.getString(R.string.cache_coordinates));
				itemValue.setText(cache.latitudeString + " | " + cache.longitudeString);
				detailsList.addView(itemLayout);
			}

			// cache attributes
			if (cache.attributes != null && cache.attributes.size() > 0) {

				final LinearLayout attribBox = (LinearLayout) findViewById(
						R.id.attributes_innerbox);

		    	// maximum width for attribute icons is screen width - paddings of parents
		        attributeBoxMaxWidth = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
		        		.getDefaultDisplay().getWidth();
		        ViewParent child = attribBox;
		        do {
		        	if (child instanceof View)
		        	attributeBoxMaxWidth = attributeBoxMaxWidth - ((View) child).getPaddingLeft()
		        			- ((View) child).getPaddingRight();
		        	child = child.getParent();
		        } while (child != null);

				// delete views holding description / icons
		        attributeDescriptionsLayout = null;
		        attributeIconsLayout = null;

				attribBox.setOnClickListener(new View.OnClickListener() {
		                @Override
		                public void onClick(View v) {
		                	// toggle between attribute icons and descriptions
		                	toggleAttributeDisplay(attribBox, attributeBoxMaxWidth);
		                }
		            } );

		        // icons or text?
				//
				// also show icons when noAttributeImagesFound == true. Explanation:
				//  1. no icons could be found in the first invocation of this method
				//  2. user refreshes cache from web
				//  3. now this method is called again
				//  4. attributeShowAsIcons is false but noAttributeImagesFound is true
				//     => try to show them now
				if (attributesShowAsIcons || noAttributeIconsFound) {
					showAttributeIcons(attribBox, attributeBoxMaxWidth);
				} else {
					showAttributeDescriptions(attribBox);
				}

				findViewById(R.id.attributes_box).setVisibility(View.VISIBLE);
			}

			// cache inventory
			if (cache.inventory != null && cache.inventory.size() > 0) {
				final LinearLayout inventBox = (LinearLayout) findViewById(R.id.inventory_box);
				final TextView inventView = (TextView) findViewById(R.id.inventory);

				StringBuilder inventoryString = new StringBuilder();
				for (cgTrackable inventoryItem : cache.inventory) {
					if (inventoryString.length() > 0) {
						inventoryString.append('\n');
					}
					// avoid HTML parsing where possible
					if (inventoryItem.name.indexOf('<') >= 0 || inventoryItem.name.indexOf('&') >= 0 ) {
						inventoryString.append(Html.fromHtml(inventoryItem.name).toString());
					}
					else {
						inventoryString.append(inventoryItem.name);
					}
				}
				inventView.setText(inventoryString);
				inventBox.setClickable(true);
				inventBox.setOnClickListener(new selectTrackable());
				inventBox.setVisibility(View.VISIBLE);
			}

			// offline use
			final TextView offlineText = (TextView) findViewById(R.id.offline_text);
			final Button offlineRefresh = (Button) findViewById(R.id.offline_refresh);
			final Button offlineStore = (Button) findViewById(R.id.offline_store);

			if (cache.reason >= 1) {
				Long diff = (System.currentTimeMillis() / (60 * 1000)) - (cache.detailedUpdate / (60 * 1000)); // minutes

				String ago = "";
				if (diff < 15) {
					ago = res.getString(R.string.cache_offline_time_mins_few);
				} else if (diff < 50) {
					ago = res.getString(R.string.cache_offline_time_about) + " " + diff + " " + res.getString(R.string.cache_offline_time_mins);
				} else if (diff < 90) {
					ago = res.getString(R.string.cache_offline_time_about) + " " + res.getString(R.string.cache_offline_time_hour);
				} else if (diff < (48 * 60)) {
					ago = res.getString(R.string.cache_offline_time_about) + " " + (diff / 60) + " " + res.getString(R.string.cache_offline_time_hours);
				} else {
					ago = res.getString(R.string.cache_offline_time_about) + " " + (diff / (24 * 60)) + " " + res.getString(R.string.cache_offline_time_days);
				}

				offlineText.setText(res.getString(R.string.cache_offline_stored) + "\n" + ago);
				offlineRefresh.setOnClickListener(new storeCache());

				offlineStore.setText(res.getString(R.string.cache_offline_drop));
				offlineStore.setClickable(true);
				offlineStore.setOnClickListener(new dropCache());
			} else {
				offlineText.setText(res.getString(R.string.cache_offline_not_ready));
				offlineRefresh.setOnClickListener(new refreshCache());

				offlineStore.setText(res.getString(R.string.cache_offline_store));
				offlineStore.setClickable(true);
				offlineStore.setOnClickListener(new storeCache());
			}
			offlineRefresh.setVisibility(cache.supportsRefresh() ? View.VISIBLE : View.GONE);
			offlineRefresh.setClickable(true);

			// cache personal note
			if (cache.personalNote != null && cache.personalNote.length() > 0) {
				((LinearLayout) findViewById(R.id.personalnote_box)).setVisibility(View.VISIBLE);

				TextView personalNoteText = (TextView) findViewById(R.id.personalnote);
				personalNoteText.setVisibility(View.VISIBLE);
				personalNoteText.setText(cache.personalNote, TextView.BufferType.SPANNABLE);
				personalNoteText.setMovementMethod(LinkMovementMethod.getInstance());
			}

			// cache short desc
			if (cache.shortdesc != null && cache.shortdesc.length() > 0) {
				((LinearLayout) findViewById(R.id.desc_box)).setVisibility(View.VISIBLE);

				TextView descView = (TextView) findViewById(R.id.shortdesc);
				descView.setVisibility(View.VISIBLE);
				descView.setText(Html.fromHtml(cache.shortdesc.trim(), new cgHtmlImg(this, geocode, true, cache.reason, false), null), TextView.BufferType.SPANNABLE);
				descView.setMovementMethod(LinkMovementMethod.getInstance());
			}

			// cache long desc
			if (longDescDisplayed) {
				parseLongDescription();

				if (longDesc != null && longDesc.length() > 0) {
					((LinearLayout) findViewById(R.id.desc_box)).setVisibility(View.VISIBLE);

					TextView descView = (TextView) findViewById(R.id.description);
					descView.setVisibility(View.VISIBLE);
					descView.setText(longDesc, TextView.BufferType.SPANNABLE);
					descView.setMovementMethod(LinkMovementMethod.getInstance());

					Button showDesc = (Button) findViewById(R.id.show_description);
					showDesc.setVisibility(View.GONE);
					showDesc.setOnTouchListener(null);
					showDesc.setOnClickListener(null);
				}
			} else if (longDescDisplayed == false && cache.description != null && cache.description.length() > 0) {
				((LinearLayout) findViewById(R.id.desc_box)).setVisibility(View.VISIBLE);

				Button showDesc = (Button) findViewById(R.id.show_description);
				showDesc.setVisibility(View.VISIBLE);
				showDesc.setOnClickListener(new View.OnClickListener() {
					public void onClick(View arg0) {
						loadLongDesc();
					}
				});
			}

			// watchlist
			Button buttonWatchlistAdd = (Button) findViewById(R.id.add_to_watchlist);
			Button buttonWatchlistRemove = (Button) findViewById(R.id.remove_from_watchlist);
			buttonWatchlistAdd.setOnClickListener(new AddToWatchlistClickListener());
			buttonWatchlistRemove.setOnClickListener(new RemoveFromWatchlistClickListener());
			updateWatchlistBox();

			// waypoints
			LinearLayout waypoints = (LinearLayout) findViewById(R.id.waypoints);
			waypoints.removeAllViews();

			if (cache.waypoints != null && cache.waypoints.size() > 0) {
				LinearLayout waypointView;

				// sort waypoints: PP, Sx, FI, OWN
				ArrayList<cgWaypoint> sortedWaypoints = new ArrayList<cgWaypoint>(cache.waypoints);
				Collections.sort(sortedWaypoints, new Comparator<cgWaypoint>() {

					@Override
					public int compare(cgWaypoint wayPoint1, cgWaypoint wayPoint2) {

						return order(wayPoint1) - order(wayPoint2);
					}

					private int order(cgWaypoint waypoint) {
						if (waypoint.prefix == null || waypoint.prefix.length() == 0) {
							return 0;
						}
						// check only the first character. sometimes there are inconsistencies like FI or FN for the FINAL
						char firstLetter = Character.toUpperCase(waypoint.prefix.charAt(0));
						switch (firstLetter) {
						case 'P' : return -100; // parking
						case 'S' : { // stage N
							try {
								Integer stageNumber = Integer.valueOf(waypoint.prefix.substring(1));
								return stageNumber;
							} catch (NumberFormatException e) {
								// nothing
							}
							return 0;
						}
						case 'F' : return 1000; // final
						case 'O' : return 10000; // own
						}
						return 0;
					}});

				for (cgWaypoint wpt : sortedWaypoints) {
					waypointView = (LinearLayout) inflater.inflate(R.layout.waypoint_item, null);
					final TextView identification = (TextView) waypointView.findViewById(R.id.identification);

					((TextView) waypointView.findViewById(R.id.type)).setText(cgBase.waypointTypes.get(wpt.type));
					if (wpt.prefix.equalsIgnoreCase("OWN") == false) {
						identification.setText(wpt.prefix.trim() + "/" + wpt.lookup.trim());
					} else {
						identification.setText(res.getString(R.string.waypoint_custom));
					}

					TextView nameView = (TextView) waypointView.findViewById(R.id.name);
					if (wpt.name.trim().length() == 0) {
						nameView.setText(cgBase.formatCoords(wpt.latitude, wpt.longitude, true));
					} else {
						// avoid HTML parsing
						if (wpt.name.indexOf('<') >= 0 || wpt.name.indexOf('&') >= 0) {
							nameView.setText(Html.fromHtml(wpt.name.trim()), TextView.BufferType.SPANNABLE);
						}
						else {
							nameView.setText(wpt.name.trim());
						}
					}
					wpt.setIcon(res, base, nameView);

					// avoid HTML parsing
					if (wpt.note.indexOf('<') >= 0 || wpt.note.indexOf('&') >= 0) {
						((TextView) waypointView.findViewById(R.id.note)).setText(Html.fromHtml(wpt.note.trim()), TextView.BufferType.SPANNABLE);
					}
					else {
						((TextView) waypointView.findViewById(R.id.note)).setText(wpt.note.trim());
					}

					waypointView.setOnClickListener(new waypointInfo(wpt.id));

					waypoints.addView(waypointView);
				}
			}

			Button addWaypoint = (Button) findViewById(R.id.add_waypoint);
			addWaypoint.setClickable(true);
			addWaypoint.setOnClickListener(new addWaypoint());

			// cache hint
			if (cache.hint != null && cache.hint.length() > 0) {
				((LinearLayout) findViewById(R.id.hint_box)).setVisibility(View.VISIBLE);
				TextView hintView = ((TextView) findViewById(R.id.hint));
				hintView.setText(cgBase.rot13(cache.hint.trim()));
				hintView.setClickable(true);
				hintView.setOnClickListener(new codeHint());
			} else {
				((LinearLayout) findViewById(R.id.hint_box)).setVisibility(View.GONE);
				TextView hintView = ((TextView) findViewById(R.id.hint));
				hintView.setClickable(false);
				hintView.setOnClickListener(null);
			}

			if (geo != null && geo.latitudeNow != null && geo.longitudeNow != null && cache != null && cache.latitude != null && cache.longitude != null) {
				cacheDistance.setText(base.getHumanDistance(cgBase.getDistance(geo.latitudeNow, geo.longitudeNow, cache.latitude, cache.longitude)));
				cacheDistance.bringToFront();
			}
		} catch (Exception e) {
			Log.e(cgSettings.tag, "cgeodetail.setView: " + e.toString());
		}

		if (waitDialog != null && waitDialog.isShowing()) waitDialog.dismiss();
		if (storeDialog != null && storeDialog.isShowing()) storeDialog.dismiss();
		if (dropDialog != null && dropDialog.isShowing()) dropDialog.dismiss();
		if (refreshDialog != null && refreshDialog.isShowing()) refreshDialog.dismiss();

		displayLogs();

		if (geo != null) geoUpdate.updateLoc(geo);
	}

	private void parseLongDescription() {
		if (longDesc == null && cache != null && cache.description != null) {
			longDesc = Html.fromHtml(cache.description.trim(), new cgHtmlImg(this, geocode, true, cache.reason, false), new UnknownTagsHandler());
		}
	}

	private RelativeLayout addStarRating(final LinearLayout detailsList, final String name, final float value) {
		RelativeLayout itemLayout = (RelativeLayout) inflater.inflate(R.layout.cache_layout, null);
		TextView itemName = (TextView) itemLayout.findViewById(R.id.name);
		TextView itemValue = (TextView) itemLayout.findViewById(R.id.value);
		LinearLayout itemStars = (LinearLayout) itemLayout.findViewById(R.id.stars);

		itemName.setText(name);
		itemValue.setText(String.format(Locale.getDefault(), "%.1f", value) + ' ' + res.getString(R.string.cache_rating_of) + " 5");
		for (int i = 0; i <= 4; i++) {
			ImageView star = (ImageView) inflater.inflate(R.layout.star, null);
			if ((value - i) >= 1.0) {
				star.setImageResource(R.drawable.star_on);
			} else if ((value - i) > 0.0) {
				star.setImageResource(R.drawable.star_half);
			} else {
				star.setImageResource(R.drawable.star_off);
			}
			itemStars.addView(star, (1 + i));
		}
		detailsList.addView(itemLayout);
		return itemLayout;
	}

	private void displayLogs() {
		// cache logs
		TextView textView = (TextView) findViewById(R.id.logcount);
		int logCounter = 0;
		if (cache != null && cache.logCounts != null) {
			final StringBuffer buff = new StringBuffer();
			buff.append(res.getString(R.string.cache_log_types));
			buff.append(": ");

			// sort the log counts by type id ascending. that way the FOUND, DNF log types are the first and most visible ones
			ArrayList<Entry<Integer, Integer>> sortedLogCounts = new ArrayList<Entry<Integer,Integer>>();
			sortedLogCounts.addAll(cache.logCounts.entrySet());
			Collections.sort(sortedLogCounts, new Comparator<Entry<Integer, Integer>>() {

				@Override
				public int compare(Entry<Integer, Integer> logCountItem1,
						Entry<Integer, Integer> logCountItem2) {
					return logCountItem1.getKey().compareTo(logCountItem2.getKey());
				}});
			for (Entry<Integer, Integer> pair : sortedLogCounts) {
				int logTypeId = pair.getKey().intValue();
				String logTypeLabel = cgBase.logTypes1.get(logTypeId);
				// it may happen that the label is unknown -> then avoid any output for this type
				if (logTypeLabel != null) {
					if (logCounter > 0) {
						buff.append(", ");
					}
					buff.append(pair.getValue().intValue());
					buff.append("× ");
					buff.append(logTypeLabel);
				}
				logCounter ++;
			}
			textView.setText(buff.toString());
		}
		// it may happen, that the logCounts map is available, but every log type has zero counts,
		// therefore check again for the number of counted logs
		if (logCounter > 0) {
			textView.setVisibility(View.VISIBLE);
		} else {
			textView.setVisibility(View.GONE);
		}

		// cache logs
		LinearLayout listView = (LinearLayout) findViewById(R.id.log_list);
		listView.removeAllViews();

		RelativeLayout rowView;

		if (cache != null && cache.logs != null) {
			for (cgLog log : cache.logs) {
				rowView = (RelativeLayout) inflater.inflate(R.layout.log_item, null);

				if (log.date > 0) {
					((TextView) rowView.findViewById(R.id.added)).setText(base.formatShortDate(log.date));
				} else {
				    ((TextView) rowView.findViewById(R.id.added)).setVisibility(View.GONE);
		        }

				if (cgBase.logTypes1.containsKey(log.type)) {
					((TextView) rowView.findViewById(R.id.type)).setText(cgBase.logTypes1.get(log.type));
				} else {
					((TextView) rowView.findViewById(R.id.type)).setText(cgBase.logTypes1.get(4)); // note if type is unknown
				}
				// avoid parsing HTML if not necessary
				if (log.author.indexOf('<') >= 0 || log.author.indexOf('&') >= 0) {
					((TextView) rowView.findViewById(R.id.author)).setText(Html.fromHtml(log.author), TextView.BufferType.SPANNABLE);
				}
				else {
					((TextView) rowView.findViewById(R.id.author)).setText(log.author);
				}

				if (log.found == -1) {
					((TextView) rowView.findViewById(R.id.count)).setVisibility(View.GONE);
				} else if (log.found == 0) {
					((TextView) rowView.findViewById(R.id.count)).setText(res.getString(R.string.cache_count_no));
				} else if (log.found == 1) {
					((TextView) rowView.findViewById(R.id.count)).setText(res.getString(R.string.cache_count_one));
				} else {
					((TextView) rowView.findViewById(R.id.count)).setText(log.found + " " + res.getString(R.string.cache_count_more));
				}
				// avoid parsing HTML if not necessary
				if (log.log.indexOf('<') >= 0 || log.log.indexOf('&') >= 0) {
					((TextView) rowView.findViewById(R.id.log)).setText(Html.fromHtml(log.log, new cgHtmlImg(this, null, false, cache.reason, false), null), TextView.BufferType.SPANNABLE);
				}
				else {
					((TextView) rowView.findViewById(R.id.log)).setText(log.log);
				}
				// add LogImages
				LinearLayout logLayout = (LinearLayout) rowView.findViewById(R.id.log_layout);

				if ((log.logImages != null) && (!log.logImages.isEmpty())) {
					for (int i_img_cnt = 0; i_img_cnt < log.logImages.size(); i_img_cnt++) {
						String img_title = log.logImages.get(i_img_cnt).title;
						if (img_title.equals("")) {
							img_title = res.getString(R.string.cache_log_image_default_title);
						}
						final String title = img_title;
						final String url = log.logImages.get(i_img_cnt).url;
						LinearLayout log_imgView = (LinearLayout) inflater.inflate(R.layout.log_img, null);
						TextView log_img_title = (TextView)log_imgView.findViewById(R.id.title);
						log_img_title.setText(title);
						log_img_title.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								Intent logImgIntent = new Intent(cgeodetail.this, cgeoimages.class);
								logImgIntent.putExtra("geocode", geocode.toUpperCase());
								logImgIntent.putExtra("type", cgeoimages.LOG_IMAGE);
								logImgIntent.putExtra("title", title);
								logImgIntent.putExtra("url", url);
								startActivity(logImgIntent);
							}
						});
						logLayout.addView(log_imgView);
					}
				}

				// Add colored mark
				final ImageView logMark = (ImageView) rowView.findViewById(R.id.log_mark);
				if (log.type == cgBase.LOG_FOUND_IT
				        || log.type == cgBase.LOG_WEBCAM_PHOTO_TAKEN
				        || log.type == cgBase.LOG_ATTENDED)
				{
				    logMark.setImageResource(R.drawable.mark_green);
				}
				else if (log.type == cgBase.LOG_PUBLISH_LISTING
                        || log.type == cgBase.LOG_ENABLE_LISTING
                        || log.type == cgBase.LOG_OWNER_MAINTENANCE)
                {
                    logMark.setImageResource(R.drawable.mark_green_more);
                }
				else if (log.type == cgBase.LOG_DIDNT_FIND_IT
				        || log.type == cgBase.LOG_NEEDS_MAINTENANCE
				        || log.type == cgBase.LOG_NEEDS_ARCHIVE)
				{
				    logMark.setImageResource(R.drawable.mark_red);
				}
				else if (log.type == cgBase.LOG_TEMP_DISABLE_LISTING
				        || log.type == cgBase.LOG_ARCHIVE)
				{
				    logMark.setImageResource(R.drawable.mark_red_more);
				}
				else
				{
				    logMark.setVisibility(View.GONE);
				}

				((TextView) rowView.findViewById(R.id.author)).setOnClickListener(new userActions());
				((TextView) logLayout.findViewById(R.id.log)).setOnClickListener(new decryptLog());

				listView.addView(rowView);
			}

			if (cache.logs.size() > 0) {
				((LinearLayout) findViewById(R.id.log_box)).setVisibility(View.VISIBLE);
			}
		}
	}

	private class loadCache extends Thread {

		private Handler handler = null;

		public loadCache(Handler handlerIn) {
			handler = handlerIn;

			if (geocode == null && guid == null) {
				showToast(res.getString(R.string.err_detail_cache_forgot));

				finish();
				return;
			}
		}

		@Override
		public void run() {
			HashMap<String, String> params = new HashMap<String, String>();
			if (geocode != null && geocode.length() > 0) {
				params.put("geocode", geocode);
			} else if (guid != null && guid.length() > 0) {
				params.put("guid", guid);
			} else {
				return;
			}

			searchId = base.searchByGeocode(params, 0, false);

			handler.sendMessage(new Message());
		}
	}

	private class loadMapPreview extends Thread {
		private Handler handler = null;

		public loadMapPreview(Handler handlerIn) {
			handler = handlerIn;
		}

		@Override
		public void run() {
			if (cache == null || cache.latitude == null || cache.longitude == null) {
				return;
			}

			BitmapDrawable image = null;

			try {
				final String latlonMap = String.format((Locale) null, "%.6f", cache.latitude) + "," + String.format((Locale) null, "%.6f", cache.longitude);
				final Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

				int width = display.getWidth();
				int height = (int) (90 * pixelRatio);

				String markerUrl = cgBase.urlencode_rfc3986("http://cgeo.carnero.cc/_markers/my_location_mdpi.png");

				cgHtmlImg mapGetter = new cgHtmlImg(cgeodetail.this, cache.geocode, false, 0, false);
				image = mapGetter.getDrawable("http://maps.google.com/maps/api/staticmap?center=" + latlonMap + "&zoom=15&size=" + width + "x" + height + "&maptype=terrain&markers=icon%3A" + markerUrl + "%7C" + latlonMap + "&sensor=false");
				Message message = handler.obtainMessage(0, image);
				handler.sendMessage(message);
			} catch (Exception e) {
				Log.w(cgSettings.tag, "cgeodetail.loadMapPreview.run: " + e.toString());
			}
		}
	}

	public void loadLongDesc() {
		if (waitDialog == null || waitDialog.isShowing() == false) {
			descDialog = ProgressDialog.show(this, null, res.getString(R.string.cache_dialog_loading_description), true);
			descDialog.setCancelable(true);
		}

		threadLongDesc = new loadLongDesc(loadDescriptionHandler);
		threadLongDesc.start();
	}

	private class loadLongDesc extends Thread {
		private Handler handler = null;

		public loadLongDesc(Handler handlerIn) {
			handler = handlerIn;
		}

		@Override
		public void run() {
			if (cache == null || cache.description == null || handler == null) {
				return;
			}
			parseLongDescription();
			handler.sendMessage(new Message());
		}
	}

	public ArrayList<cgCoord> getCoordinates() {
		cgCoord coords = null;
		ArrayList<cgCoord> coordinates = new ArrayList<cgCoord>();

		try {
			// cache
			coords = new cgCoord();
			coords.type = "cache";
			if (name != null && name.length() > 0) {
				coords.name = name;
			} else {
				coords.name = geocode.toUpperCase();
			}
			coords.latitude = cache.latitude;
			coords.longitude = cache.longitude;
			coordinates.add(coords);
		} catch (Exception e) {
			Log.e(cgSettings.tag, "cgeodetail.getCoordinates (cache): " + e.toString());
		}

		try {
			// waypoints
			for (cgWaypoint waypoint : cache.waypoints) {
				if (waypoint.latitude == null || waypoint.longitude == null) {
					continue;
				}

				coords = new cgCoord();
				coords.type = "waypoint";
				coords.name = waypoint.name;
				coords.latitude = waypoint.latitude;
				coords.longitude = waypoint.longitude;
				coordinates.add(coords);
			}
		} catch (Exception e) {
			Log.e(cgSettings.tag, "cgeodetail.getCoordinates (waypoint): " + e.toString());
		}

		return coordinates;
	}

	private void cachesAround() {
		cgeocaches.startActivityCachesAround(this, cache.latitude, cache.longitude);

		finish();
	}

	private void addToCalendar() {
		String[] projection = new String[] { "_id", "displayName" };
		Uri calendarProvider = Compatibility.getCalendarProviderURI();

		Cursor cursor = managedQuery(calendarProvider, projection, "selected=1", null, null);

		calendars.clear();
		int cnt = 0;
		if (cursor != null) {
			cnt = cursor.getCount();

			if (cnt > 0) {
				cursor.moveToFirst();

				int calId = 0;
				String calIdPre = null;
				String calName = null;
				int calIdIn = cursor.getColumnIndex("_id");
				int calNameIn = cursor.getColumnIndex("displayName");

				do {
					calIdPre = cursor.getString(calIdIn);
					if (calIdPre != null) {
						calId = new Integer(calIdPre);
					}
					calName = cursor.getString(calNameIn);

					if (calId > 0 && calName != null) {
						calendars.put(calId, calName);
					}
				} while (cursor.moveToNext());
			}
		}

		final CharSequence[] items = calendars.values().toArray(new CharSequence[calendars.size()]);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.cache_calendars);
		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				addToCalendarFn(item);
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void addToCalendarFn(int index) {
		if (calendars == null || calendars.isEmpty()) {
			return;
		}

		try {
			Uri calendarProvider = Compatibility.getCalenderEventsProviderURI();

			final Integer[] keys = calendars.keySet().toArray(new Integer[calendars.size()]);
			final Integer calId = keys[index];

			final Date eventDate = cache.hidden;
			eventDate.setHours(0);
			eventDate.setMinutes(0);
			eventDate.setSeconds(0);

			StringBuilder description = new StringBuilder();
			description.append("http://coord.info/");
			description.append(cache.geocode.toUpperCase());
			description.append("\n\n");
			if (cache.shortdesc != null && cache.shortdesc.length() > 0) {
				description.append(Html.fromHtml(cache.shortdesc).toString());
			}

			if (cache.personalNote != null && cache.personalNote.length() > 0) {
				description.append("\n\n"+Html.fromHtml(cache.personalNote).toString());
			}

			ContentValues event = new ContentValues();
			event.put("calendar_id", calId);
			event.put("dtstart", eventDate.getTime() + 43200000); // noon
			event.put("dtend", eventDate.getTime() + 43200000 + 3600000); // + one hour
			event.put("eventTimezone", "UTC");
			event.put("title", Html.fromHtml(cache.name).toString());
			event.put("description", description.toString());
			String location = "";
			if (cache.latitudeString != null && cache.latitudeString.length() > 0 && cache.longitudeString != null && cache.longitudeString.length() > 0) {
				location += cache.latitudeString + " " + cache.longitudeString;
			}
			if (cache.location != null && cache.location.length() > 0) {
				boolean addParenteses = false;
				if (location.length() > 0) {
					addParenteses = true;
					location += " (";
				}

				location += Html.fromHtml(cache.location).toString();
				if (addParenteses) {
					location += ")";
				}
			}
			if (location.length() > 0) {
				event.put("eventLocation", location);
			}
			event.put("allDay", 1);
			event.put("hasAlarm", 0);

			getContentResolver().insert(calendarProvider, event);

			showToast(res.getString(R.string.event_success));
		} catch (Exception e) {
			showToast(res.getString(R.string.event_fail));

			Log.e(cgSettings.tag, "cgeodetail.addToCalendarFn: " + e.toString());
		}
	}

	private void navigateTo() {
		if (cache == null || cache.latitude == null || cache.longitude == null) {
			showToast(res.getString(R.string.err_location_unknown));
		}

		cgeonavigate navigateActivity = new cgeonavigate();

		Intent navigateIntent = new Intent(this, navigateActivity.getClass());
		navigateIntent.putExtra("latitude", cache.latitude);
		navigateIntent.putExtra("longitude", cache.longitude);
		navigateIntent.putExtra("geocode", cache.geocode.toUpperCase());
		navigateIntent.putExtra("name", cache.name);

		if (cgeonavigate.coordinates != null) {
			cgeonavigate.coordinates.clear();
		}
		cgeonavigate.coordinates = getCoordinates();
		startActivity(navigateIntent);
	}

	public void shareCache() {
		if (geocode == null && cache == null) {
			return;
		}

		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");

		if (cache != null && cache.geocode != null) {
			String subject = cache.geocode.toUpperCase();
			if (cache.name != null && cache.name.length() > 0){
				subject = subject + " - " + cache.name;
			}
			intent.putExtra(Intent.EXTRA_SUBJECT, "Geocache " + subject);
			intent.putExtra(Intent.EXTRA_TEXT, "http://coord.info/" + cache.geocode.toUpperCase());
		} else if (geocode != null) {
			intent.putExtra(Intent.EXTRA_SUBJECT, "Geocache " + geocode.toUpperCase());
			intent.putExtra(Intent.EXTRA_TEXT, "http://coord.info/" + geocode.toUpperCase());
		}

		startActivity(Intent.createChooser(intent, res.getText(R.string.action_bar_share_title)));
	}

	private class waypointInfo implements View.OnClickListener {
		private int id = -1;

		public waypointInfo(int idIn) {
			id = idIn;
		}

		public void onClick(View arg0) {
			Intent waypointIntent = new Intent(cgeodetail.this, cgeowaypoint.class);
			waypointIntent.putExtra("waypoint", id);
			waypointIntent.putExtra("geocode", cache.geocode);
			startActivity(waypointIntent);
		}
	}

	private void logVisit() {
		cache.logVisit(this);
	}

	private void showSpoilers() {
		if (cache == null || cache.spoilers == null || cache.spoilers.isEmpty()) {
			showToast(res.getString(R.string.err_detail_no_spoiler));
		}

		Intent spoilersIntent = new Intent(this, cgeoimages.class);
		spoilersIntent.putExtra("geocode", geocode.toUpperCase());
		spoilersIntent.putExtra("type", cgeoimages.SPOILER_IMAGE);
		startActivity(spoilersIntent);
	}

	public class codeHint implements View.OnClickListener {
		public void onClick(View arg0) {
			// code hint
			TextView hintView = ((TextView) findViewById(R.id.hint));
			hintView.setText(cgBase.rot13(hintView.getText().toString()));

		}
	}

	private class update extends cgUpdateLoc {
		@Override
		public void updateLoc(cgGeo geo) {
			if (geo == null) {
				return;
			}
			if (cacheDistance == null) {
				return;
			}

			try {
				StringBuilder dist = new StringBuilder();

				if (geo.latitudeNow != null && geo.longitudeNow != null && cache != null && cache.latitude != null && cache.longitude != null) {
					dist.append(base.getHumanDistance(cgBase.getDistance(geo.latitudeNow, geo.longitudeNow, cache.latitude, cache.longitude)));
				}

				if (cache != null && cache.elevation != null) {
					if (geo.altitudeNow != null) {
						Double diff = (cache.elevation - geo.altitudeNow);
						if (diff >= 0) {
							dist.append(" ↗");
						} else if (diff < 0) {
							dist.append(" ↘");
						}
						if (settings.units == cgSettings.unitsImperial) {
							dist.append(String.format(Locale.getDefault(), "%.0f", (Math.abs(diff) * 3.2808399)));
							dist.append(" ft");
						} else {
							dist.append(String.format(Locale.getDefault(), "%.0f", (Math.abs(diff))));
							dist.append(" m");
						}
					}
				}

				cacheDistance.setText(dist.toString());
				cacheDistance.bringToFront();
			} catch (Exception e) {
				Log.w(cgSettings.tag, "Failed to update location.");
			}
		}
	}

	private class selectTrackable implements View.OnClickListener {
		public void onClick(View arg0) {
			// show list of trackables
			try {
				Intent trackablesIntent = new Intent(cgeodetail.this, cgeotrackables.class);
				trackablesIntent.putExtra("geocode", geocode.toUpperCase());
				startActivity(trackablesIntent);
			} catch (Exception e) {
				Log.e(cgSettings.tag, "cgeodetail.selectTrackable: " + e.toString());
			}
		}
	}

	private class storeCache implements View.OnClickListener {
		public void onClick(View arg0) {
			if (dropDialog != null && dropDialog.isShowing()) {
				showToast(res.getString(R.string.err_detail_still_removing));
				return;
			}
			if (refreshDialog != null && refreshDialog.isShowing()) {
				showToast(res.getString(R.string.err_detail_still_refreshing));
				return;
			}

			storeDialog = ProgressDialog.show(cgeodetail.this, res.getString(R.string.cache_dialog_offline_save_title), res.getString(R.string.cache_dialog_offline_save_message), true);
			storeDialog.setCancelable(true);

			if (storeThread != null) {
				storeThread.interrupt();
			}

			storeThread = new storeCacheThread(storeCacheHandler);
			storeThread.start();
		}
	}

	private class refreshCache implements View.OnClickListener {
		public void onClick(View arg0) {
			if (dropDialog != null && dropDialog.isShowing()) {
				showToast(res.getString(R.string.err_detail_still_removing));
				return;
			}
			if (storeDialog != null && storeDialog.isShowing()) {
				showToast(res.getString(R.string.err_detail_still_saving));
				return;
			}

			refreshDialog = ProgressDialog.show(cgeodetail.this, res.getString(R.string.cache_dialog_refresh_title), res.getString(R.string.cache_dialog_refresh_message), true);
			refreshDialog.setCancelable(true);

			if (refreshThread != null) {
				refreshThread.interrupt();
			}

			refreshThread = new refreshCacheThread(refreshCacheHandler);
			refreshThread.start();
		}
	}

	private class storeCacheThread extends Thread {
		private Handler handler = null;

		public storeCacheThread(Handler handlerIn) {
			handler = handlerIn;
		}

		@Override
		public void run() {
			int reason = (cache.reason > 1) ? cache.reason : 1;
			base.storeCache(app, cgeodetail.this, cache, null, reason, handler);
		}
	}

	private class refreshCacheThread extends Thread {
		private Handler handler = null;

		public refreshCacheThread(Handler handlerIn) {
			handler = handlerIn;
		}

		@Override
		public void run() {
			app.removeCacheFromCache(geocode);

			final HashMap<String, String> params = new HashMap<String, String>();
			params.put("geocode", cache.geocode);
			searchId = base.searchByGeocode(params, 0, true);

			handler.sendEmptyMessage(0);
		}
	}

	private class dropCache implements View.OnClickListener {
		public void onClick(View arg0) {
			if (storeDialog != null && storeDialog.isShowing()) {
				showToast(res.getString(R.string.err_detail_still_saving));
				return;
			}
			if (refreshDialog != null && refreshDialog.isShowing()) {
				showToast(res.getString(R.string.err_detail_still_refreshing));
				return;
			}

			dropDialog = ProgressDialog.show(cgeodetail.this, res.getString(R.string.cache_dialog_offline_drop_title), res.getString(R.string.cache_dialog_offline_drop_message), true);
			dropDialog.setCancelable(false);
			Thread thread = new dropCacheThread(dropCacheHandler);
			thread.start();
		}
	}

	private class dropCacheThread extends Thread {

		private Handler handler = null;

		public dropCacheThread(Handler handlerIn) {
			handler = handlerIn;
		}

		@Override
		public void run() {
			cgBase.dropCache(app, cgeodetail.this, cache, handler);
		}
	}

	/**
	 * Abstract Listener for add / remove buttons for watchlist
	 */
    private abstract class AbstractWatchlistClickListener implements View.OnClickListener {
        public void doExecute(int titleId, int messageId, Thread thread) {
            if (watchlistDialog != null  &&  watchlistDialog.isShowing()) {
                showToast(res.getString(R.string.err_watchlist_still_managing));
                return;
            }
            watchlistDialog = ProgressDialog.show(cgeodetail.this,
                    res.getString(titleId), res.getString(messageId), true);
            watchlistDialog.setCancelable(true);

            if (watchlistThread != null) {
                watchlistThread.interrupt();
            }

            watchlistThread = thread;
            watchlistThread.start();
        }
    }

	/**
	 * Listener for "add to watchlist" button
	 */
	private class AddToWatchlistClickListener extends AbstractWatchlistClickListener {
		public void onClick(View arg0) {
		    doExecute(R.string.cache_dialog_watchlist_add_title,
		            R.string.cache_dialog_watchlist_add_message,
		            new WatchlistAddThread(WatchlistHandler) );
		}
	}

	/**
	 * Listener for "remove from watchlist" button
	 */
	private class RemoveFromWatchlistClickListener extends AbstractWatchlistClickListener {
        public void onClick(View arg0) {
            doExecute(R.string.cache_dialog_watchlist_remove_title,
                    R.string.cache_dialog_watchlist_remove_message,
                    new WatchlistRemoveThread(WatchlistHandler) );
        }
	}

	/** Thread to add this cache to the watchlist of the user */
	private class WatchlistAddThread extends Thread {
	    private final Handler handler;
        public WatchlistAddThread(Handler handler) {
            this.handler = handler;
        }
        @Override
        public void run() {
            handler.sendEmptyMessage(base.addToWatchlist(cache));
        }
	}

    /** Thread to remove this cache from the watchlist of the user */
    private class WatchlistRemoveThread extends Thread {
        private final Handler handler;
        public WatchlistRemoveThread(Handler handler) {
            this.handler = handler;
        }
        @Override
        public void run() {
            handler.sendEmptyMessage(base.removeFromWatchlist(cache));
        }
    }

	private class addWaypoint implements View.OnClickListener {

		public void onClick(View view) {
			Intent addWptIntent = new Intent(cgeodetail.this, cgeowaypointadd.class);

			addWptIntent.putExtra("geocode", geocode);
			int wpCount = 0;
			if (cache.waypoints != null) {
				wpCount = cache.waypoints.size();
			}
			addWptIntent.putExtra("count", wpCount);

			startActivity(addWptIntent);
		}
	}

	private static class decryptLog implements View.OnClickListener {

		public void onClick(View view) {
			if (view == null) {
				return;
			}

			try {
				final TextView logView = (TextView)view;
				Spannable span = (Spannable) logView.getText();

				// I needed to re-implement the base.rot13() encryption here because we must work on
				// a SpannableStringBuilder instead of the pure text and we must replace each character inline.
				// Otherwise we loose all the images, colors and so on...
				SpannableStringBuilder buffer = new SpannableStringBuilder(span);
				boolean plaintext = false;

				int length = span.length();
				for (int index = 0; index < length; index++) {
					int c = span.charAt(index);
					if (c == '[') {
						plaintext = true;
					} else if (c == ']') {
						plaintext = false;
					} else if (!plaintext) {
						int capitalized = c & 32;
						c &= ~capitalized;
						c = ((c >= 'A') && (c <= 'Z') ? ((c - 'A' + 13) % 26 + 'A') : c)
								| capitalized;
					}
					buffer.replace(index, index + 1, String.valueOf((char) c));
				}
				logView.setText(buffer);
			} catch (Exception e) {
				// nothing
			}
		}
	}

	private class userActions implements View.OnClickListener {

		public void onClick(View view) {
			if (view == null) {
				return;
			}

			try {
				registerForContextMenu(view);
				openContextMenu(view);
			} catch (Exception e) {
				// nothing
			}
		}
	}

	public void goCompass(View view) {
		if (cache == null || cache.latitude == null || cache.longitude == null) {
			showToast(res.getString(R.string.cache_coordinates_no));

			return;
		}

		Intent navigateIntent = new Intent(this, cgeonavigate.class);
		navigateIntent.putExtra("latitude", cache.latitude);
		navigateIntent.putExtra("longitude", cache.longitude);
		navigateIntent.putExtra("geocode", cache.geocode.toUpperCase());
		navigateIntent.putExtra("name", cache.name);

		if (cgeonavigate.coordinates != null) {
			cgeonavigate.coordinates.clear();
		}
		cgeonavigate.coordinates = getCoordinates();
		startActivity(navigateIntent);
	}

	/**
	 * lazy-creates the layout holding the icons of the chaches attributes
	 * and makes it visible
	 */
	private void showAttributeIcons(LinearLayout attribBox, int parentWidth) {
		if (attributeIconsLayout == null) {
			attributeIconsLayout = createAttributeIconsLayout(parentWidth);
			// no matching icons found? show text
			if (noAttributeIconsFound) {
				showAttributeDescriptions(attribBox);
				return;
			}
		}
		attribBox.removeAllViews();
		attribBox.addView(attributeIconsLayout);
		attributesShowAsIcons = true;
	}

	/**
	 * lazy-creates the layout holding the discriptions of the chaches attributes
	 * and makes it visible
	 */
	private void showAttributeDescriptions(LinearLayout attribBox) {
		if (attributeDescriptionsLayout == null) {
			attributeDescriptionsLayout = createAttributeDescriptionsLayout();
		}
		attribBox.removeAllViews();
		attribBox.addView(attributeDescriptionsLayout);
		attributesShowAsIcons = false;
	}

	/**
	 * toggle attribute descriptions and icons
	 */
	private void toggleAttributeDisplay(LinearLayout attribBox, int parentWidth) {
		// Don't toggle when there are no icons to show.
		if (noAttributeIconsFound) {
			return;
		}

		// toggle
		if (attributesShowAsIcons) {
			showAttributeDescriptions(attribBox);
		} else {
			showAttributeIcons(attribBox, parentWidth);
		}
	}

	private ViewGroup createAttributeIconsLayout(int parentWidth) {
    	LinearLayout rows = new LinearLayout(this);
    	rows.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
    	rows.setOrientation(LinearLayout.VERTICAL);

    	LinearLayout attributeRow = newAttributeIconsRow();
        rows.addView(attributeRow);

        noAttributeIconsFound = true;

        for(String attributeName : cache.attributes) {
			boolean strikethru = attributeName.endsWith("_no");
			// cut off _yes / _no
			if (attributeName.endsWith("_no") || attributeName.endsWith("_yes")) {
				attributeName = attributeName.substring(0, attributeName.lastIndexOf("_"));
			}
			// check if another attribute icon fits in this row
        	attributeRow.measure(0, 0);
        	int rowWidth = attributeRow.getMeasuredWidth();
        	FrameLayout fl = (FrameLayout) inflater.inflate(R.layout.attribute_image, null);
        	ImageView iv = (ImageView) fl.getChildAt(0);
        	if ((parentWidth - rowWidth) < iv.getLayoutParams().width) {
        		// make a new row
        		attributeRow = newAttributeIconsRow();
        		rows.addView(attributeRow);
        	}

			// dynamically search icon of the attribute
        	Drawable d = null;
		    int id = res.getIdentifier("attribute_" + attributeName, "drawable",
		    		base.context.getPackageName());
		    if (id > 0) {
		        noAttributeIconsFound = false;
		    	d = res.getDrawable(id);
		        iv.setImageDrawable(d);
		        // strike through?
	        	if (strikethru) {
	        		// generate strikethru image with same properties as attribute image
	        		ImageView strikethruImage = new ImageView(this);
	        		strikethruImage.setLayoutParams(iv.getLayoutParams());
	        		d = res.getDrawable(R.drawable.attribute__strikethru);
	    	        strikethruImage.setImageDrawable(d);
	    	        fl.addView(strikethruImage);
	        	}
		    } else {
		    	d = res.getDrawable(R.drawable.attribute_icon_not_found);
		        iv.setImageDrawable(d);
		    }

        	attributeRow.addView(fl);
        }

    	return rows;
    }

    private LinearLayout newAttributeIconsRow() {
    	 LinearLayout rowLayout = new LinearLayout(this);
         rowLayout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                 LayoutParams.WRAP_CONTENT));
         rowLayout.setOrientation(LinearLayout.HORIZONTAL);
         return rowLayout;
    }

    private ViewGroup createAttributeDescriptionsLayout() {
    	final LinearLayout descriptions = (LinearLayout) inflater.inflate(
    			R.layout.attribute_descriptions, null);
    	TextView attribView = (TextView) descriptions.getChildAt(0);

		StringBuilder buffer = new StringBuilder();
		String attribute;
		for (int i = 0; i < cache.attributes.size(); i++) {
			attribute = cache.attributes.get(i);

			// dynamically search for a translation of the attribute
		    int id = res.getIdentifier("attribute_" + attribute, "string",
		    		base.context.getPackageName());
		    if (id > 0) {
		    	String translated = res.getString(id);
		    	if (translated != null && translated.length() > 0) {
		    		attribute = translated;
		    	}
		    }
		    if (buffer.length() > 0) buffer.append('\n');
		    buffer.append(attribute);
		}

		if (noAttributeIconsFound)
			buffer.append("\n\n").append(res.getString(R.string.cache_attributes_no_icons));

		attribView.setText(buffer);

        return descriptions;
    }

	public static void startActivity(final Context context, final String geocode) {
		final Intent detailIntent = new Intent(context, cgeodetail.class);
		detailIntent.putExtra("geocode", geocode.toUpperCase());
		context.startActivity(detailIntent);
	}
}
