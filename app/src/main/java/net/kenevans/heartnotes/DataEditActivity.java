package net.kenevans.heartnotes;

import java.io.File;
import java.text.ParseException;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
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
public class DataEditActivity extends AppCompatActivity implements IConstants {
    private HeartNotesrDbAdapter mDbAdapter;
    private EditText mCountText;
    private EditText mTotalText;
    private EditText mDateText;
    private EditText mDateModText;
    private EditText mEditedText;
    private EditText mCommentText;
    private Long mRowId;
    private File mDataDir;

    /**
     * Possible values for the edit state.
     */
    private enum State {
        SAVED, DELETED, CANCELLED
    }

    /**
     * The edit state. Since the initial state is cancelled, if the system
     * calls
     * pause, then it will be cancelled and not saved.
     */
    private State state = State.CANCELLED;

    /**
     * Task for getting the temperature from the web
     */
    private GetWeatherTask updateTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);
        // The default state is cancelled and won't be changed until the users
        // selects one of the buttons
        state = State.CANCELLED;

        setContentView(R.layout.data_edit);

        mCountText = findViewById(R.id.count);
        mTotalText = findViewById(R.id.total);
        mDateText = findViewById(R.id.timestamp);
        mDateModText = findViewById(R.id.datemod);
        mEditedText = findViewById(R.id.edited);
        mCommentText = findViewById(R.id.comment);

        mEditedText.setMovementMethod(new ScrollingMovementMethod());

        mRowId = (savedInstanceState == null) ? null
                : (Long) savedInstanceState.getSerializable(COL_ID);
        if (mRowId == null) {
            Bundle extras = getIntent().getExtras();
            mRowId = extras != null ? extras.getLong(COL_ID) : null;
            // -1 indicates a new entry which is implemented as mRowId=null
            if (mRowId != null && mRowId == -1L) {
                mRowId = null;
            }
        }
        if (mDataDir == null) {
            Bundle extras = getIntent().getExtras();
            String dataDirName = extras != null ? extras
                    .getString(PREF_DATA_DIRECTORY) : null;
            if (dataDirName != null) {
                mDataDir = new File(dataDirName);
            }
        }

        mDbAdapter = new HeartNotesrDbAdapter(this, mDataDir);
        mDbAdapter.open();

        // Save
        Button button = findViewById(R.id.save);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Debug
                Log.v(TAG, "Save Button");
                state = State.SAVED;
                setResult(RESULT_OK);
                finish();
            }
        });

        // Cancel
        button = findViewById(R.id.cancel);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Debug
                Log.v(TAG, "Cancel Button");
                String msg = DataEditActivity.this.getString(
                        R.string.note_cancel_prompt);
                new AlertDialog.Builder(DataEditActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.confirm)
                        .setMessage(msg)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        dialog.dismiss();
                                        state = State.CANCELLED;
                                        setResult(RESULT_CANCELED);
                                        finish();
                                    }
                                })
                        .setNegativeButton(R.string.continue_editing_label,
                                null).show();
            }
        });
        populateFields();

        // Delete
        button = findViewById(R.id.delete);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Debug
                Log.v(TAG, "Delete Button");
                String msg = DataEditActivity.this.getString(
                        R.string.note_delete_prompt);
                new AlertDialog.Builder(DataEditActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.confirm)
                        .setMessage(msg)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        dialog.dismiss();
                                        state = State.DELETED;
                                        setResult(RESULT_OK);
                                        finish();
                                    }
                                })
                        .setNegativeButton(R.string.continue_editing_label,
                                null).show();
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
        Log.v(TAG, this.getClass().getSimpleName() + "onPause");
        super.onPause();
        // This gets called on every pause. Since the state is CANCELLED until a
        // button is tapped, it doesn't typically do anything here.
        saveState();
    }

    @Override
    protected void onResume() {
        Log.v(TAG, this.getClass().getSimpleName() + "onResume");
        super.onResume();
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

    /**
     * Starts a GetWeatherTask.
     *
     * @see GetWeatherTask
     */

    private void insertWeather() {
        if (updateTask != null) {
            // Don't do anything if we are updating
            Log.d(TAG, this.getClass().getSimpleName()
                    + ": getStrip: updateTask is not null");
            return;
        }
        updateTask = new GetWeatherTask();
        updateTask.execute();
    }

    /**
     * Updates the database, depending on the state. If the state is
     * CANCELLED, it does nothing. It it is DELETE, the record is deleted.
     * Otherwise it creates or updates the record as appropriate.
     */
    private void saveState() {
        // DEBUG
        Log.v(TAG, this.getClass().getSimpleName() + "saveState called mRowId="
                + mRowId + " state=" + state);
        // Do nothing if cancelled
        if (state == State.CANCELLED) {
            return;
        }
        // Delete if deleted and there is a row ID
        if (state == State.DELETED) {
            if (mRowId != null) {
                mDbAdapter.deleteData(mRowId);
            }
            return;
        }
        // Remaining state is saved, get the entries
        // Don't use entries for edited and dateMod, set them
        String comment;
        long count, total, date, dateMod;
        String string;
        try {
            comment = mCommentText.getText().toString();
            string = mCountText.getText().toString();
            count = Long.parseLong(string);
            string = mTotalText.getText().toString();
            total = Long.parseLong(string);
            string = mDateText.getText().toString();
            Date testDate;
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
            long id = mDbAdapter.createData(date, dateMod, count, total, false,
                    comment);
            if (id > 0) {
                mRowId = id;
            }
        } else {
            // Is edited
            mDbAdapter.updateData(mRowId, date, dateMod, count, total, true,
                    comment);
        }
    }

    /**
     * Initializes the edit fields.
     */
    private void populateFields() {
        if (mRowId != null) {
            Cursor cursor = mDbAdapter.fetchData(mRowId);
            mCountText.setText(cursor.getString(cursor
                    .getColumnIndexOrThrow(COL_COUNT)));
            mTotalText.setText(cursor.getString(cursor
                    .getColumnIndexOrThrow(COL_TOTAL)));
            long time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DATE));
            mDateText.setText(HeartNotesActivity.formatDate(time));
            time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DATEMOD));
            mDateModText.setText(HeartNotesActivity.formatDate(time));
            long val = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EDITED));
            mEditedText.setText(val == 0 ? "false" : "true");
            mCommentText.setText(cursor.getString(cursor
                    .getColumnIndexOrThrow(COL_COMMENT)));
        } else {
            // A new data, set defaults
            mCountText.setText(R.string.default_count);
            mTotalText.setText(R.string.default_total);
            mEditedText.setText(R.string.false_string);
            Date now = new Date();
            mDateText.setText(HeartNotesActivity.formatDate(now.getTime()));
            mDateModText
                    .setText(HeartNotesActivity.formatDate(now.getTime()));
        }
    }

    /**
     * An AsyncTask to get the weather from the web.
     */
    private class GetWeatherTask extends AsyncTask<Void, Void, Boolean> {
        private String[] vals;

        // public GetWeatherTask() {
        // super();
        // }

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
