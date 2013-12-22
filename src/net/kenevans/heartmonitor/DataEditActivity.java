package net.kenevans.heartmonitor;

import java.io.File;
import java.text.ParseException;
import java.util.Date;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Manages editing a set of data. Done similarly to the Notes example.
 */
public class DataEditActivity extends Activity implements IConstants {
	private HeartMonitorDbAdapter mDbHelper;
	private EditText mCountText;
	private EditText mTotalText;
	private EditText mDateText;
	private EditText mDateModText;
	private EditText mEditedText;
	private EditText mCommentText;
	private Long mRowId;
	private File mDataDir;

	/** Possible values for the edit state. */
	private enum State {
		SAVED, DELETED, CANCELLED
	};

	/**
	 * The edit state. Since the initial state is cancelled, if the system calls
	 * pause, then it will be cancelled and not saved.
	 */
	private State state = State.CANCELLED;

	/** Task for getting the temperature from the web */
	private GetWeatherTask updateTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Debug
		Log.v(TAG, "onCreate called");

		// The default state is cancelled and won't be changed until the users
		// selects one of the buttons
		state = State.CANCELLED;

		setContentView(R.layout.data_edit);

		mCountText = (EditText) findViewById(R.id.count);
		mTotalText = (EditText) findViewById(R.id.total);
		mDateText = (EditText) findViewById(R.id.date);
		mDateModText = (EditText) findViewById(R.id.datemod);
		mEditedText = (EditText) findViewById(R.id.edited);
		mCommentText = (EditText) findViewById(R.id.comment);

		mRowId = (savedInstanceState == null) ? null
				: (Long) savedInstanceState.getSerializable(COL_ID);
		if (mRowId == null) {
			Bundle extras = getIntent().getExtras();
			mRowId = extras != null ? extras.getLong(COL_ID) : null;
			String dataDirName = extras != null ? extras
					.getString(PREF_DATA_DIRECTORY) : null;
			if (dataDirName != null) {
				mDataDir = new File(dataDirName);
			}
		}

		mDbHelper = new HeartMonitorDbAdapter(this, mDataDir);
		mDbHelper.open();

		// Save
		Button button = (Button) findViewById(R.id.save);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				// Debug
				Log.v(TAG, "Save Button");
				state = State.SAVED;
				setResult(RESULT_OK);
				finish();
			}
		});

		// Delete
		button = (Button) findViewById(R.id.delete);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				// Debug
				Log.v(TAG, "Delete Button");
				state = State.DELETED;
				setResult(RESULT_OK);
				finish();
			}
		});

		// Cancel
		button = (Button) findViewById(R.id.cancel);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				// Debug
				Log.v(TAG, "Cancel Button");
				state = State.CANCELLED;
				setResult(RESULT_CANCELED);
				finish();
			}
		});

		populateFields();
	}

	// This was used in the notes example. It only applies to kill, not
	// onResume.
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		saveState();
		outState.putSerializable(COL_ID, mRowId);
	}

	@Override
	protected void onPause() {
		// Debug
		Log.v(TAG, "onPause called");
		super.onPause();
		// This gets called on every pause. Since the state is cancelled until a
		// button is pushed, it doesn't do anything until a button is is pressed
		// and calls finish().
		saveState();
	}

	@Override
	protected void onResume() {
		// DEBUG
		Log.v(TAG, "onResume called");
		super.onResume();
		// We don't want to do this here. It causes the EditTexts to revert to
		// the original values instead of what has been edited so far.
		// populateFields();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.editmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
		case R.id.weather:
			insertWeather();
			return true;
		}
		return false;
	}

	private void insertWeather() {
		// FIXME
		// Workaround until Google Weather API becomes available or a substitute
		// is found
		// mCommentText.append("Temp: Humidity: %");

		if (updateTask != null) {
			// Don't do anything if we are updating
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": getStrip: updateTask is not null");
			return;
		}
		updateTask = new GetWeatherTask();
		updateTask.execute();
	}

	private void saveState() {
		// DEBUG
		Log.v(TAG, "saveState called mRowId=" + mRowId + " state=" + state);
		// Do nothing if cancelled
		if (state == State.CANCELLED) {
			return;
		}
		// Delete if deleted and there is a row ID
		if (state == State.DELETED) {
			if (mRowId != null) {
				mDbHelper.deleteData(mRowId);
			}
			return;
		}
		// Remaining state is saved, get the entries
		// Don't use entries for edited and dateMod, set them
		String comment;
		long count = 0, total = 0, date = 0, dateMod = 0;
		boolean edited = false;
		String string = null;
		try {
			comment = mCommentText.getText().toString();
			string = mCountText.getText().toString();
			count = Long.parseLong(string);
			string = mTotalText.getText().toString();
			total = Long.parseLong(string);
			string = mDateText.getText().toString();
			Date testDate = null;
			try {
				testDate = longFormatter.parse(string);
			} catch (ParseException ex) {
				Utils.excMsg(this, "Cannot parse the date", ex);
				return;
			}
			date = testDate.getTime();
		} catch (Exception ex) {
			Utils.excMsg(this, "Failed to parse the entered values", ex);
			return;
		}
		// Save the values
		dateMod = new Date().getTime();
		if (mRowId == null) {
			// Is new
			edited = false;
			long id = mDbHelper.createData(date, dateMod, count, total, edited,
					comment);
			if (id > 0) {
				mRowId = id;
			}
		} else {
			// Is edited
			edited = true;
			mDbHelper.updateData(mRowId, date, dateMod, count, total, edited,
					comment);
		}
	}

	/**
	 * Initializes the fields.
	 */
	private void populateFields() {
		if (mRowId != null) {
			Cursor cursor = mDbHelper.fetchData(mRowId);
			startManagingCursor(cursor);
			mCountText.setText(cursor.getString(cursor
					.getColumnIndexOrThrow(COL_COUNT)));
			mTotalText.setText(cursor.getString(cursor
					.getColumnIndexOrThrow(COL_TOTAL)));
			long time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DATE));
			mDateText.setText(HeartMonitorActivity.formatDate(time));
			time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DATEMOD));
			mDateModText.setText(HeartMonitorActivity.formatDate(time));
			long val = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EDITED));
			mEditedText.setText(val == 0 ? "false" : "true");
			mCommentText.setText(cursor.getString(cursor
					.getColumnIndexOrThrow(COL_COMMENT)));
		} else {
			// A new data, set defaults
			mCountText.setText("0");
			mTotalText.setText("60");
			mEditedText.setText("false");
			Date now = new Date();
			mDateText.setText(HeartMonitorActivity.formatDate(now.getTime()));
			mDateModText
					.setText(HeartMonitorActivity.formatDate(now.getTime()));
		}
	}

	private class GetWeatherTask extends AsyncTask<Void, Void, Boolean> {
		private ProgressDialog dialog;
		private String[] vals;

		// public GetWeatherTask() {
		// super();
		// }

		@Override
		protected void onPreExecute() {
			// DEBUG TIME
			// Log.d(TAG, this.getClass().getSimpleName()
			// + ": onPreExecute: delta(1)=" + getDeltaTime());
			dialog = new ProgressDialog(DataEditActivity.this);
			dialog.setMessage("Getting Weather");
			dialog.setCancelable(true);
			dialog.setIndeterminate(true);
			dialog.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					Log.d(TAG, GetWeatherTask.this.getClass().getSimpleName()
							+ ": ProgressDialog.onCancel: Cancelled");
					if (updateTask != null) {
						updateTask.cancel(true);
						updateTask = null;
					}
				}
			});
			dialog.show();
			// DEBUG TIME
			// Log.d(TAG, this.getClass().getSimpleName()
			// + ": onPreExecute: delta(2)=" + getDeltaTime());
		}

		@Override
		protected Boolean doInBackground(Void... dummy) {
			// DEBUG TIME
			// Log.d(TAG, this.getClass().getSimpleName()
			// + ": doInBackground: delta=" + getDeltaTime());

			// Up the priority
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

			// vals = GoogleLocationUtils
			// .getGoogleWeather(DataEditActivity.this);
			vals = LocationUtils.getWundWeather(DataEditActivity.this);
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": onPostExecute: result=" + result);
			if (dialog != null) {
				dialog.dismiss();
			}
			// // Should not be called if it is cancelled
			// if (isCancelled()) {
			// updateTask = null;
			// Log.d(TAG, this.getClass().getSimpleName()
			// + ": onPostExecute: isCancelled");
			// return;
			// }
			updateTask = null;
			if (vals == null) {
				Utils.errMsg(DataEditActivity.this, "Failed to get weather");
				mCommentText.append("Weather NA.");
				return;
			}
			mCommentText.append("Temp " + vals[0] + " Humidity " + vals[1]
					+ " " + "(" + vals[2] + ")");
		}
	}
}
