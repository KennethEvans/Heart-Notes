package net.kenevans.heartmonitor;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
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
	private HeartMonitorDbAdapter mDbHelper;
	private CustomCursorAdapter adapter;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		File dbFile = getDatabasePath("dummy");
		Log.d(TAG, this.getClass().getSimpleName() + ".onCreate: "
				+ " getDatabasePath(\"dummy\")=" + dbFile);
		String[] databases = databaseList();
		if (databases.length == 0) {
			Log.d(TAG, "    No databases found");
		} else {
			for (String db : databases) {
				Log.d(TAG, "    " + db);
			}
		}

		mDbHelper = new HeartMonitorDbAdapter(this);
		mDbHelper.open();

		refresh();
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
	 * Gets a new cursor and starts managing it.
	 */
	private void refresh() {
		try {
			// First get the names of all the columns in the database
			Cursor cursor = mDbHelper.fetchAllData();
			String[] avaliableColumns = cursor.getColumnNames();
			cursor.close();

			// Make an array of the desired ones that are available
			String[] desiredColumns = { COL_ID, COL_DATE, COL_DATEMOD,
					COL_EDITED, COL_COMMENT };
			ArrayList<String> list = new ArrayList<String>();
			for (String col : desiredColumns) {
				for (String col1 : avaliableColumns) {
					if (col.equals(col1)) {
						list.add(col);
						break;
					}
				}
			}
			String[] columns = new String[list.size()];
			list.toArray(columns);

			// Get the available columns from all rows
			// String selection = COL_ID + "<=76" + " OR " + COL_ID + "=13";
			cursor = mDbHelper.fetchAllData();
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