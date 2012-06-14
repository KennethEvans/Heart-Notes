package net.kenevans.heartmonitor;

import java.text.ParseException;
import java.util.Date;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class DataEditActivity extends Activity implements IConstants {
	private HeartMonitorDbAdapter mDbHelper;
	private EditText mCountText;
	private EditText mTotalText;
	private EditText mDateText;
	private EditText mDateModText;
	private EditText mEditedText;
	private EditText mCommentText;
	private Long mRowId;

	/**
	 * Whether the edit was cancelled or not. Note that if the system calls
	 * pause, then it will not be cancelled and the note will be saved.
	 */
	private enum State {
		SAVED, DELETED, CANCELLED
	};

	private State state = State.CANCELLED;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Debug
		Log.v(TAG, "onCreate called");

		// The default state is cancelled
		state = State.CANCELLED;

		mDbHelper = new HeartMonitorDbAdapter(this);
		mDbHelper.open();

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
		}

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
		saveState();
	}

	@Override
	protected void onResume() {
		// DEBUG
		Log.v(TAG, "onResume called");
		super.onResume();
		populateFields();
	}

	private void saveState() {
		// DEBUG
		Log.v(TAG, "saveState called mRowId=" + mRowId + " state=" + state);
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
			// Don't use edited and dateMod
		} catch (Exception ex) {
			Utils.excMsg(this, "Failed to parse the entered values", ex);
			return;
		}

		if (state == State.CANCELLED) {
			return;
		}
		if (mRowId == null) {
			if (state == State.SAVED) {
				// Set new values
				edited = false;
				dateMod = new Date().getTime();
				long id = mDbHelper.createData(date, dateMod, count, total,
						edited, comment);
				if (id > 0) {
					mRowId = id;
				}
			}
		} else {
			if (state == State.SAVED) {
				// Set new values
				edited = true;
				dateMod = new Date().getTime();
				mDbHelper.updateData(mRowId, date, dateMod, count, total,
						edited, comment);
			} else if (state == State.DELETED) {
				mDbHelper.deleteData(mRowId);
			}
		}
	}

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
}
