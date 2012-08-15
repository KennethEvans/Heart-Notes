package net.kenevans.heartmonitor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Manages a database with entries for the number of Premature Ventricular
 * Contractions (PVCs) at a given time. The database implementation is similar
 * to the Notes example, but the database is on the SD card.
 */
public class HeartMonitorActivity extends ListActivity implements IConstants {
	/** Template for the name of the file written to the SD card */
	private static final String sdCardFileNameTemplate = "HeartMonitor.%s.txt";

	private HeartMonitorDbAdapter mDbHelper;
	private CustomCursorAdapter adapter;

	/** Array of hard-coded filters */
	protected Filter[] filters;
	/** The current filter. */
	private int filter = 0;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Create filters here so getText is available
		filters = new Filter[] {
				new Filter(getText(R.string.filter_none), null),
				new Filter(getText(R.string.filter_nonzero), COL_COUNT
						+ " <> 0"),
				new Filter(getText(R.string.filter_counttotal), COL_COUNT
						+ " = " + COL_TOTAL), };

		// Get the preferences here before refresh()
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		filter = prefs.getInt("filter", 0);
		if (filter < 0 || filter >= filters.length) {
			filter = 0;
		}

		mDbHelper = new HeartMonitorDbAdapter(this);
		mDbHelper.open();

		refresh();

		// Position it to the end
		positionListView(true);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
		case R.id.refresh:
			refresh();
			return true;
		case R.id.newdata:
			createData();
			return true;
		case R.id.savetext:
			save();
			return true;
		case R.id.toend:
			positionListView(true);
			return true;
		case R.id.tostart:
			positionListView(false);
			return true;
		case R.id.filter:
			setFilter();
			return true;
		}
		return false;
	}

	@Override
	protected void onListItemClick(ListView lv, View view, int position, long id) {
		super.onListItemClick(lv, view, position, id);
		Intent i = new Intent(this, DataEditActivity.class);
		i.putExtra(COL_ID, id);
		startActivityForResult(i, ACTIVITY_EDIT);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		refresh();
	}

	// @Override
	// public File getDatabasePath(String name) {
	// File file = null;
	// File sdCardRoot = Environment.getExternalStorageDirectory();
	// if (sdCardRoot.canWrite()) {
	// File dir = new File(sdCardRoot, SD_CARD_DB_DIRECTORY);
	// file = new File(dir, name);
	// }
	// return file;
	// }

	public static File getDatabaseDirectory() {
		File dir = null;
		File sdCardRoot = Environment.getExternalStorageDirectory();
		if (sdCardRoot.canWrite()) {
			dir = new File(sdCardRoot, SD_CARD_DB_DIRECTORY);
		}
		return dir;
	}

	private void createData() {
		Intent i = new Intent(this, DataEditActivity.class);
		startActivityForResult(i, ACTIVITY_CREATE);
		// Date date = new Date();
		// Date dateMod = date;
		// int count = (int) Math.round(Math.random() * 60);
		// int total = 60;
		// String comment = "This is a test";
		// mDbHelper.createData(date.getTime(), dateMod.getTime(), count, total,
		// false, comment);
	}

	/**
	 * Format the date using the static format.
	 * 
	 * @param dateNum
	 * @return
	 * @see #longFormat
	 */
	public static String formatDate(Long dateNum) {
		return formatDate(HeartMonitorActivity.longFormatter, dateNum);
	}

	/**
	 * Format the date using the given format.
	 * 
	 * @param formatter
	 * @param dateNum
	 * @return
	 * @see #longFormat
	 */
	public static String formatDate(SimpleDateFormat formatter, Long dateNum) {
		// Consider using Date.toString() as it might be more locale
		// independent.
		if (dateNum == null) {
			return "<Unknown>";
		}
		if (dateNum == -1) {
			// Means the column was not found in the database
			return "<Date NA>";
		}
		// Consider using Date.toString()
		// It might be more locale independent.
		// return new Date(dateNum).toString();

		// Include the dateNum
		// return dateNum + " " + formatter.format(dateNum);

		return formatter.format(dateNum);
	}

	/**
	 * Positions the list view.
	 * 
	 * @param toEnd
	 *            True to go to the end, false to go to the beginning.
	 */
	private void positionListView(final boolean toEnd) {
		final ListView lv = this.getListView();
		if (lv == null) {
			Utils.errMsg(this, "Error positioning ListView");
			return;
		}
		lv.post(new Runnable() {
			public void run() {
				int pos = toEnd ? lv.getCount() - 1 : 0;
				lv.setSelection(pos);
			}
		});
	}

	/**
	 * Saves the info to the SD card
	 */
	private void save() {
		BufferedWriter out = null;
		Cursor cursor = null;
		try {
			File dir = getDatabaseDirectory();
			if (dir == null) {
				Utils.errMsg(this, "Error saving to SD card");
				return;
			}
			String format = "yyyy-MM-dd-HHmmss";
			SimpleDateFormat formatter = new SimpleDateFormat(format);
			Date now = new Date();
			String fileName = String.format(sdCardFileNameTemplate,
					formatter.format(now), now.getTime());
			File file = new File(dir, fileName);
			FileWriter writer = new FileWriter(file);
			out = new BufferedWriter(writer);
			cursor = mDbHelper.fetchAllData(filters[filter].selection);
			// int indexId = cursor.getColumnIndex(COL_ID);
			int indexDate = cursor.getColumnIndex(COL_DATE);
			// int indexDateMod = cursor.getColumnIndex(COL_DATEMOD);
			int indexCount = cursor.getColumnIndex(COL_COUNT);
			int indexTotal = cursor.getColumnIndex(COL_TOTAL);
			// indexEdited = cursor.getColumnIndex(COL_EDITED);
			int indexComment = cursor.getColumnIndex(COL_COMMENT);
			// Loop over items
			cursor.moveToFirst();
			String comment, info, date;
			long count, total, dateNum;
			while (cursor.isAfterLast() == false) {
				comment = "<None>";
				if (indexComment > -1) {
					comment = cursor.getString(indexComment);
				}
				date = "<Unknown>";
				if (indexDate > -1) {
					dateNum = cursor.getLong(indexDate);
					date = formatDate(dateNum);
				}
				count = -1;
				if (indexCount > -1) {
					count = cursor.getInt(indexCount);
				}
				total = -1;
				if (indexTotal > -1) {
					total = cursor.getInt(indexTotal);
				}
				info = String.format("%2d/%d \t%s \t%s\n", count, total, date,
						comment);
				out.write(info);
				cursor.moveToNext();
			}
			Utils.infoMsg(this, "Wrote " + file.getPath());
		} catch (Exception ex) {
			Utils.excMsg(this, "Error saving to SD card", ex);
		} finally {
			try {
				cursor.close();
			} catch (Exception ex) {
				// Do nothing
			}
			try {
				out.close();
			} catch (Exception ex) {
				// Do nothing
			}
		}
	}

	/**
	 * Bring up a dialog to change the filter order.
	 */
	private void setFilter() {
		final CharSequence[] items = new CharSequence[filters.length];
		for (int i = 0; i < filters.length; i++) {
			items[i] = filters[i].name;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getText(R.string.filter_title));
		builder.setSingleChoiceItems(items, filter,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						dialog.dismiss();
						if (item < 0 || item >= filters.length) {
							Utils.errMsg(HeartMonitorActivity.this,
									"Invalid filter");
							filter = 0;
						} else {
							filter = item;
						}
						refresh();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Gets a new cursor and starts managing it.
	 */
	private void refresh() {
		try {
			// Get the available columns from all rows
			// String selection = COL_ID + "<=76" + " OR " + COL_ID + "=13";
			Cursor cursor = mDbHelper.fetchAllData(filters[filter].selection);
			// editingCursor = getContentResolver().query(editingURI, columns,
			// "type=?", new String[] { "1" }, "_id DESC");
			startManagingCursor(cursor);

			// Manage the adapter
			if (adapter == null) {
				// Set a custom cursor adapter
				adapter = new CustomCursorAdapter(getApplicationContext(),
						cursor);
				setListAdapter(adapter);
			} else {
				// This should close the current cursor and start using the new
				// one, hopefully avoiding memory leaks.
				adapter.changeCursor(cursor);
			}
		} catch (Exception ex) {
			Utils.excMsg(this, "Error finding messages", ex);
		}

		// Save the preferences
		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putInt("filter", filter);
		editor.commit();
	}

	/**
	 * Class to manage a filter.
	 */
	private static class Filter {
		private CharSequence name;
		private String selection;

		private Filter(CharSequence menuName, String selection) {
			this.name = menuName;
			this.selection = selection;
		}
	}

	private class CustomCursorAdapter extends CursorAdapter {
		private LayoutInflater inflater;
		private int indexId;
		private int indexDate;
		private int indexCount;
		private int indexTotal;
		// private int indexDateMod;
		// private int indexEdited;
		private int indexComment;

		public CustomCursorAdapter(Context context, Cursor cursor) {
			super(context, cursor);
			inflater = LayoutInflater.from(context);
			indexId = cursor.getColumnIndex(COL_ID);
			indexDate = cursor.getColumnIndex(COL_DATE);
			// indexDateMod = cursor.getColumnIndex(COL_DATEMOD);
			indexCount = cursor.getColumnIndex(COL_COUNT);
			indexTotal = cursor.getColumnIndex(COL_TOTAL);
			// indexEdited = cursor.getColumnIndex(COL_EDITED);
			indexComment = cursor.getColumnIndex(COL_COMMENT);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			TextView title = (TextView) view.findViewById(R.id.title);
			TextView subtitle = (TextView) view.findViewById(R.id.subtitle);
			String id = cursor.getString(indexId);
			String comment = "<Comment NA>";
			if (indexComment > -1) {
				comment = cursor.getString(indexComment);
			}
			Long dateNum = -1L;
			if (indexDate > -1) {
				dateNum = cursor.getLong(indexDate);
			}
			int count = -1;
			if (indexCount > -1) {
				count = cursor.getInt(indexCount);
			}
			int total = -1;
			if (indexTotal > -1) {
				total = cursor.getInt(indexTotal);
			}
			title.setText(id + ": " + count + "/" + total + " at "
					+ formatDate(dateNum));
			subtitle.setText(comment);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			return inflater.inflate(R.layout.list_row, null);
		}

	}

}